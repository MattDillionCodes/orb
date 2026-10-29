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

## Layout

- `app/src/main/java/com/orbdash/` — app loading (`AppRepository`), app sorting (`Category`), sounds (`Sfx`)
- `app/src/main/java/com/orbdash/ui/` — orb, backdrop, menus, widgets, icons, intro
- `app/src/main/res/drawable-nodpi/wp_*.jpg` — wallpapers (Settings → Wallpaper)
- `app/src/main/res/font/` — Saira Semi Condensed (SIL Open Font License)
