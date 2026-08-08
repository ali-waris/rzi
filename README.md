# RZI

An offline-first personal reference manager for quotes. Save quotes from books with page numbers and tags, then browse them in an infinite scroll reel or search your library.

## Features

- **Quote reel** — Swipe through your saved references in an infinite, vertical deck. Reel order can be linear or shuffled, and filtered by book or tags.
- **Library** — Browse every saved reference with full-text search and tag-based filtering.
- **Quote management** — Add, edit, and delete references with quote text, book name, page number, and tags. Book and tag suggestions are offered as you type.
- **Admin PIN** — Editing features are protected by a PIN gate. Set a PIN on first launch, and change it any time from the library.
- **Offline-first** — All data is stored locally on device (Room). No account or network required.
- **Export / Import** — Export your library as a SQLite file to back it up or move it to another device, and import it back at any time.

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Room (local database + FTS search)
- Hilt (dependency injection)
- Paging 3
- DataStore (preferences)
- kotlinx.serialization
- Navigation Compose / Navigation 3

| Property | Value |
|----------|-------|
| Min SDK | 26 |
| Target SDK | 36 |
| JDK | 17 |


## Release Process

Releases are created **only when a git tag is pushed**.

### Versioning

- `versionCode` lives in `app/build.gradle.kts` and is bumped **manually** for each release.
- Tag format: `v<versionCode>.<dd>.<mm>.<yyyy>`

For example, to release `versionCode = 2` on 08 August 2026:

```bash
git tag v2.08.08.2026
git push origin v2.08.08.2026
```

### What the workflow does

1. Triggers on a pushed tag matching `v<versionCode>.<dd>.<mm>.<yyyy>`.
2. Decodes the signing keystore from GitHub secrets.
3. Builds a **signed release APK** with Gradle.
4. Creates a GitHub Release for the tag with auto-generated release notes.
5. Uploads the APK as `rzi-v<tag>.apk`.

### Required secrets

| Secret | Description |
|--------|-----------|
| `KEYSTORE_BASE64` | Base64-encoded signing keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |


### Local signed builds

Release signing reads credentials from environment variables:

```bash
export KEYSTORE_PASSWORD=your-store-password
export KEY_ALIAS=rzi-key
export KEY_PASSWORD=your-key-password
./gradlew assembleRelease
```

## License

See [LICENSE](LICENSE).
