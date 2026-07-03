# FTC Demo Robot Controller

This workspace is a full FTC SDK Android Studio project plus local helper
scripts for working with the Control Hub.

## Android Studio Tutorial

This tutorial assumes you switch Wi-Fi by hand. Do not use `ftc-net-run` for
these steps.

### 1. Open And Build

Open this directory itself in Android Studio. Gradle should import two Android
modules:

- `FtcRobotController`
- `TeamCode`

The local OpMode source is:

```text
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/DanceBotAuto.java
```

Build the Robot Controller APK from Android Studio with **Build > Make
Project**, or from the Android Studio Terminal:

```bash
./gradlew :TeamCode:assembleDebug
```

The debug APK is written to:

```text
TeamCode/build/outputs/apk/debug/TeamCode-debug.apk
```

### 2. Connect To The Control Hub

On macOS, manually join the Control Hub Wi-Fi:

```text
SSID: FTC-L3OE
Password: password
```

Wait a few seconds, then confirm the Control Hub is reachable from the Android
Studio Terminal:

```bash
ping -c 2 192.168.43.1
```

### 3. Deploy The Code

Deploy over Control Hub Wi-Fi ADB and start the Robot Controller app:

```bash
./deploy-ftc-apk
```

If Android Studio prompts for a deploy target, use the Control Hub device at
`192.168.43.1:5555`.

### 4. Launch DanceBot

Keep the Mac connected to `FTC-L3OE`, lift the robot or make sure it has room to
move, then launch the autonomous OpMode from the Android Studio Terminal:

```bash
./run-ftc-opmode-robocol run-bounded 'DanceBot Auto' --duration 22 --allow-motion
```

For a one-motor-at-a-time diagnostic:

```bash
./run-ftc-opmode-robocol run-bounded 'DanceBot Motor Test' --duration 22 --allow-motion
```

To list the OpModes the Robot Controller sees:

```bash
./run-ftc-opmode-robocol list
```

Tail the OpMode logs in another Android Studio Terminal tab if needed:

```bash
./logcat-ftc
```

If the launcher times out and Logcat says `Incompatible robocol versions`, make
sure the script is using the same FTC SDK generation as the installed Robot
Controller app. This repo defaults to FTC SDK `11.1.0` / Robocol `124`.

### Launch Shortcut

After manually connecting to the Control Hub Wi-Fi, you can use the shorter
launcher:

```bash
./launch-dancebot
```

The same launcher is available as Android Studio Gradle tasks:

```bash
./gradlew launchDanceBot
./gradlew launchDanceBotMotorTest
./gradlew listDanceBotOpModes
```

Use `DANCEBOT_DURATION=10 ./launch-dancebot` to change the bounded run
duration.

When you are done, manually switch the Mac back to your normal Wi-Fi.

If the app deploys but the robot does not move, run `DanceBot Motor Test` first.
It spins `motor0`, `motor1`, `motor2`, and `motor3` one at a time in both
directions and logs under the `DanceBotMotorTest` tag. Lift the robot or remove
the wheels before running this diagnostic.

This project is based on FTC SDK `v11.1`. The Driver Station app should be on
the same FTC SDK major/minor release as the Robot Controller app you deploy.

## OnBot Java Mirror

The old OnBot Java source mirror is still present at:

```text
onbot/DanceBotAuto.java
```

The source-deploy helper still uses that mirror:

```bash
./deploy-ftc-dance-opmode
./build-ftc-onbot
```

For local Android Studio builds, edit the `TeamCode` copy first. Keep the
`onbot` mirror synchronized only if you still need the OnBot Java workflow.

## FTC Control Hub Network Handoff

This workspace contains `ftc-net-run`, a local macOS helper for commands that
must run while connected to the FTC Control Hub access point.

The helper:

1. Records the current Wi-Fi SSID.
2. Optionally disconnects VPN.
3. Connects to `FTC-L3OE`.
4. Waits for `http://192.168.43.1:8080/`.
5. Runs the command you provide.
6. Attempts to restore the previous Wi-Fi and reconnect VPN.

## Basic Probe

```bash
./ftc-net-run -- curl -I http://192.168.43.1:8080/
```

If the FTC Wi-Fi password is not saved in Keychain:

```bash
FTC_WIFI_PASSWORD='your-password' ./ftc-net-run -- curl -I http://192.168.43.1:8080/
```

## VPN Handling

For Cisco Secure Client, use:

```bash
CISCO_SECURE_CLIENT=1 ./ftc-net-run -- curl -I http://192.168.43.1:8080/
```

The helper will try to detect the currently connected Cisco VPN host before it
disconnects. You can also pin a VPN host explicitly:

```bash
CISCO_SECURE_CLIENT=1 \
CISCO_VPN_HOST='your-vpn-host.example.com' \
./ftc-net-run -- curl -I http://192.168.43.1:8080/
```

If your VPN is managed by a macOS Network service:

```bash
VPN_SERVICE='Your VPN Name' ./ftc-net-run -- curl -I http://192.168.43.1:8080/
```

If your VPN needs custom commands:

```bash
VPN_DISCONNECT_CMD='your disconnect command' \
VPN_RECONNECT_CMD='your reconnect command' \
./ftc-net-run -- curl -I http://192.168.43.1:8080/
```

Run `./ftc-net-run --help` for all supported environment variables.

## Hardware Report

To collect a read-only hardware/configuration report from the robot and then
restore Cisco VPN:

```bash
./start-ftc-report-handoff
```

If macOS redacts the current SSID from command-line tools, provide the Wi-Fi
network to restore:

```bash
RESTORE_SSID='Your Wi-Fi' ./start-ftc-report-handoff
```

That launcher starts a detached background job. Codex may disconnect while the
job switches networks. After VPN reconnects, the report should be available at:

```text
reports/latest-ftc-report.md
```

Each run also writes a full raw collection directory:

```text
reports/ftc-robot-YYYYMMDD-HHMMSS/
```

The collector performs GET requests against the Robot Controller console and, if
ADB is available on `192.168.43.1:5555`, pulls FTC configuration XML/JSON files
from `/sdcard/FIRST`. It does not start OpModes or send movement commands.

## Control Hub Config Workaround

If you do not have a Driver Station available, `install-ftc-controlhub-config`
can install a minimal FTC hardware configuration over ADB. It assumes this Mac is
already connected to the Control Hub Wi-Fi and does not actuate motors.

```bash
./install-ftc-controlhub-config
```

By default it creates and activates `dancebot.xml` with four DC motors named
`motor0`, `motor1`, `motor2`, and `motor3` on the embedded Control Hub motor
ports 0, 1, 2, and 3.

For the 312 rpm motors, the default XML tag is the generic FTC SDK motor type:

```text
MOTOR_XML_TAG=Motor
```

If you want the closest built-in motor profile instead, use:

```bash
MOTOR_XML_TAG=NeveRest20Gearmotor ./install-ftc-controlhub-config
```

The script writes the XML file to `/sdcard/FIRST`, then uses Android `run-as` to
update the Robot Controller app's active hardware configuration preference. If
`run-as` is unavailable for the installed Robot Controller app, the XML can be
pushed but the script cannot make it active automatically.
