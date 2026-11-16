"""Builds the offline map pack that is bundled inside the MycoTrack APK.

The tiles come from the public WMTS services of the Institut Cartografic i
Geologic de Catalunya (ICGC). Every tile is stored in a standard MBTiles file
(SQLite, TMS rows) so that the application can serve it with its own loopback
tile server.

The script is resumable: tiles that are already present in the target file are
not downloaded again, so an interrupted run can simply be started again.

Example:
    python tools/build_offline_pack.py --layer ORTO --min-zoom 10 --max-zoom 13 \
        --out app/offline-assets/offline/catalonia_ortho.mbtiles
"""

from __future__ import annotations

import argparse
import io
import math
import os
import sqlite3
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from typing import Iterable

UA = "MycoTrack/1.0 (offline map pack builder; contact: local build)"

# The public tile endpoints of the ICGC. The "MON3857NW" matrix set uses a
# 2^z grid, so the tile (z, x, y) of both matrix sets covers the same
# geographical footprint.
SERVEI = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts"

# The bounding box of Catalonia, including a small margin.
CATALONYA = (40.52, 42.86, 0.15, 3.33)

# The real pixel size of the tiles served by every layer.
LAYER_INFO = {
    "ORTO": {
        "service": "orto",
        "pixels": 256,
        "format": "jpg",
        "attribution": "Institut Cartografic i Geologic de Catalunya",
    },
    "TOPO": {
        "service": "topografic",
        "pixels": 512,
        "format": "jpg",
        "attribution": "Institut Cartografic i Geologic de Catalunya",
    },
}

_print_lock = threading.Lock()


def log(message: str) -> None:
    with _print_lock:
        print(message, flush=True)


def lon_to_x(lon: float, zoom: int) -> int:
    return int((lon + 180.0) / 360.0 * (1 << zoom))


def lat_to_y(lat: float, zoom: int) -> int:
    rad = math.radians(lat)
    value = (1.0 - math.asinh(math.tan(rad)) / math.pi) / 2.0
    return int(value * (1 << zoom))


def level_tiles(bbox: tuple[float, float, float, float], zoom: int) -> list[tuple[int, int]]:
    south, north, west, east = bbox
    x0, x1 = lon_to_x(west, zoom), lon_to_x(east, zoom)
    y0, y1 = lat_to_y(north, zoom), lat_to_y(south, zoom)
    return [(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)]


def fetch(url: str, attempts: int = 4) -> bytes | None:
    """Downloads a tile, retrying a few times on a transient network error."""
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(request, timeout=45) as response:
                if response.status == 200:
                    return response.read()
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
        except Exception:  # noqa: BLE001 - the network is allowed to fail
            pass
        time.sleep(0.4 * (attempt + 1))
    return None


def encode_jpeg(raw: bytes, quality: int) -> bytes:
    """Re-encodes a PNG tile as JPEG, which is much smaller for this kind of map."""
    try:
        from PIL import Image
    except ImportError as error:
        raise RuntimeError("Pillow is required to re-encode the tiles") from error
    try:
        image = Image.open(io.BytesIO(raw))
        if image.mode in ("RGBA", "LA", "P"):
            image = image.convert("RGBA")
            flat = Image.new("RGB", image.size, (255, 255, 255))
            flat.paste(image, mask=image.split()[3])
            image = flat
        else:
            image = image.convert("RGB")
        buffer = io.BytesIO()
        image.save(buffer, "JPEG", quality=quality, optimize=True, progressive=False)
        return buffer.getvalue()
    except Exception as error:  # noqa: BLE001
        raise RuntimeError(f"Could not re-encode the tile: {error}") from error


def open_database(path: str) -> sqlite3.Connection:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    connection = sqlite3.connect(path)
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("PRAGMA synchronous=NORMAL")
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS tiles (
            zoom_level INTEGER NOT NULL,
            tile_column INTEGER NOT NULL,
            tile_row INTEGER NOT NULL,
            tile_data BLOB NOT NULL,
            PRIMARY KEY (zoom_level, tile_column, tile_row)
        )
        """
    )
    connection.execute("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT)")
    connection.execute(
        "CREATE INDEX IF NOT EXISTS tiles_index ON tiles (zoom_level, tile_column, tile_row)"
    )
    # The application opens the file through a SQLiteOpenHelper with version 1;
    # the user_version has to match so that the file is not recreated.
    connection.execute("PRAGMA user_version = 1")
    connection.commit()
    return connection


def existing_tiles(connection: sqlite3.Connection, zoom: int) -> set[tuple[int, int]]:
    rows = connection.execute(
        "SELECT tile_column, tile_row FROM tiles WHERE zoom_level = ?", (zoom,)
    ).fetchall()
    return {(row[0], row[1]) for row in rows}


def write_metadata(
    connection: sqlite3.Connection,
    name: str,
    bbox: tuple[float, float, float, float],
    min_zoom: int,
    max_zoom: int,
    fmt: str,
    attribution: str,
) -> None:
    south, north, west, east = bbox
    values = {
        "name": name,
        "type": "baselayer",
        "version": "1.3",
        "description": "Offline map area bundled with MycoTrack",
        "format": fmt,
        "bounds": f"{west},{south},{east},{north}",
        "minzoom": str(min_zoom),
        "maxzoom": str(max_zoom),
        "attribution": attribution,
    }
    connection.executemany(
        "INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)",
        list(values.items()),
    )
    connection.commit()


def build(args: argparse.Namespace) -> int:
    info = LAYER_INFO[args.layer]
    service = info["service"]
    bbox = CATALONYA
    out_path = os.path.abspath(args.out)

    connection = open_database(out_path)
    template = f"{SERVEI}/{service}/MON3857NW/{{z}}/{{x}}/{{y}}.png"

    grand_total = 0
    grand_bytes = 0
    grand_failed = 0

    for zoom in range(args.min_zoom, args.max_zoom + 1):
        wanted = level_tiles(bbox, zoom)
        # The file stores TMS rows, so the already downloaded tiles have to be
        # looked up with the same convention.
        have = existing_tiles(connection, zoom)
        pending = [
            (x, y) for (x, y) in wanted if (x, (1 << zoom) - 1 - y) not in have
        ]

        level_bytes = 0
        level_failed = 0
        level_total = len(pending)
        started = time.time()

        def work(job: tuple[int, int]) -> tuple[int, int, bytes | None]:
            x, y = job
            raw = fetch(template.format(z=zoom, x=x, y=y))
            if raw is None:
                return x, y, None
            if args.quality and args.quality > 0:
                raw = encode_jpeg(raw, args.quality)
            return x, y, raw

        if pending:
            with ThreadPoolExecutor(max_workers=args.workers) as pool:
                batch: list[tuple[int, int, int, bytes]] = []
                done = 0
                for (x, y, data) in pool.map(work, pending):
                    done += 1
                    if data is None:
                        level_failed += 1
                    else:
                        level_bytes += len(data)
                        batch.append((zoom, x, (1 << zoom) - 1 - y, data))
                        if len(batch) >= 400:
                            connection.executemany(
                                "INSERT OR REPLACE INTO tiles "
                                "(zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)",
                                batch,
                            )
                            connection.commit()
                            batch.clear()
                    if done % 500 == 0:
                        rate = done / max(time.time() - started, 0.001)
                        log(
                            f"  z{zoom}: {done}/{level_total} "
                            f"({level_bytes / 1048576:.1f} MB, {rate:.0f} tiles/s)"
                        )
                if batch:
                    connection.executemany(
                        "INSERT OR REPLACE INTO tiles "
                        "(zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)",
                        batch,
                    )
                    connection.commit()

        grand_total += len(wanted)
        grand_bytes += level_bytes
        grand_failed += level_failed
        log(
            f"z{zoom}: {len(wanted)} tiles in the level, {len(pending)} downloaded, "
            f"{level_failed} missing, {level_bytes / 1048576:.1f} MB"
        )

    write_metadata(
        connection,
        args.name,
        bbox,
        args.min_zoom,
        args.max_zoom,
        "jpg" if args.quality and args.quality > 0 else info["format"],
        info["attribution"],
    )
    connection.execute("VACUUM")
    connection.commit()
    connection.close()

    size = os.path.getsize(out_path)
    log(
        f"Done: {grand_total} tiles, {grand_failed} missing, "
        f"{grand_bytes / 1048576:.1f} MB of tiles, {size / 1048576:.1f} MB on disk -> {out_path}"
    )
    return 0


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--layer", choices=sorted(LAYER_INFO), required=True)
    parser.add_argument("--min-zoom", type=int, default=10)
    parser.add_argument("--max-zoom", type=int, default=13)
    parser.add_argument("--out", required=True)
    parser.add_argument("--name", default=None)
    parser.add_argument(
        "--quality",
        type=int,
        default=0,
        help="Re-encode the tiles as JPEG with this quality (0 keeps the original bytes).",
    )
    parser.add_argument("--workers", type=int, default=12)
    args = parser.parse_args(list(argv) if argv is not None else None)
    if args.name is None:
        args.name = f"Catalonia {args.layer}"
    return build(args)


if __name__ == "__main__":
    sys.exit(main())
