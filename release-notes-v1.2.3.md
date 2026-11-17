# MycoTrack v1.2.3

## Habitat layer: no more empty cells
Some habitat cells used to stay blank and only appeared after zooming. Failed tiles are now retried instead of being remembered as permanently empty, and concurrent map downloads are shared, so the layer fills in reliably.

## Parking waypoints get a car icon
- The parking marker now shows a vehicle glyph inside the badge.
- The waypoint panel has a **Go there** button that opens the destination in Google Maps.
- In **Settings** you can choose the icon (car, motorcycle, bicycle, campervan, garage) and the badge colour.

## Splash screen
The app now opens with a loading screen showing the mascot, the developer credit (*by Elric of Anaconda*) and the current version.

## Report a problem
A new **Report a problem** button in Settings opens a ready-to-fill GitHub issue with the app version and your device already written.

---
Install the APK that matches your device (see [INSTALL.md](./INSTALL.md)):
- `app-arm64-v8a-release.apk` — most modern phones (2018+)
- `app-armeabi-v7a-release.apk` — older 32-bit phones
- `app-universal-release.apk` — works on everything (largest file)
