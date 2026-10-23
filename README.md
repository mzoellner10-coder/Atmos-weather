# Atmos Weather 2.0

Native Kotlin + Jetpack Compose weather app for Android 10+ (`com.atmos.weather`). Hybrid is the default; weather gradients and true black modes are available. No account or API key is required.

## Build and install

Use Android Studio with JDK 17 or 21 and Android SDK 35, or:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In this managed cloud machine, `python3 tools/setup_cloud.py` installs/checks the pinned, checksum-verified Temurin JDK and Android SDK. Then run `tools/cloud-build.sh` to use the prepared SDK, writable caches and inherited HTTP proxy. TLS and Gradle distribution checksum verification stay enabled. The Gradle wrapper pins Gradle 8.9; Android Gradle Plugin 8.7.3 and Kotlin 2.0.21 are pinned in the root build file. CI builds and uploads the debug APK and check reports on pushes, pull requests, or manual dispatch.

The debug APK is for personal testing and uses a debug certificate. A production release needs your own signing configuration. Never commit signing keys or passwords.

## Use

1. Search for a city, or grant approximate/precise foreground location for a single location fix.
2. Tap forecast cards for hourly graphs, the 16-day forecast, precipitation, wind direction, air quality, solar times and metrics. Tap graphs or use their hour controls.
3. Pull down or tap Refresh for a new forecast. Saved cities can be opened, removed, or made default in Places.
4. Settings stores temperature, wind and precipitation units, theme, animation intensity and refresh preferences on device.
5. Add any of the seven Atmos designs through Android's widget picker. Select a saved city, Fahrenheit/Celsius, location visibility, style and opacity in the widget configuration screen. Resize through the launcher. A widget opens its city's corresponding detail screen.

Widgets use the same Room cache as the app. No widget runs continuous animation. WorkManager requests refresh approximately every 30 minutes with network availability and backoff; Android can defer this. Manual requests are deduplicated for 30 seconds; normal refreshes reuse results for 15 minutes. A failed refresh preserves the last successful, timestamped forecast and displays a cached/offline state. There is no fabricated live data or default pretend city.

Optional passive background location following needs foreground location first and “Allow all the time” through Android's app permission settings. It reads recent, sufficiently accurate last-known fixes, moves only after 3 km, and never starts GPS in the worker. With permission denied or no recent fix, saved coordinates still work. Background refresh can be disabled independently.

## Providers and privacy

- Open-Meteo forecast, geocoding and air quality APIs. Free access is intended for personal non-commercial use and subject to provider rate limits and terms. Weather attribution: Open-Meteo, CC BY 4.0. Air quality: CAMS ENSEMBLE via Open-Meteo.
- NOAA/NWS active official alerts for U.S. cities, with a descriptive User-Agent. Current-device coordinates have unknown country, so the alert endpoint is attempted; unsupported coverage is shown as unavailable. Known non-U.S. cities omit this feature.
- Requests disclose the selected coordinates or search query to the relevant provider. Preferences, saved cities and cached forecasts stay on device. No analytics, ads or account. Android Clear storage deletes local records.
- Radar remains a stated future feature; no paid or unlicensed radar tiles are used.

Required development network destinations: `api.open-meteo.com`, `geocoding-api.open-meteo.com`, `air-quality-api.open-meteo.com`, `api.weather.gov`, plus the standard Google/Maven/Gradle dependency hosts. On a phone the app uses normal Android Internet access.

## Structure

- `data/`: provider DTOs, Retrofit/OkHttp, Room cache, DataStore preferences
- `ui/`: lifecycle-aware MVVM state, Compose screens and atmospheric Canvas effects
- `AtmosApp.kt`: WorkManager refresh and passive location handling
- `widgets/`: seven registered native AppWidget providers and configuration activity
- `app/src/test/`: provider parsing and weather conversion checks

Animations pause outside the resumed lifecycle, respect Android's animator-duration setting, and can be reduced or disabled in-app. Widget grid sizes are launcher hints: Samsung and other launchers may apply their own spacing and need device testing. No emulator or physical-device rendering/performance validation is implied by a successful APK build.

## 2.0 redesign

See [the redesign notes](docs/REDESIGN.md) for the dimensional scene, seven matching widgets, AM/PM formatting, motion/audio controls, migration behavior, validation, and debug-signing compatibility. New builds use version code 2 and the CI artifact is `atmos-weather-v2-debug`. Android Studio and `./gradlew assembleDebug` remain supported.
