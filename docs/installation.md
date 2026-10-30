# Installation

For developers who build the app. To just install it, download the APK from [Releases](https://github.com/wimvdon/watchlist/releases).

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17 or newer | CI uses Temurin 21. Android Studio's bundled JBR works. |
| Android SDK | Platform 37 | `compileSdk` and `targetSdk` are 37. |
| Device or emulator | Android 8.0+ (API 26) | Per-app language setting needs Android 13+. |
| SIMKL client ID | AUTH V2 app | From [simkl.com/settings/developer](https://simkl.com/settings/developer), redirect URI `watchlist://oauth`. Without it the app starts but cannot sign in. |
| Git | any | Debug builds are versioned with `git describe`. |

Gradle is downloaded by the wrapper. There is no database or server to set up.

## 1. Clone the repo

```bash
git clone https://github.com/wimvdon/watchlist.git
cd watchlist
```

## 2. Point Gradle at the SDK

Android Studio creates `local.properties` when it opens the project. From the command line:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties        # macOS: $HOME/Library/Android/sdk
```

## 3. Provide the SIMKL client ID

It is a Gradle property, not an environment variable or file in the repo. Either pass it on every command, or keep it in your user-level Gradle properties so it never lands in git:

```bash
echo "simklClientId=<your client ID>" >> ~/.gradle/gradle.properties
```

## 4. Build and run

```bash
./gradlew assembleDebug -PsimklClientId=<id>   # APK: app/build/outputs/apk/debug/app-debug.apk (".debug" ID)
./gradlew installDebug -PsimklClientId=<id>    # installs on a connected device (USB debugging on)
```

Or open the project in Android Studio and press **Run** (with the client ID in `~/.gradle/gradle.properties`).

## Verify it works

1. Open the app: the sign-in screen appears.
2. Tap **Sign in with SIMKL**, approve in the browser: the app returns to the **TV Shows** tab and fills the shelves.
3. Pull down to refresh: the progress indicator shows and disappears without an error.

The checks CI runs:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 -m unittest discover -s scripts/tests
```

## Testing a pull request

Open the pull request › **Checks** › **CI** › **Artifacts** and install `watchlist-<version>-debug.apk`. Debug builds have the application ID `to.wim.watchlist.debug` and the name **Watchlist Dev**, so they install next to the released app with their own sign-in and data. When signing in, Android may ask which app should open the SIMKL redirect: pick **Watchlist Dev**.

## Release builds

Signed APKs and app bundles (AAB, for Google Play) are built only by `.github/workflows/release.yml`. It needs, under **Settings › Secrets and variables › Actions**:

| Name | Kind | Value |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | secret | The keystore file as base64 |
| `ANDROID_KEYSTORE_PASSWORD` | secret | Keystore password |
| `ANDROID_KEY_ALIAS` | secret | Key alias |
| `ANDROID_KEY_PASSWORD` | secret | Key password |
| `SIMKL_CLIENT_ID` | variable | The app's SIMKL client ID (not a secret, per SIMKL's API rules) |

The secret names without `ANDROID_` also work, as earlier releases used them. The workflow decodes the keystore with `scripts/decode_keystore.py`, checks the password, alias and key password before building, runs the tests and lint, and builds with `-PappVersionName`, `-PappVersionCode` and `-PsimklClientId`.

## Making a release

**Actions › Release › Run workflow** on `main`, choose `patch`, `minor` or `major` (or `none` to only build an APK artifact). Pushing a tag such as `v1.2.3` also releases. The versionCode is `major × 10000 + minor × 100 + patch`.

It refuses to publish when not on `main`, when the tag or release exists, when the version is not higher than the last release, or when nothing changed. Each release has `watchlist-<version>.apk` (to install directly), `watchlist-<version>.aab` (to upload to Google Play; signed with the same key) and `watchlist-<version>-mapping.txt`.
