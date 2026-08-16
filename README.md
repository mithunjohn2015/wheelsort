# WheelSort

A Tinder-style photo sorting app for Android, built with Kotlin + Jetpack Compose.

## The core interaction

- Photos are shown one at a time, full-screen, like a wheel/carousel.
- **Swipe up / down** — turn the wheel, browse to the next/previous photo (no action taken).
- **Swipe left** on the centered photo — send it to **Trash**.
- **Swipe right** on the centered photo — **Keep** it and move to the next one.
- A single custom gesture detector (`WheelGesture.kt`) locks onto whichever axis you actually drag in, so vertical browsing and horizontal decisions never fight each other.

## How deletion actually works

WheelSort never touches files directly. Instead it uses the real Android scoped-storage APIs:

- `MediaStore.createTrashRequest()` — moves a photo to the system Trash (this is what "Delete" does). This is recoverable, exactly like the description of a Trash folder.
- Trashed photos are hidden from the main library automatically (queried with `MATCH_EXCLUDE`) and shown in the in-app **Trash** screen (queried with `MATCH_ONLY`).
- From Trash you can **Restore** (`createTrashRequest(trash = false)`) or **Delete forever** (`createDeleteRequest()`).
- All of these show the standard one-time Android system confirmation dialog the first time, as required by scoped storage — this is expected OS behavior, not a bug.

This requires **Android 11 (API 30) or newer**, since that's when these MediaStore APIs were introduced. `minSdk` is set to 30 in `app/build.gradle.kts`.

## Features included

- Tinder-style swipeable card with animated offset/rotation and KEEP/DELETE badges that fade in as you drag
- Wheel-style vertical browsing with dimmed peeks of the previous/next photo
- Trash screen with grid view, multi-select, restore, and permanent delete
- Undo button (top bar) and an undo action on the delete snackbar
- Stats screen (photos reviewed, trashed count, space recoverable)
- Album/bucket filter on the home screen (sort just one album, or everything)
- Runtime permission screen that adapts to Android 13+ (`READ_MEDIA_IMAGES`) vs older versions, with a "permanently denied → open settings" fallback
- Haptic feedback on swipe decisions
- Material 3 theming with automatic light/dark mode
- Custom adaptive app icon (fanned photo-card / wheel motif)

### Easy extension points already wired up in `PhotoRepository`
- `createFavoriteRequest()` — mark/unmark a photo as a system favorite (e.g. bind to a double-tap or a "super keep" swipe).
- `distinctAlbums()` — already powers the album filter; could become a full albums grid.

## Project structure

```
app/src/main/java/com/wheelsort/app/
  data/PhotoRepository.kt      MediaStore queries + trash/restore/delete PendingIntents
  data/Photo.kt                 photo model
  ui/sort/                      the wheel + swipe screen (SortScreen, SwipeableCard, WheelGesture, SortViewModel)
  ui/trash/                     trash grid screen + view model
  ui/stats/                     stats screen
  ui/home/                      album picker / entry screen
  ui/permission/                permission gate screen
  ui/navigation/                Navigation-Compose graph
  ui/theme/                     Material 3 theme, colors, type
  MainActivity.kt                permission check → hosts the nav graph
```

## Building it

1. Open the `wheelsort/` folder in Android Studio (Koala or newer recommended).
2. Let Gradle sync — it will pull Compose BOM 2024.06.00, Navigation-Compose, and Coil automatically.
3. Run on a device or emulator running **Android 11+**.
4. Grant photo access when prompted.

## Notes / things worth testing on a real device

- The axis-lock gesture threshold (18px) and swipe-commit threshold (28% of card width) in `SortScreen.kt` / `WheelGesture.kt` are reasonable starting points — tune them to taste once you're swiping on your own phone.
- On first delete/restore/permanent-delete, Android shows a system confirmation sheet — this is required by the OS and can't be skipped (it can be bypassed only for media the app itself created).
- No cloud backend — everything reads/writes to the phone's own `MediaStore`. There's no separate "trash folder on disk" because Android 11+ handles that natively; this is the modern, scoped-storage-correct equivalent of what you described.
