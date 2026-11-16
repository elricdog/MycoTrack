"""Finalises an MBTiles pack that ships inside the APK.

The pack builder leaves the database in WAL journal mode, which produces a pair of
``-wal``/``-shm`` sidecar files. Those sidecars must not be shipped: the APK only
contains the ``.mbtiles`` file, so the database has to be self contained.

This script checkpoints the write-ahead log, switches the journal back to the rollback
mode, records the tile size of the pack in the metadata table and verifies the result.
"""

import argparse
import os
import sqlite3
import sys


def finalize(path: str, tile_size: int) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        connection.execute("PRAGMA journal_mode=DELETE")
        connection.execute("PRAGMA synchronous=FULL")
        connection.execute("VACUUM")

        connection.execute(
            "INSERT INTO metadata (name, value) VALUES ('tilesize', ?) "
            "ON CONFLICT DO NOTHING",
            (str(tile_size),),
        )
        connection.execute(
            "INSERT INTO metadata (name, value) VALUES ('format', 'jpg') "
            "ON CONFLICT DO NOTHING",
        )
        connection.execute("PRAGMA user_version = 1")
        connection.commit()

        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        journal = connection.execute("PRAGMA journal_mode").fetchone()[0]
        tiles = connection.execute("SELECT COUNT(*) FROM tiles").fetchone()[0]
        zooms = [
            row[0]
            for row in connection.execute(
                "SELECT DISTINCT zoom_level FROM tiles ORDER BY zoom_level"
            )
        ]
        metadata = dict(connection.execute("SELECT name, value FROM metadata"))
    finally:
        connection.close()

    for suffix in ("-wal", "-shm"):
        sidecar = path + suffix
        if os.path.exists(sidecar):
            os.remove(sidecar)

    print(f"{path}")
    print(f"  integrity : {integrity}")
    print(f"  journal   : {journal}")
    print(f"  tiles     : {tiles}")
    print(f"  zooms     : {zooms}")
    print(f"  format    : {metadata.get('format')}")
    print(f"  tilesize  : {metadata.get('tilesize')}")
    print(f"  bounds    : {metadata.get('bounds')}")
    print(f"  size      : {os.path.getsize(path)} bytes")

    if integrity != "ok":
        sys.exit(f"integrity check failed for {path}")
    if journal.lower() != "delete":
        sys.exit(f"journal mode is still {journal}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", help="path to the .mbtiles file")
    parser.add_argument("--tile-size", type=int, required=True)
    arguments = parser.parse_args()
    finalize(arguments.pack, arguments.tile_size)


if __name__ == "__main__":
    main()
