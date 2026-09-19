package org.firstinspires.ftc.teamcode;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

@Autonomous(name = "Duck: 20 Left 20", group = "Duck")
public class DuckOdometryAuto extends LinearOpMode {
    private static final double LEG_DISTANCE_CM = 20.0 * 2.54;
    private static final double LEFT_TURN_RADIANS = Math.PI / 2.0;
    private static final double PAUSE_SECONDS = 20.0;

    // Values recovered from the Automa Duck's original DeadWheelsLocalizer.
    private static final double POD_CENTER_OFFSET_CM = -7.5;
    private static final double POD_PARALLEL_HALF_SPACING_CM = 16.905;
    private static final double POD_WHEEL_CIRCUMFERENCE_CM = 10.0531;
    private static final double POD_COUNTS_PER_REVOLUTION = 2000.0;

    private static final double MAX_DRIVE_POWER = 0.35;
    private static final double MIN_DRIVE_POWER = 0.12;
    private static final double DRIVE_KP = 0.025;
    private static final double DRIVE_TOLERANCE_CM = 1.0;
    private static final double DRIVE_TIMEOUT_SECONDS = 8.0;
    private static final double HEADING_HOLD_KP = 0.018;
    private static final double MAX_HEADING_CORRECTION = 0.12;

    private static final double MAX_TURN_POWER = 0.30;
    private static final double MIN_TURN_POWER = 0.10;
    private static final double TURN_KP = 0.8;
    private static final double TURN_TOLERANCE_RADIANS = Math.toRadians(2.0);
    private static final double TURN_TIMEOUT_SECONDS = 5.0;

    private static final RevHubOrientationOnRobot.LogoFacingDirection HUB_LOGO_DIRECTION =
            RevHubOrientationOnRobot.LogoFacingDirection.UP;
    private static final RevHubOrientationOnRobot.UsbFacingDirection HUB_USB_DIRECTION =
            RevHubOrientationOnRobot.UsbFacingDirection.FORWARD;

    private DcMotor frontLeft;
    private DcMotor rearLeft;
    private DcMotor frontRight;
    private DcMotor rearRight;
    private DcMotor[] driveMotors;
    private IMU imu;

    @Override
    public void runOpMode() {
        frontLeft = hardwareMap.get(DcMotor.class, "front_left");
        rearLeft = hardwareMap.get(DcMotor.class, "rear_left");
        frontRight = hardwareMap.get(DcMotor.class, "front_right");
        rearRight = hardwareMap.get(DcMotor.class, "rear_right");
        imu = hardwareMap.get(IMU.class, "imu");
        driveMotors = new DcMotor[] {frontLeft, rearLeft, frontRight, rearRight};

        setDirections();
        setRunMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        stopDrive();

        RevHubOrientationOnRobot orientation =
                new RevHubOrientationOnRobot(HUB_LOGO_DIRECTION, HUB_USB_DIRECTION);
        imu.initialize(new IMU.Parameters(orientation));

        PodSnapshot pods = readPods();
        telemetry.addLine("Ready: forward 20 in, left 90 deg, forward 20 in");
        telemetry.addData("Left pod", "front_left: %d", pods.left);
        telemetry.addData("Right pod", "rear_right (reversed): %d", pods.right);
        telemetry.addData("Center pod", "front_right: %d", pods.center);
        telemetry.update();

        waitForStart();
        if (isStopRequested()) {
            return;
        }

        imu.resetYaw();

        try {
            if (!driveForward(LEG_DISTANCE_CM, headingDegrees())) {
                return;
            }
            if (!pauseBetweenMovements("First 20 inches complete")) {
                return;
            }
            if (!turnLeft(LEFT_TURN_RADIANS)) {
                return;
            }
            if (!pauseBetweenMovements("Left turn complete")) {
                return;
            }
            driveForward(LEG_DISTANCE_CM, headingDegrees());
        } finally {
            stopDrive();
        }

        telemetry.addData("Status", isStopRequested() ? "Stopped" : "Path complete");
        telemetry.addData("Final heading", "%.1f deg", headingDegrees());
        telemetry.update();
    }

    private boolean driveForward(double targetCm, double targetHeadingDegrees) {
        PodSnapshot start = readPods();
        ElapsedTime timeout = new ElapsedTime();

        while (opModeIsActive() && timeout.seconds() < DRIVE_TIMEOUT_SECONDS) {
            PodDelta movement = movementSince(start);
            double remainingCm = targetCm - movement.forwardCm;
            if (remainingCm <= DRIVE_TOLERANCE_CM) {
                stopDrive();
                return true;
            }

            double drive = Range.clip(remainingCm * DRIVE_KP,
                    MIN_DRIVE_POWER, MAX_DRIVE_POWER);
            double headingError = normalizeDegrees(targetHeadingDegrees - headingDegrees());
            double correction = Range.clip(headingError * HEADING_HOLD_KP,
                    -MAX_HEADING_CORRECTION, MAX_HEADING_CORRECTION);
            setTankPower(drive - correction, drive + correction);

            showOdometry("Drive", movement, targetCm);
            idle();
        }

        return failAndStop("Drive timed out");
    }

    private boolean turnLeft(double targetRadians) {
        PodSnapshot start = readPods();
        ElapsedTime timeout = new ElapsedTime();

        while (opModeIsActive() && timeout.seconds() < TURN_TIMEOUT_SECONDS) {
            PodDelta movement = movementSince(start);
            // The Duck's pod installation reports left rotation with a negative sign.
            // This maneuver only turns left, so use measured rotation magnitude.
            double turnedRadians = Math.abs(movement.headingRadians);
            double error = targetRadians - turnedRadians;
            if (error <= TURN_TOLERANCE_RADIANS) {
                stopDrive();
                return true;
            }

            double turn = Range.clip(error * TURN_KP,
                    MIN_TURN_POWER, MAX_TURN_POWER);
            setTankPower(-turn, turn);

            showOdometry("Turn left", movement, Math.toDegrees(targetRadians));
            idle();
        }

        return failAndStop("Turn timed out");
    }

    private boolean pauseBetweenMovements(String completedStep) {
        stopDrive();
        ElapsedTime pause = new ElapsedTime();
        while (opModeIsActive() && pause.seconds() < PAUSE_SECONDS) {
            telemetry.addData("Status", completedStep);
            telemetry.addData("Paused", "%.1f / %.1f seconds",
                    pause.seconds(), PAUSE_SECONDS);
            telemetry.update();
            idle();
        }
        return opModeIsActive();
    }

    private PodSnapshot readPods() {
        // These mappings and the right-side inversion match the Duck's original app.
        return new PodSnapshot(
                frontLeft.getCurrentPosition(),
                -rearRight.getCurrentPosition(),
                frontRight.getCurrentPosition());
    }

    private PodDelta movementSince(PodSnapshot start) {
        PodSnapshot now = readPods();
        double leftCm = countsToCm(now.left - start.left);
        double rightCm = countsToCm(now.right - start.right);
        double centerCm = countsToCm(now.center - start.center);
        double headingRadians = (rightCm - leftCm)
                / (2.0 * POD_PARALLEL_HALF_SPACING_CM);
        double forwardCm = (leftCm + rightCm) / 2.0;
        double lateralCm = centerCm - POD_CENTER_OFFSET_CM * headingRadians;
        return new PodDelta(forwardCm, lateralCm, headingRadians, now);
    }

    private double countsToCm(int counts) {
        return counts * POD_WHEEL_CIRCUMFERENCE_CM / POD_COUNTS_PER_REVOLUTION;
    }

    private void showOdometry(String step, PodDelta movement, double target) {
        telemetry.addData("Step", step);
        telemetry.addData("Forward / lateral", "%.1f / %.1f cm",
                movement.forwardCm, movement.lateralCm);
        telemetry.addData("Odo heading", "%.1f deg", Math.toDegrees(movement.headingRadians));
        telemetry.addData("IMU heading", "%.1f deg", headingDegrees());
        telemetry.addData("Target", "%.1f", target);
        telemetry.addData("Pods L/R/C", "%d / %d / %d",
                movement.current.left, movement.current.right, movement.current.center);
        telemetry.update();
    }

    private boolean failAndStop(String message) {
        stopDrive();
        if (opModeIsActive()) {
            telemetry.addLine(message);
            telemetry.update();
            requestOpModeStop();
        }
        return false;
    }

    private double headingDegrees() {
        return imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES);
    }

    private double normalizeDegrees(double degrees) {
        while (degrees > 180.0) {
            degrees -= 360.0;
        }
        while (degrees <= -180.0) {
            degrees += 360.0;
        }
        return degrees;
    }

    private void setDirections() {
        // Matches the motor direction flags in the Duck's original Configuration.Chassis.
        frontLeft.setDirection(DcMotorSimple.Direction.FORWARD);
        rearLeft.setDirection(DcMotorSimple.Direction.FORWARD);
        frontRight.setDirection(DcMotorSimple.Direction.REVERSE);
        rearRight.setDirection(DcMotorSimple.Direction.REVERSE);
    }

    private void setRunMode(DcMotor.RunMode mode) {
        for (DcMotor motor : driveMotors) {
            motor.setMode(mode);
        }
    }

    private void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        for (DcMotor motor : driveMotors) {
            motor.setZeroPowerBehavior(behavior);
        }
    }

    private void setTankPower(double leftPower, double rightPower) {
        frontLeft.setPower(leftPower);
        rearLeft.setPower(leftPower);
        frontRight.setPower(rightPower);
        rearRight.setPower(rightPower);
    }

    private void stopDrive() {
        for (DcMotor motor : driveMotors) {
            motor.setPower(0.0);
        }
    }

    private static final class PodSnapshot {
        final int left;
        final int right;
        final int center;

        PodSnapshot(int left, int right, int center) {
            this.left = left;
            this.right = right;
            this.center = center;
        }
    }

    private static final class PodDelta {
        final double forwardCm;
        final double lateralCm;
        final double headingRadians;
        final PodSnapshot current;

        PodDelta(double forwardCm, double lateralCm, double headingRadians,
                 PodSnapshot current) {
            this.forwardCm = forwardCm;
            this.lateralCm = lateralCm;
            this.headingRadians = headingRadians;
            this.current = current;
        }
    }
}
