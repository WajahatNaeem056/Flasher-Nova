# Flasher Nova
An Android kernel flashing tool that lets you flash, back up and restore Android kernel images on rooted devices.
Developed on top of [KernelFlasher](https://github.com/fatalcoder524/KernelFlasher) and maintained by [WajahatNaeem056](https://github.com/WajahatNaeem056).
<!-- Floating SVG icon on the right; width controls the icon size, align="right" makes the text wrap around it -->
[![Latest release](https://img.shields.io/github/v/release/WajahatNaeem056/Flasher-Nova?label=Release&logo=github)](https://github.com/WajahatNaeem056/Flasher-Nova/releases)
[![License: GPLv3.0](https://img.shields.io/badge/License-GPLv3.0-orange?logo=gnu)](https://www.gnu.org/licenses/quick-guide-gplv3.html)
<img align="right" width="100" src="./docs/RI.svg" alt="Flasher Nova Logo" style="display:block; margin: 0; vertical-align: bottom;">


---
## Inspiration
The original KernelFlasher is unfriendly to beginners: it has no second confirmation prompt, so it is easy to flash the wrong partition, and many beginners have no habit of backing up, so a bad flash leaves them helplessly waiting for a full firmware package... The original repository is also archived, so I made this fork.
## Features
### Documentation / docs
[Usage](docs/USAGE_EN.md)
### Home
| Feature | Description |
|---|---|
| Device info | Shows model, build number, Android version, app version, kernel version, root manager (KernelSU/Magisk/APatch), slot suffix, SUSFS version and Boot HAL version |
| Reboot menu | Expands from the button in the top right; supports reboot to system/Recovery/Bootloader/Download/EDL, with a confirmation dialog every time |
### Flash
| Feature | Description |
|---|---|
| Slot management | View A/B slot info (bootable, marked successful, Boot SHA1, image format, ramdisk info) |
| Flash AK3 Zip | Select an AnyKernel3-compatible zip to flash |
| Flash AK3 Zip (mkbootfs) | Flash an AK3 zip in mkbootfs mode |
| Flash partition image | Select an .img file to flash to a given partition |
| Back up partition | Choose partitions to back up locally |
| Restore backup | Restore partitions from a previous backup |
| vendor_dlkm management | Mount/unmount/map/unmap the vendor_dlkm partition |
| Check kernel version | Extract the kernel version string from the current boot image |
| Operation history | View the records of all flash/backup/reboot operations |
### Settings
| Feature | Description |
|---|---|
| Backup management | View and manage all backups |
| Update sources | Add and manage kernel update JSON URLs |
| Auto backup | Toggle; when enabled, the target partition is backed up to `/sdcard/FlasherNova-AutoBackup/` before flashing a partition or AK3 |
| View auto backup records | Shows auto backup history; long-press to locate the file |
| Save ramoops | Save the kernel crash log |
| Save dmesg | Save the kernel log |
| Save logcat | Save the system log |
| UI scale (DPI) | Drag the slider to adjust the UI scale (50%-150%); tap to apply |
### Second confirmation
All operations have a confirmation dialog (with a fade-in animation), including:
- Flashing AK3/images/KernelSU drivers -> shows the source path -> destination partition path
- Backing up/restoring/deleting partitions
- Rebooting to each mode
### Operation history
- Persisted to `/sdcard/FlasherNova/history.json`
- Record format: `yyyy-MM-dd_HH operation description`
- History can be cleared
### Auto backup
- When enabled, the target partition image is extracted with `dd` before flashing
- Save path: `/sdcard/FlasherNova-AutoBackup/{unix timestamp}/{partition name}.img`
- A `backup.yml` metadata file and a `summary.json` master index are also generated
- Long-press a record on the records page to open the backup file with the system file manager
---
## Tech stack
| Category | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM |
| Root | libsu |
| Database | Room |
| Serialization | kotlinx.serialization |
| HTTP | OkHttp + Retrofit |
## Native binaries
| File | Purpose |
|---|---|
| magiskboot | Boot image unpack/repack |
| lptools_static | Logical partition management |
| httools_static | Fstab export, AVB, mounting |
| bootctl | A/B slot control |
| busybox | General-purpose shell toolkit |
## Build
```bash
# Prerequisites: JDK 21, Android SDK (compileSdk 37)
./gradlew assembleDebug   # Debug build
./gradlew assembleRelease # Release build
```
## Credits
- [Bacillusf](https://github.com/Bacillusf) - author of ReKernelFlasher
- [fatalcoder524](https://github.com/fatalcoder524/KernelFlasher) - KernelFlasher fork that ReKernelFlasher was built on
- [capntrips](https://github.com/capntrips/KernelFlasher) - original author of KernelFlasher
- [WajahatNaeem056](https://github.com/WajahatNaeem056) - maintainer of Flasher Nova

Licensed under GPLv3. See the `LICENSE` file.
