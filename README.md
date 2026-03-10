# GallaryApp – Android Photo Gallery

A simple Android photo gallery application built with Kotlin and Jetpack Compose for **159.336 Assignment 2**.

---

## Overview

GallaryApp displays all photos stored on the device in a scrollable grid. Tapping a photo opens it in a full-screen viewer with pinch-to-zoom support. The app is built without any third-party image libraries, using only the Android SDK.

---

## Features

- **Scrollable photo grid** – `LazyVerticalGrid` displays all device photos ordered by most recently added.
- **Thumbnail loading** – Thumbnails are loaded asynchronously on the IO dispatcher using `LaunchedEffect` and Kotlin coroutines.
- **Efficient decoding** – `BitmapFactory.decodeStream` with `inSampleSize` is used to load low-resolution thumbnails without excessive memory usage.
- **Thumbnail cache** – An `LruCache`-backed `ThumbnailCache` prevents redundant disk reads and speeds up scrolling.
- **Orientation correction** – Thumbnails and full images are rotated based on the EXIF orientation metadata from MediaStore.
- **Full-screen photo viewer** – Opens in a separate `PhotoViewActivity` with a higher-resolution image than the thumbnail.
- **Pinch to zoom** – The full-screen viewer supports pinch-to-zoom (1×–5×) and panning when zoomed in.
- **Permission handling** – Requests `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (API < 33) at runtime.
- **Rotation & lifecycle aware** – The photo list refreshes on resume so additions and deletions are reflected correctly. State is preserved across device rotation.
- **Large image support** – Handles images up to 24MP via `inSampleSize` downsampling.

---

## Project Structure

```
GallaryApp/
├── app/
│   ├── manifests/
│   │   └── AndroidManifest.xml          ← Permissions & activity declarations
│   ├── kotlin+java/
│   │   └── com.example.gallaryapp/
│   │       ├── ui.theme/
│   │       │   ├── Color.kt             # App colour palette
│   │       │   ├── Theme.kt             # Material3 theme (light/dark/dynamic)
│   │       │   └── Type.kt              # Typography definitions
│   │       ├── MainActivity.kt          # Grid view, permission handling, lifecycle
│   │       ├── Photo.kt                 # Data class for photo metadata
│   │       ├── PhotoLoader.kt           # MediaStore queries & bitmap decoding
│   │       ├── PhotoViewActivity.kt     # Full-screen viewer with pinch-to-zoom
│   │       └── ThumbnailCache.kt        # LruCache wrapper for in-memory bitmaps
│   │   └── com.example.gallaryapp (androidTest)/
│   │       └── ExampleInstrumentedTest.kt
│   │   └── com.example.gallaryapp (test)/
│   │       └── ExampleUnitTest.kt
│   └── res/
│       ├── drawable/
│       │   ├── ic_launcher_background.xml
│       │   └── ic_launcher_foreground.xml
│       ├── mipmap/
│       │   ├── ic_launcher/             # App icon (hdpi → xxxhdpi + anydpi)
│       │   └── ic_launcher_round/       # Round app icon (hdpi → xxxhdpi + anydpi)
│       ├── values/
│       │   ├── colors.xml
│       │   ├── strings.xml
│       │   └── themes.xml
│       └── xml/
│           ├── backup_rules.xml
│           └── data_extraction_rules.xml
└── Gradle Scripts/
    ├── build.gradle.kts (Project: GallaryApp)   ← Do not modify
    ├── build.gradle.kts (Module :app)
    ├── proguard-rules.pro
    ├── gradle.properties
    ├── gradle-wrapper.properties
    ├── libs.versions.toml                       ← Version catalog
    ├── local.properties                         ← SDK location (not committed)
    └── settings.gradle.kts
```

---

## Requirements

| Property | Value |
|----------|-------|
| Language | Kotlin |
| UI toolkit | Jetpack Compose (Material3) |
| `targetSdk` | 36 |
| `minSdk` | 26 |
| Image libraries | **None** – Coil, Glide, Picasso, Fresco, and Photo Picker are prohibited |

---

## Key Implementation Details

### MediaStore Query (`PhotoLoader.loadPhotos`)
Photos are queried from `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, selecting `_ID`, `ORIENTATION`, `WIDTH`, `HEIGHT`, and `DATE_ADDED`, sorted by date descending.

### Thumbnail Loading (`PhotoLoader.loadThumbnail`)
1. Check `ThumbnailCache` first; return cached bitmap if present.
2. Open an `InputStream` via `contentResolver.openInputStream(uri)`.
3. Decode bounds only (`inJustDecodeBounds = true`) to determine image dimensions.
4. Calculate `inSampleSize` to fit within the target size (default 300 px).
5. Decode the full bitmap at the reduced sample size.
6. Rotate the bitmap if `photo.orientation != 0`.
7. Store the result in `ThumbnailCache`.

### Full Image Loading (`PhotoLoader.loadFullImage`)
Same pipeline as thumbnails but with a larger `maxSize` (default 2048 px) and no caching, since only one full image is shown at a time.

### Thumbnail Cache (`ThumbnailCache`)
Uses Android's `LruCache` sized at 1/8 of the available heap. Bitmap size is measured in KB via `bitmap.byteCount / 1024`.

### Pinch to Zoom (`PhotoViewActivity`)
`detectTransformGestures` tracks scale (clamped to 1×–5×) and X/Y offsets, applied via `graphicsLayer`. Panning is disabled when the image is at 1× scale.

---

## Permissions

Declared in `AndroidManifest.xml` and requested at runtime:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

---

## Building

Open the project in Android Studio and build normally, or from the command line:

```bash
./gradlew assembleDebug
```

> **Note:** Do not modify `build.gradle.kts (Project: GallaryApp)`, the `gradle/` folder, or the `gradlew` scripts — the assignment is graded via the Gradle command line.

---

## Testing with Large Images

1. Transfer test photos to `/sdcard/Pictures` on the emulator using the Android Studio Device Explorer.
2. Perform a **cold boot** of the emulator (Device Manager → ⋮ → Cold Boot) so the images are indexed by MediaStore.
3. Launch the app and verify the grid loads and large images (up to 24MP) display correctly.

---

## Assignment Submission

Export a clean source tree via **File → Export to Zip File…** in Android Studio and submit the resulting `.zip` on Stream.
