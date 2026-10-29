# OrbDash

A Google TV / Android TV home screen inspired by the original 2001 green-orb console dashboard:
a breathing gel orb, jelly menu pods on glowing tethers, a hex-tunnel backdrop, and synthesized UI sounds.

## Build and run

```bash
./scripts/run-emulator.sh
```

This builds the app and runs it on its own dedicated Android TV emulator, `OrbDash_TV`.
The script creates the emulator the first time and boots it on a free port. It then installs OrbDash and makes it that emulator's Home screen.
It never touches other emulators or a real TV.
Arrow keys and Enter on your keyboard act as the remote's D-pad and OK; Esc is Back.

## Music from other apps

The Music screen can follow Spotify, YouTube Music and other music apps: it shows what's playing, the remote controls it, and the light show reacts to the TV's sound.
This needs OrbDash's "notification access" switched on. The emulator script does that for you.
On a TV whose Settings hide that switch, turn it on from a computer:

```bash
adb shell cmd notification allow_listener com.orbdash/com.orbdash.music.MediaListener
```

The emulator can't run Spotify or YouTube, so `testmusic/` is a tiny stand-in ("Test Music") that plays a beat and announces songs the same way. It's for the emulator only:

```bash
./gradlew :testmusic:assembleDebug && adb -s emulator-5584 install -r testmusic/build.nosync/outputs/apk/debug/testmusic-debug.apk
```

Some TVs don't let apps hear other apps' sound. There, the light show pulses to a steady beat instead of the music.

## Layout

- `app/src/main/java/com/orbdash/` — app loading (`AppRepository`), app sorting (`Category`), sounds (`Sfx`)
- `app/src/main/java/com/orbdash/ui/` — orb, backdrop, menus, widgets, icons, intro
- `app/src/main/res/drawable-nodpi/wp_*.jpg` — wallpapers (Settings → Wallpaper)
- `app/src/main/res/font/` — Saira Semi Condensed (SIL Open Font License)
