# OrbDash

A Google TV / Android TV home screen inspired by the original 2001 green-orb console dashboard:
a breathing gel orb, jelly menu pods on glowing tethers, a hex-tunnel backdrop, and synthesized UI sounds.

## Build and run

```bash
./gradlew :app:installDebug
```

Then launch **OrbDash** from the TV's app list. It also registers as a Home app, so the TV can offer it as the launcher.

## Layout

- `app/src/main/java/com/orbdash/` — app loading (`AppRepository`), app sorting (`Category`), sounds (`Sfx`)
- `app/src/main/java/com/orbdash/ui/` — orb, backdrop, menus, widgets, icons, intro
- `app/src/main/res/drawable-nodpi/wp_*.jpg` — wallpapers (Settings → Wallpaper)
- `app/src/main/res/font/` — Saira Semi Condensed (SIL Open Font License)
