# Installing MycoTrack

MycoTrack is distributed outside the Google Play Store: you install a signed **APK**
directly from the [GitHub releases page](https://github.com/elricdog/MycoTrack/releases).
This page explains, step by step, how to do it on any compatible phone or tablet, and how
to keep the app up to date.

- **Requirements:** Android 8.0 (API 26) or newer. **No Google Play Services needed** and
  **no account, no API key, no subscription**.
- **Storage:** the app itself needs about 100 MB; offline map areas are extra (downloaded
  on the device when you want them).
- **Language:** the interface is available in English, Catalan and Spanish.

## 1. Choose the right APK

Every release publishes one APK per processor type, plus a universal one:

| APK | Install this one if… |
| --- | --- |
| `app-universal-release.apk` | **You are not sure** — it works on every compatible device (largest file, ~65 MB) |
| `app-arm64-v8a-release.apk` | Modern phone or tablet (practically every device sold since 2017: Pixel, Samsung Galaxy, Xiaomi, OnePlus, Motorola, Honor…) |
| `app-armeabi-v7a-release.apk` | Older 32-bit ARM devices (entry-level phones from ~2014–2016, some smartwatches-style hardware) |
| `app-x86_64-release.apk` / `app-x86-release.apk` | Emulators (BlueStacks, Android Studio…) and the rare x86 tablet |

Not sure which one you have? Install the **universal** APK and you are done. If you like
precision, open a terminal with ADB and run `adb shell getprop ro.product.cpu.abi`, or
install any app that shows hardware information (for instance DevCheck).

All APKs contain exactly the same application; only the bundled native libraries differ.

## 2. Method A — download and install on the phone (no computer needed)

1. On the phone, open the [releases page](https://github.com/elricdog/MycoTrack/releases)
   and download the APK of your choice (latest release at the top of the page).
2. Open the downloaded file from the notification or from your *Files* app.
3. The first time, Android warns that this app comes from an *unknown source*:
   tap **Settings → Allow from this source** (the wording changes with each brand):
   - **Samsung:** Allow only in this account / *Aquesta fonts coneguda*
   - **Xiaomi / Redmi / POCO:** enable *Install unknown apps* for the browser and accept the security scan screen
   - **Huawei / Honor:** *Settings → Security → More settings → Install unknown apps*
   - **Other brands:** the system normally shows a single *Allow* switch for the browser or file manager
4. Confirm the install and open the app.
5. Grant the permissions the app asks for when it starts (see section 4).

> Minimal Android builds sometimes refuse to open APKs downloaded by certain apps. If
> nothing happens when you tap the file, first open the *Files* app, find it
> (usually `Download/`) and tap it there.

## 3. Method B — install with a computer and ADB

Install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)
on the computer, enable **Settings → Developer options → USB debugging** on the phone
(tap *Build number* seven times to reveal Developer options) and connect by USB:

```bash
adb devices                    # the phone must appear as 'device'
adb install app-universal-release.apk      # or the APK of your device's ABI
```

Useful variants:

```bash
adb install -r app-universal-release.apk   # update without losing data
adb install -r -d app-….apk                # allow version downgrade (testing)
adb shell getprop ro.product.cpu.abi       # which ABI the device uses
adb uninstall com.mikotrack                # remove (see the backup note first)
```

## 4. First start: permissions

The app explains every permission on screen before asking the system for it:

| Permission | Needed for |
| --- | --- |
| Location (*while using the app*) | Position on the map and route recording |
| Location *all the time* | The only optional one: keeps recording and sounding the alarms with the screen off or the app closed. Answer *Not now* if you only want to record with the screen on |
| Notifications | The ongoing recording notification, the quick 🍄 logging and the safety alerts |
| Camera | The geotagged photographs taken inside the app |

Nothing leaves the device: all trips, photographs and settings are stored locally. The app
works without an account and without Play Services; internet is only used for map tiles,
place search and elevation data.

## 5. Updating to a new version

### In-app auto-update (recommended)

From v1.1 on, MycoTrack updates itself: once a day when the app opens — and whenever you
tap **Settings → Check for updates** — it asks GitHub for a new release. If there is one,
a dialog shows the new version and its notes, and with your confirmation it downloads the
APK that suits your device and opens the Android installer. Nothing is ever installed
without your explicit confirmation, and your trips, photos and settings are kept.

The first time you update in-app, Android asks to allow MycoTrack to install apps from
unknown sources (*Settings → Install unknown apps*): enable it and tap *Install* again.
You can turn the automatic checks off in **Settings → Updates**.

### Manual update

Download the new APK and install it over the old one: trips, photographs and settings are
kept. No need to uninstall first — and if you do, export or back up your data beforehand
(*Settings* and the export tools). All released APKs are signed with the same key; an APK
signed with a different key will refuse to install over the existing app.

You can watch new releases automatically: on GitHub, *Watch → Custom → Releases* on the
[repository page](https://github.com/elricdog/MycoTrack).

## 6. Uninstalling

*Settings → Apps → MycoTrack → Uninstall* removes the app and its local data. If you want
to keep your trips and photographs, use the export tools first (GPX / GeoJSON / KML / CSV /
ZIP and PNG reports) or share the gallery to `Pictures/MikoTrack`.

## Troubleshooting

| Problem | Solution |
| --- | --- |
| *App not installed* or *parse error* | The download is incomplete, or the APK is for another ABI — download it again or use the universal one |
| The install is blocked during download (Chrome, Xiaomi…) | Tap the download in the notifications panel and choose the option to install anyway, or open the file from the Files app |
| The map shows no tiles | Check the internet connection; download offline areas over Wi-Fi from *Settings → Offline maps* |
| Recording stops with the screen off | Grant *all the time* location and disable battery optimisation for the app (*Settings → Battery → MycoTrack → Unrestricted*) |
| The photo camera stays black | Grant the Camera permission in the system settings |
| You installed by mistake with another signature (e.g. a debug APK) | Uninstall the app first (export your data before) and install the release APK |
