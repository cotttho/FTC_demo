# ADB Tutorial For FTC Debugging

These commands are for working with a REV Control Hub from the Android Studio
Terminal. They assume you have manually joined the Control Hub Wi-Fi.

Default target for this robot:

```bash
export FTC_ADB=192.168.43.1:5555
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
```

## 1. Connect

Confirm the Control Hub is reachable:

```bash
ping -c 2 192.168.43.1
```

Connect ADB over Wi-Fi:

```bash
$ADB connect "$FTC_ADB"
$ADB devices -l
$ADB -s "$FTC_ADB" get-state
```

Expected state:

```text
device
```

If ADB is stale, reset the local ADB server:

```bash
$ADB kill-server
$ADB start-server
$ADB connect "$FTC_ADB"
```

## 2. Build And Install

Build the Robot Controller APK:

```bash
./gradlew :TeamCode:assembleDebug
```

Install it on the Control Hub:

```bash
$ADB -s "$FTC_ADB" install -r -d TeamCode/build/outputs/apk/debug/TeamCode-debug.apk
```

Use the repo helper for the normal deploy flow:

```bash
./deploy-ftc-apk
```

If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed app
was signed with a different key. If you are okay removing the current app data,
rerun:

```bash
UNINSTALL_INCOMPATIBLE=1 ./deploy-ftc-apk
```

## 3. Start Or Stop The App

Robot Controller package name:

```bash
export FTC_PACKAGE=com.qualcomm.ftcrobotcontroller
```

Start the app:

```bash
$ADB -s "$FTC_ADB" shell monkey -p "$FTC_PACKAGE" -c android.intent.category.LAUNCHER 1
```

Force-stop the app:

```bash
$ADB -s "$FTC_ADB" shell am force-stop "$FTC_PACKAGE"
```

Restart the app:

```bash
$ADB -s "$FTC_ADB" shell am force-stop "$FTC_PACKAGE"
$ADB -s "$FTC_ADB" shell monkey -p "$FTC_PACKAGE" -c android.intent.category.LAUNCHER 1
```

## 4. Read Logs

Clear old logs:

```bash
$ADB -s "$FTC_ADB" logcat -c
```

Tail DanceBot logs:

```bash
./logcat-ftc
```

Tail broader Robot Controller logs:

```bash
$ADB -s "$FTC_ADB" logcat -v time \
  DanceBotAuto:V \
  DanceBotMotorTest:V \
  OpmodeRegistration:V \
  RobotCore:V \
  FtcEventLoop:V \
  EventLoopManager:V \
  PeerDiscovery:V \
  AndroidRuntime:E \
  '*:S'
```

Save a log snapshot locally:

```bash
mkdir -p reports
$ADB -s "$FTC_ADB" logcat -d -v time > reports/logcat-snapshot.txt
```

Useful strings to search for:

```bash
rg "DanceBot|CMD_INIT_OP_MODE|CMD_RUN_OP_MODE|Incompatible robocol|Exception|ERROR" reports/logcat-snapshot.txt
```

## 5. Verify Deployment

Check installed package info:

```bash
$ADB -s "$FTC_ADB" shell dumpsys package com.qualcomm.ftcrobotcontroller | head -80
```

Check the app process:

```bash
$ADB -s "$FTC_ADB" shell pidof com.qualcomm.ftcrobotcontroller
$ADB -s "$FTC_ADB" shell ps | rg ftcrobotcontroller
```

Confirm the Robot Controller web console is up:

```bash
curl -I http://192.168.43.1:8080/
```

List OpModes over Robocol:

```bash
./run-ftc-opmode-robocol list
```

## 6. Pull FTC Files

Pull the Robot Controller log file:

```bash
$ADB -s "$FTC_ADB" pull /sdcard/robotControllerLog.txt reports/robotControllerLog.txt
```

List FTC storage:

```bash
$ADB -s "$FTC_ADB" shell ls -la /sdcard/FIRST
$ADB -s "$FTC_ADB" shell find /sdcard/FIRST -maxdepth 3 -type f
```

Pull hardware configuration files:

```bash
mkdir -p reports/FIRST
$ADB -s "$FTC_ADB" pull /sdcard/FIRST reports/FIRST
```

## 7. Common Recovery Commands

Reconnect after switching Wi-Fi:

```bash
$ADB disconnect "$FTC_ADB" || true
$ADB connect "$FTC_ADB"
$ADB devices -l
```

Reboot the Control Hub:

```bash
$ADB -s "$FTC_ADB" reboot
```

After reboot, wait for the Control Hub Wi-Fi to return, reconnect to it, then:

```bash
$ADB connect "$FTC_ADB"
```

Check battery and device properties:

```bash
$ADB -s "$FTC_ADB" shell dumpsys battery
$ADB -s "$FTC_ADB" shell getprop ro.product.model
$ADB -s "$FTC_ADB" shell getprop ro.build.version.release
```

## 8. Launch DanceBot

ADB installs and starts the Robot Controller app, but it does not start an
OpMode. After deployment, use the Robocol launcher:

```bash
./launch-dancebot
```

Diagnostic motor test:

```bash
./launch-dancebot motor-test
```

Always lift the robot or make sure it has room to move before launching an
OpMode that commands motor power.
