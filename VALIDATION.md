# Validation

The cloud machine built the native debug APK with:

```sh
python3 tools/setup_cloud.py
tools/cloud-build.sh assembleDebug testDebugUnitTest lintDebug
```

The final build completed successfully. Seven JUnit tests executed: seven passed, zero failed, zero skipped. They cover Open-Meteo forecast/geocoding/air-quality parsing, Celsius/Fahrenheit conversion, WMO condition mapping, location-free initial settings, cache expiry/clock boundaries, and timezone-aware selection of forecast hours, including expired forecast horizons.

Android lint completed with zero errors and two nonblocking warnings: KAPT can be replaced with KSP, and the older allowBackup flag triggers DataExtractionRules guidance despite the explicit Android 12+ exclusion rules. No lint errors were suppressed and no test assertions were disabled.

The APK passed `apksigner verify --verbose` (v2 signing) and `zipalign -c 4`. Package metadata reports `com.atmos.weather`, version 1.0, minimum API 29 and target API 35. This is a debug-signed test build.

Gradle distribution, Gradle wrapper JAR, Temurin JDK and Android command-line tools were checked against their published checksums. Java uses the managed system CA trust and inherited proxy; TLS verification remains enabled. Setup was rerun successfully with already-installed tools.

Live Open-Meteo requests from this cloud machine received a proxy policy denial. The four provider domains were saved to the environment configuration draft, along with installation and startup instructions; draft saving does not apply or publish them. Live provider responses, offline behavior on a phone, permissions, widget placement/resizing, Samsung One UI appearance, animation performance and foldable layouts have not been validated on an emulator or physical device.

Before release, install on the target phone, select a city and check current, hourly, daily, precipitation and air-quality data; verify U.S. alerts where available; disconnect networking to check cached forecasts; add and resize all seven widgets; exercise approximate/denied location and background refresh. There is no claim of production readiness or completed device testing.
