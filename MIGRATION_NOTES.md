# Toolchain Migration — AGP 7.2.1 → 8.5.2 / Gradle 7.4.2 → 8.9

**Why:** CI run #75 failed at `:app:mergeExtDexDebug` — the D8 dexer bundled
with AGP 7.2.1 throws `NullPointerException (String.length() because
<parameter1> is null)` on the modern library set introduced by Phase 2
(libv2ray AAR runtime jar, WireGuard tunnel 1.0.20260102,
androidx.collection-jvm 1.5.0). The old dexer cannot process those jars;
upgrading AGP (and its D8) is the fix. Toolchain only — **no feature or app
code changed.**

## Changes

| File | Change |
|---|---|
| `build.gradle` (root) | `com.android.application` + `com.android.library` plugin versions `7.2.1` → `8.5.2` |
| `gradle/wrapper/gradle-wrapper.properties` | distribution `gradle-7.4.2-bin.zip` → `gradle-8.9-bin.zip` (AGP 8.5 requires Gradle 8.9+) |
| `app/build.gradle` | added `namespace 'com.jepongdevxyz.fablevpn'` |
| `gen/build.gradle` | added `namespace 'com.jepongdevxyz.fablegen'` |
| `app/src/main/AndroidManifest.xml` | removed `package="com.jepongdevxyz.fablevpn"` (AGP 8 forbids it; namespace replaces it) |
| `gen/src/main/AndroidManifest.xml` | removed `package="com.jepongdevxyz.fablegen"` |
| `app/build.gradle`, `gen/build.gradle` | added `buildFeatures { buildConfig true }` — AGP 8 generates BuildConfig **off** by default; app's `LoadDefaultConfig` reads `BuildConfig.XPTUNNELCONFIGURL / XPTUNNELCONFIG / XPTUNNELUDPCONFIG` and gen's `SignatureCheck` references BuildConfig |
| `gradle.properties` | added `android.suppressUnsupportedCompileSdk=33` — compileSdk stays **33** by design; this silences the AGP 8.5 unsupported-compileSdk warning instead of raising compileSdk |
| `app/build.gradle` | `packagingOptions { … }` → `packaging { … }` (DSL renamed; old name removed in AGP 8) |
| `app/build.gradle`, `gen/build.gradle` | `lintOptions { … }` → `lint { … }` (same rename; contents unchanged: `abortOnError false`) |

Notes:
- Only two Android modules exist (`:app`, `:gen`, both application modules);
  `settings.gradle` includes nothing else, so there are no library modules
  to migrate.
- Each `namespace` equals the module's old manifest `package`, so the
  generated `R` and `BuildConfig` classes keep their exact packages
  (`com.jepongdevxyz.fablevpn.R`, `com.jepongdevxyz.fablegen.*`) — no source
  imports needed changing.
- `compileSdk 33 / targetSdk 33 / minSdk 21`, `buildToolsVersion "33.0.0"`,
  signing configs, `buildTypes.each { buildConfigField … }` wiring and all
  dependencies are untouched.

## What CI should do differently

**Nothing.** Same workflow, same JDK 17 (AGP 8.5 requires JDK 17 — already
the CI JDK). The wrapper download switches to Gradle 8.9 automatically.
`restore_binaries.py` still runs before Gradle, so `app/libs/libv2ray.aar`
is in place for the flatDir repository exactly as before.

## Residual risks / expected warnings

- `buildToolsVersion "33.0.0"` is below AGP 8.5's minimum (34.0.0): AGP
  ignores the pin and uses its default build-tools 34.0.0, with a warning.
  Left as-is to keep the diff toolchain-minimal; harmless in CI.
- Root `task clean` uses `rootProject.buildDir` — deprecated (warning) in
  Gradle 8, removed in Gradle 9. Still works on 8.9.
- Older dependencies may print AGP-8 deprecation warnings but are known to
  build on AGP 8: appcompat 1.4.2 / material 1.6.1 (consumer rules fine),
  MPAndroidChart v3.0.3 and volley 1.0.19 (pre-AndroidX-era jars pass
  through unchanged; `android.useAndroidX` without jetifier was already
  the configuration), WireGuard tunnel 1.0.20260102 and libv2ray AAR both
  target modern AGP and are the reason for this upgrade.
- AGP 8's manifest merger is stricter; the app's existing
  `tools:overrideLibrary` for the API-24 cores is unchanged and remains
  the mechanism that lets minSdk 21 coexist with those AARs.
- AAPT2 in AGP 8 is stricter about resource linking; no resource changes
  were made, and `android.nonTransitiveRClass=true` was already set, so
  library R-class references were already non-transitive before this
  migration.
