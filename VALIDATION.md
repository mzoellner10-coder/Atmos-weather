# Atmos Weather 2.0 validation

Run from the project root:

```sh
tools/cloud-build.sh assembleDebug testDebugUnitTest lintDebug
```

The local check suite executes 19 tests with no skipped tests:

- Seven existing forecast parsing, condition mapping, units, cache-boundary and forecast-hour tests.
- Six v2 tests covering the requested AM/PM examples, opt-in 24-hour format, selected-city zones and daylight-saving transitions, solar progression, legacy settings, AQI/UV categories and forecast-based day/night art.
- Four native Android/Robolectric tests for original sculptures, all seven widget renderers at nominal and resized dimensions, a real Room cache through concurrent refreshes and offline failures, and preservation of widget appearance/location bindings during passive movement.
- Two Compose/Robolectric smoke tests for actual navigation and persisted time preference, and a loaded dashboard rendering from a clearly identified test fixture.

Native images are generated in `app/build/reports/visuals`; they are simulator/test-fixture renderings, not phone screenshots or live weather demonstrations. Test results and lint reports are under `app/build/reports/`. Tests do not disable assertions or bypass artifact/TLS verification.

The v2 debug APK has application ID `com.atmos.weather`, minimum API 29, target API 35, version code 2 and version name 2.0. Local APK signing matches the prior local v1 certificate (`9bdbcf05e04cdf22798e36854305ed8023368d6fbdb92963acfd2eacdf1507b6`). A fresh GitHub runner's default debug certificate may differ from a previously installed APK. No keystore is committed; updating an installed APK requires a matching signer.

The GitHub workflow builds `atmos-weather-v2-debug` and uploads reports on pushes/PRs/manual dispatch. See the actual Actions run for its current result; local checks alone do not establish CI success.

This environment's proxy blocks live weather-provider requests and the inspiration website. Existing free provider APIs, schemas, caching, permissions and WorkManager scheduling were preserved; no new live weather values are invented. Physical Samsung One UI placement, high-refresh-rate performance, foldable layouts, sound/audio focus and real permission behavior still require device testing.
