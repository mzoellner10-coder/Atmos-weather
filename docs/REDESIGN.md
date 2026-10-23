# Atmos Weather 2.0

The subsequent 2.1 additions (native radar and expanded optional UI sounds) are documented in [RADAR_AND_SOUND.md](RADAR_AND_SOUND.md).

The redesign is an original interpretation of the brief's tactile, playful weather philosophy: oversized editorial type, a dimensional weather object, touch-responsive movement, restrained data instruments, and black negative space. It uses no proprietary artwork, models, icons, recordings, branding or layouts. The reference site https://notboring.so/weather was attempted but blocked by this environment's outbound policy; no independent review of its current interface is claimed. The implementation follows the user's supplied visual brief.

## What changed

- An original Android Canvas renderer creates shaded suns, moons with craters, layered clouds, fog, rain, snow and storm sculptures. Compose and all seven native AppWidget providers share it.
- The main dashboard uses huge animated temperature text, a draggable/tappable weather sculpture, a swipeable hourly/daily/rain forecast panel and expandable circular instruments. Horizontal dragging tilts the scene; vertical gestures continue scrolling. Scene taps spring into a glow. Wind strength and bearing influence precipitation and air-current motion.
- Dynamic adds a moving weather-colored atmospheric backdrop. Hybrid keeps the immersive scene and dark forecast information. OLED Black keeps the surrounding background pure black and reduces ambient glow. All share readable text and colorful artwork.
- Wind, humidity, UV, AQI, precipitation, solar progression and interactive temperature graphs have purpose-built visualizations. Graphs support dragging/tapping and explicit previous/next controls. AQI categories and unavailable readings are explicit.
- The seven widgets are static size-aware Canvas artboards, using native RemoteViews ImageViews rather than an animation or video player. Narrow and horizontal layouts are distinct. Hourly, rain, daily-range and configurable metric layouts adapt to available dimensions; very small sizes fall back to a readable summary. Full accessible descriptions expose data behind the images. Widget bitmaps are bounded to 160,000 pixels; artwork uses an 8 MiB LRU cache. Font scaling, background opacity, transparent/light/dark/dynamic styles, Celsius/Fahrenheit, location visibility and two illustration styles are supported. Android 12+ reconfiguration preserves each widget's saved choices.
- Procedural rain, wind, thunder and short interaction sounds are original. They are disabled by default and guarded by lifecycle, normal ringer mode, existing media playback and audio focus. No audio runs in a worker or widget. Haptics are optional and respect system settings.

## Data and upgrades

Application ID remains `com.atmos.weather`; version code increases from 1 to 2. Room remains `atmos.db`, schema version 1. DataStore remains `atmos_preferences`, key `settings`; widget preference keys are retained. An additive settings decoder fills missing v2 defaults without replacing old values or cities. Legacy theme names and Off/Subtle/Full motion values retain their meaning. Sounds and haptics default off; AM/PM defaults on (24-hour mode off).

Open-Meteo, geocoding, air quality, NWS alerts, cache refresh/deduplication/backoff, approximate/precise location permissions and 30-minute WorkManager requests remain in place. Background refresh timing is approximate. Passive movement updates existing widget coordinate bindings without changing their appearance settings. Removing all locations clears the old displayed forecast.

Presentation helpers format provider-local timestamps and convert update/structured-alert instants into the selected forecast zone. US AM/PM is explicit, minutes appear when needed, and no seconds are shown. A 24-hour preference is available. DST changes are tested. Forecast timestamps and scheduling logic are not rewritten. Official NWS alert prose is preserved verbatim; its structured issued/expires timestamps use the selected format. Hourly day/night illustrations use the forecast's own solar times.

## Motion and performance

Full and Balanced use different particle counts; Minimal and Android's reduced-motion setting stop ambient frame loops and remove transition durations. Lifecycle gates stop frame loops and audio outside the resumed activity. Clock values are consumed in drawing lambdas, not in the data UI's composition. Dimensional shading is rendered into bounded reusable transparent bitmaps; frames transform those bitmaps and draw small particle sets. Widgets redraw only on updates/resizes, not continuously. No large video files or graphics engine dependency is used.

## Validation and delivery

`tools/cloud-build.sh assembleDebug testDebugUnitTest lintDebug` runs the local build. Unit/Robolectric tests exercise formatting (including DST and midnight), settings migration, cache expiry, a real Room cache with failed network requests and concurrent refreshes, UI navigation/settings persistence, sculpture rendering, and all widget artboards at nominal and reduced sizes. Native test images under `app/build/reports/visuals/` are labeled fixtures; they are not live weather demonstrations or phone screenshots.

CI builds artifact `atmos-weather-v2-debug` and uploads test/lint/visual reports. The redesign branch is intended for a pull request rather than a direct overwrite of main.

The local v2 APK uses the retained local debug keystore. Its SHA-256 certificate matches the prior local v1 APK: `9bdbcf05e04cdf22798e36854305ed8023368d6fbdb92963acfd2eacdf1507b6`. This supports an upgrade from that local build. GitHub-hosted runners generate a new default debug key on a fresh machine; compatibility with a previously installed GitHub-built APK cannot be guaranteed without its original keystore. No signing keys are committed or uploaded. Do not uninstall an existing app merely to bypass a signing conflict, since that deletes its local data.

No physical S21 Ultra/One UI/foldable performance, launcher, audio-focus or permission validation is claimed. A native simulator test is not a high-refresh-rate device benchmark. Live API requests remain subject to environment network policy.
