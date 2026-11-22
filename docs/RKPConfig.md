# RKPConfig (Toolbox)

A page ported into this project from the standalone app **True RKP** (note: True RKP is a test project of the ReKernelFlasher team); the entry is at **Settings -> Toolbox -> RKPConfig**.
Functionality matches the standalone version: read/apply RKP config, renew provisioning, view status, generate CSR, verify certificate,
engineer mode, clear data, clear/copy logs, set su path, and persist logs to disk.

---

## 1. Entry and routing

| Location | File | Description |
|---|---|---|
| Toolbox list item | `ui/screens/toolbox/ToolboxContent.kt` | Added an `RKPConfig` entry (below "Fix RKP") |
| Route registration | `MainActivity.kt` | Added `composable("toolbox/rkp_config")` |
| Page | `ui/screens/toolbox/RkpConfigContent.kt` | Compose page (Material3, following the project's component style) |

---

## 2. New core classes (`toolbox/rkp/`)

| File | Standalone counterpart | Responsibility |
|---|---|---|
| `RkpCommands.kt` | `RkpCommands.java` | The single source of all shell commands (getprop/setprop/csr/certify/clear/engineer/dump) |
| `RkpShell.kt` | `Sh.java` | Root shell execution layer: `ProcessBuilder(su,"-c",script)` + sentinel-based per-command output splitting + timeout + streaming callbacks. **Keeps a customizable su path**, so libsu is not used |
| `RkpLauncher.kt` | `RkpdLauncher.java` | Extracts `assets/rkpdapp.apk`, finds the system rkpdapp, picks the setns helper, assembles the launch command |
| `RkpMain.kt` | `RkpMain.java` | `app_process` entry (`systemMain()` + `IPackageManager` proxy + Application attachment + PathClassLoader) |
| `RkpProvisioning.kt` | `Provisioning.java` | Calls AOSP rkpdapp via reflection to complete RKP provisioning and dump |
| `RkpWidevine.kt` | `Widevine.java` | Widevine certificate provisioning (**China URL first**) |
| `RkpHiddenApi.kt` | `HiddenApi.java` | Lifts hidden API restrictions via double reflection |
| `RkpPrefs.kt` | `Prefs.java` | Config storage |
| `RkpLogger.kt` | `LogStore.java` | Log output abstraction (one implementation each for the UI process and the rkpd child process) |

---

## 3. Launch chain (matches actual device behavior)

The command actually executed by "Renew provisioning":

```
<nativeLibraryDir>/libnsexec.so  /system/bin/app_process \
    -Djava.class.path='<this project's APK>' / \
    --nice-name=com.android.rkpdapp \
    com.panda.flashernova.toolbox.rkp.RkpMain \
    '<cacheDir>/rkpdapp.apk'
```

1. **`libnsexec.so`** (`jniLibs/arm64-v8a/`): an in-house setns helper that enters init's mount namespace and then `execve`s the following command.
   When it cannot be found, it falls back in order to `nsenter -t 1 -m --` and then a direct connection (the log states which one was used).
2. **`--nice-name=com.android.rkpdapp`**: disguises the process name.
3. **`RkpMain`**: runs RKP provisioning + Widevine inside `app_process`, then chowns the data directory and exits.

`RkpMain` has two key points internally (both pitfalls learned the hard way):

* **`IPackageManager` proxy**: `hasSystemFeature("cn.google.services")` always returns `false` (compatibility for devices without GMS).
* **A self-built `PathClassLoader` is required**: `createPackageContext("com.android.rkpdapp").classLoader`
  returns an **empty DexPathList** for packages installed via APEX, causing `ClassNotFoundException`; you must use
  `PathClassLoader(rkpdapp.apk, pkgCtx.classLoader)`.

---

## 4. New binary resources

| Path | Size | SHA-256 | Description |
|---|---|---|---|
| `app/src/main/assets/rkpdapp.apk` | 1,164,970 | `4a52e05b…6dd7ac65` | AOSP `com.android.rkpdapp` (RemoteProvisioner), Apache-2.0, unmodified |
| `app/src/main/jniLibs/arm64-v8a/libnsexec.so` | 417,136 | `08604b58…43057091` | In-house setns helper (statically compiled with NDK r29 / clang 21, same semantics as the original `librkp.so`) |

* `rkpdapp.apk` **can be missing and it still works**: it falls back to querying the installed copy on the device via `PackageManager`.
* `.gitignore` originally ignored `*.apk`; an exception line `!app/src/main/assets/rkpdapp.apk` has been added.
* `libnsexec.so` can also be missing: it falls back to `nsenter` or a direct connection.
* `packaging.jniLibs` in `app/build.gradle.kts` gained `keepDebugSymbols += "**/libnsexec.so"`,
  to stop AGP's strip task from processing this prebuilt artifact.

---

## 5. Manifest changes

Added `<queries>` (required on Android 11+ to query these packages via `PackageManager`):

```xml
<queries>
    <package android:name="com.android.rkpdapp" />
    <package android:name="com.google.android.rkpdapp" />
    <package android:name="com.android.remoteprovisioner" />
    <package android:name="com.oplus.engineermode" />
    <package android:name="io.github.vvb2060.keyattestation" />
</queries>
```

---

## 6. UI

Follows the project's Material3 style (`surfaceVariant` cards + the project's typography), in four blocks:

1. **Status**: ROOT / StrongBox / namespace helper
2. **Config**: enable RKP, server address (with Google / GrapheneOS shortcut chips), StrongBox only, TEE only, connection timeout slider
3. **Actions**: read config / apply config / renew provisioning / view status / generate CSR / verify certificate / engineer mode / clear data
   (**long-press "Apply config" to set the su path**)
4. **Log**: a monospaced log with level-based coloring that can be copied and cleared, and is also written to `filesDir/rkp.log`

---

## 7. Build notes

* This project uses `compileSdk = 37`. The standard channel of the current SDK has no `platforms;android-37`;
  the preview channel provides `platforms;android-37.0/37.1/37.2`:
  ```bash
  sdkmanager --channel=3 "platforms;android-37.0"
  ```
  If AGP cannot find `android-37`, you can temporarily lower `compileSdk` to 36 to verify this feature (it does not depend on API 37).
* The first build needs to download dependencies such as AGP 8.13.2 / Compose / Room / KSP / libsu.
* `minSdk = 29`; the APIs this feature uses (`java.time`, `android.system.Os`, `PathClassLoader`) are all satisfied.

---

## 8. Verification status

### ✅ Full build passed on the local machine (2026-09-26)

```
BUILD SUCCESSFUL in 29s
Artifact: app/build/outputs/apk/debug/app-debug.apk
          31,110,621 B
          SHA-256 8e323777f210f3d650669c07a1c6f953382545c044fcc01e9fafc889ddc4bf0c
Warnings: 0
```

Artifact contents checked (`aapt2` + `dexdump`):

| Check | Result |
|---|---|
| Package name / version | `com.panda.flashernova`, versionCode 1 / versionName 1.0.0 |
| `native-code` | `arm64-v8a` (includes `lib/arm64-v8a/libnsexec.so`, 417,136 B) |
| Embedded resources | `assets/rkpdapp.apk` (1,164,970 B) ✓ |
| All new classes | `RkpMain` / `RkpProvisioning` / `RkpLauncher` / `RkpConfigContent` are all in the dex |
| **app_process entry** | `RkpMain.main` = `PUBLIC STATIC FINAL ([Ljava/lang/String;)V` ✓ |
| Key strings | `setHiddenApiExemptions`, `cn.google.services`, `remote_provisioning.hostname` are all present |

### Three fixes during the build

1. **`app/build.gradle.kts`: added `compileSdkMinor = 0`**
   In the public SDK repository the platform package for API 37 is `platforms;android-37.0` (there is no `android-37`),
   and the original config reported `Failed to find target with hash string 'android-37'`.
   `compileSdk = 37` + `compileSdkMinor = 0` is what matches the installed `android-37.0`.
2. **`.gitignore`: added `!app/src/main/assets/rkpdapp.apk`**
   The existing `*.apk` rule would have ignored the embedded rkpdapp as well.
3. **Local Maven mirror config** (`~/.gradle/init.gradle`, not part of this project)
   rewrites `dl.google.com` / `repo1.maven.org` / `plugins.gradle.org` in place to the Alibaba Cloud mirror;
   **jitpack.io is left as is** (libsu is only published on JitPack, while miuix is on Maven Central and would be hit first by the Central mirror).

---

## 9. Signed release build

### ✅ Signed package produced (2026-09-26)

```
BUILD SUCCESSFUL in 1m 36s    (including lintVital; no tasks skipped)
Artifact: app/build/outputs/apk/release/app-release.apk
          23,337,231 B
          SHA-256 f554d9d2dc3f3e766b001c47b863272cfff9a967c122b15b29ee3837f286532d
```

`apksigner verify --print-certs` result:

```
Verifies
Verified using v2 scheme: true      (minSdk 29, v2 is enough, v1 is not needed)
Number of signers: 1
certificate DN:                CN=Bacillusf, OU=Unknown, O=Unknown, L=Chengdu, ST=Sichuan, C=CN
certificate SHA-256 digest:    30a1646080234ff50a0c7ead70931e1a8ef2a0d915defde30af8196334ed957d
```

This fingerprint **exactly matches** the certificate in `RKF-release-key.jks`, confirming that the project's own release key was used.

### Signing config (`keystore.properties`)

`app/build.gradle.kts` reads the passwords from `keystore.properties` in the **project root** (this file is in `.gitignore` and will not be committed):

```properties
storeFile=../RKF-release-key.jks
storePassword=***
keyAlias=rekernelflasher
keyPassword=***
```

> Note that `storeFile` is written as `../RKF-release-key.jks`: in AGP, `file()` is resolved relative to the **module directory** (`app/`),
> while the keystore lives in the repository root, so you need to go up one level.
> Also, the actual alias inside the PKCS12 is lowercase `rekernelflasher` (go by `keytool -list` for the exact case).

### Reproduce

```bash
cd items/RekernelFlasher
./gradlew :app:assembleRelease        # requires keystore.properties to exist
```

### Real-device behavior

The feature logic is fully identical to the **standalone True RKP v1.0 that has been verified on a real device**
(the standalone version ran end to end on OnePlus PJZ110 / Android 16 / KernelSU: RKP provisioning succeeded 2/2, and Widevine went through the China URL and returned 1844 bytes).
The version inside this project has not yet been tested on a device; after installing, it is recommended to first tap "Read config" (read-only, safe) -> "View status" -> and then use "Renew provisioning".
