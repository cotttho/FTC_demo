# DanceBot Demo Concepts

DanceBot is a small FTC SDK demo that proves this project can build, deploy,
map hardware, launch an OpMode, and command motors from a REV Control Hub.

The main code lives here:

```text
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/DanceBotAuto.java
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/DanceBotMotorTest.java
```

## What The Demo Does

`DanceBot Auto` is an autonomous OpMode. When it starts, it runs a sequence of
ten 2-second motor-power steps. Each step sends a different power pattern to
four motors named:

```text
motor0
motor1
motor2
motor3
```

The power is intentionally modest:

```text
0.35
```

The sequence includes all-forward, all-reverse, alternating directions, and
partial motor patterns. It is meant to create visible movement without needing
gamepad control.

`DanceBot Motor Test` is a diagnostic OpMode. It spins one motor at a time in
both directions. Use it when the autonomous routine starts but the robot does
not move as expected.

## Key FTC Concepts

### OpModes

An OpMode is the code unit the FTC Robot Controller can run. DanceBot uses
`LinearOpMode`, which means the program is written as one clear sequence:

```text
initialize hardware
wait for start
run motor steps
stop motors
```

The `@Autonomous` annotation registers the class with the Robot Controller:

```java
@Autonomous(name = "DanceBot Auto", group = "Demo")
```

That name is what the launcher sends when it starts the OpMode.

### Hardware Map

FTC code does not talk directly to motor ports by number. It asks the hardware
map for configured devices by name:

```java
hardwareMap.get(DcMotor.class, "motor0")
```

The Control Hub configuration must contain motors with the exact names
`motor0`, `motor1`, `motor2`, and `motor3`.

### Motor Power

Motor power is a value from `-1.0` to `1.0`:

```text
 0.35   forward
-0.35   reverse
 0.00   stopped
```

The demo uses `RUN_WITHOUT_ENCODER`, so it commands raw power rather than a
target encoder position.

### Start Gate

The OpMode initializes hardware first, then waits here:

```java
waitForStart();
```

Nothing should move before the OpMode is started.

### Active Loop

The code checks `opModeIsActive()` during each timed step. That lets the Robot
Controller stop the sequence cleanly if the OpMode is stopped remotely.

### Telemetry And Logs

The demo reports status through both FTC telemetry and Android logs:

```text
Status
Battery
Powers
Dance timer
```

The helper script `./logcat-ftc` filters to the DanceBot tags:

```text
DanceBotAuto
DanceBotMotorTest
```

### Battery Voltage

Both OpModes read the Control Hub voltage sensor and include battery voltage in
telemetry/logs. This is useful when motors are commanded but do not visibly
move.

### Remote Launch Without Driver Station

Normally an FTC Driver Station selects and starts OpModes. This project also
contains a Robocol launcher:

```bash
./launch-dancebot
```

That script sends the same kind of OpMode init/run commands over UDP to the
Robot Controller. The Robot Controller still runs the normal FTC OpMode code;
the script only replaces the Driver Station selection/start step.

## Typical Demo Flow

1. Build the APK in Android Studio or with Gradle.
2. Connect the Mac to the Control Hub Wi-Fi.
3. Deploy with `./deploy-ftc-apk`.
4. Confirm OpModes are listed with `./run-ftc-opmode-robocol list`.
5. Launch with `./launch-dancebot`.
6. Watch logs with `./logcat-ftc`.

## Safety

Before running `DanceBot Auto` or `DanceBot Motor Test`, lift the robot or make
sure it has enough room to move. Both OpModes command real motor power.

If something unexpected happens, stop the OpMode by running:

```bash
./run-ftc-opmode-robocol smoke '$Stop$Robot$'
```

or force-stop the Robot Controller app with ADB:

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
FTC_ADB=192.168.43.1:5555
$ADB -s "$FTC_ADB" shell am force-stop com.qualcomm.ftcrobotcontroller
```
