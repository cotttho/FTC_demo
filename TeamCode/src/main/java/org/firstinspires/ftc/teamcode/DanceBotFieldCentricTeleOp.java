package org.firstinspires.ftc.teamcode;

import android.util.Log;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.Range;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

@TeleOp(name = "DanceBot Field Centric", group = "Demo")
public class DanceBotFieldCentricTeleOp extends LinearOpMode {
    private static final String TAG = "DanceBotFieldCentric";
    private static final double NORMAL_SCALE = 1.00;
    private static final double SLOW_SCALE = 0.30;
    private static final double STICK_DEAD_ZONE = 0.08;

    // Change these two values if the Control Hub is mounted in another orientation.
    private static final RevHubOrientationOnRobot.LogoFacingDirection HUB_LOGO_DIRECTION =
            RevHubOrientationOnRobot.LogoFacingDirection.UP;
    private static final RevHubOrientationOnRobot.UsbFacingDirection HUB_USB_DIRECTION =
            RevHubOrientationOnRobot.UsbFacingDirection.FORWARD;

    private DcMotor frontRight;
    private DcMotor frontLeft;
    private DcMotor rearLeft;
    private DcMotor rearRight;
    private DcMotor intake;
    private IMU imu;

    @Override
    public void runOpMode() {
        frontRight = hardwareMap.get(DcMotor.class, "front_right");
        rearRight = hardwareMap.get(DcMotor.class, "rear_right");
        rearLeft = hardwareMap.get(DcMotor.class, "rear_left");
        frontLeft = hardwareMap.get(DcMotor.class, "front_left");
        intake = hardwareMap.tryGet(DcMotor.class, "intake");
        imu = hardwareMap.get(IMU.class, "imu");

        setDirections();
        setRunMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        if (intake != null) {
            intake.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            intake.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            intake.setPower(0.0);
        }
        stopMotors();

        RevHubOrientationOnRobot hubOrientation =
                new RevHubOrientationOnRobot(HUB_LOGO_DIRECTION, HUB_USB_DIRECTION);
        imu.initialize(new IMU.Parameters(hubOrientation));

        log("Initialized 4-motor field-centric drive; battery=%.2fV", batteryVoltage());
        telemetry.addData("Status", "Ready (4 motors + IMU)");
        telemetry.addData("Drive", "LS: field drive/strafe; RS X: turn; RB: slow");
        telemetry.addData("Heading", "START / OPTIONS / Y: reset field forward");
        telemetry.addData("Operator", "Gamepad 2 RT: intake; LT: reverse; B: intake stop");
        telemetry.addData("Battery", "%.2f V", batteryVoltage());
        telemetry.update();

        waitForStart();
        if (isStopRequested()) {
            return;
        }

        // Treat the direction the robot faces at the start of TeleOp as field forward.
        imu.resetYaw();
        boolean previousResetButton = false;
        double resetConfirmationUntil = 0.0;

        try {
            while (opModeIsActive()) {
                boolean resetButton = gamepad1.start || gamepad1.options || gamepad1.y;
                if (resetButton && !previousResetButton) {
                    imu.resetYaw();
                    resetConfirmationUntil = getRuntime() + 1.0;
                    log("Field heading reset from gamepad 1");
                }
                previousResetButton = resetButton;

                double scale = gamepad1.right_bumper ? SLOW_SCALE : NORMAL_SCALE;
                double fieldForward = applyDeadZone(-gamepad1.left_stick_y);
                double fieldStrafe = applyDeadZone(gamepad1.left_stick_x);
                double turn = applyDeadZone(gamepad1.right_stick_x);
                double yawRadians = imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);

                // Rotate the field-relative stick vector into the robot's coordinate frame.
                double cosYaw = Math.cos(yawRadians);
                double sinYaw = Math.sin(yawRadians);
                double robotForward = fieldForward * cosYaw - fieldStrafe * sinYaw;
                double robotStrafe = fieldForward * sinYaw + fieldStrafe * cosYaw;

                double intakePower = 0.0;
                if (intake != null && !gamepad2.b) {
                    intakePower = applyDeadZone(gamepad2.right_trigger)
                            - applyDeadZone(gamepad2.left_trigger);
                }

                double frontLeftPower = robotForward + robotStrafe + turn;
                double frontRightPower = robotForward - robotStrafe - turn;
                double rearLeftPower = robotForward - robotStrafe + turn;
                double rearRightPower = robotForward + robotStrafe - turn;

                double maxMagnitude = Math.max(1.0,
                        Math.max(Math.max(Math.abs(frontLeftPower), Math.abs(frontRightPower)),
                                Math.max(Math.abs(rearLeftPower), Math.abs(rearRightPower))));
                frontLeftPower = Range.clip((frontLeftPower / maxMagnitude) * scale, -1.0, 1.0);
                frontRightPower = Range.clip((frontRightPower / maxMagnitude) * scale, -1.0, 1.0);
                rearLeftPower = Range.clip((rearLeftPower / maxMagnitude) * scale, -1.0, 1.0);
                rearRightPower = Range.clip((rearRightPower / maxMagnitude) * scale, -1.0, 1.0);

                setDrivePowers(frontLeftPower, rearLeftPower, frontRightPower, rearRightPower);
                if (intake != null) {
                    intake.setPower(intakePower);
                }

                telemetry.addData("Drive Mode", "Field Centric");
                telemetry.addData("Speed Mode", gamepad1.right_bumper ? "Slow" : "Normal");
                telemetry.addData("Heading", "%.1f deg", Math.toDegrees(yawRadians));
                telemetry.addData("Field Forward / Strafe", "%.2f / %.2f", fieldForward, fieldStrafe);
                telemetry.addData("Robot Forward / Strafe", "%.2f / %.2f", robotForward, robotStrafe);
                telemetry.addData("Turn", "%.2f", turn);
                telemetry.addData("Front Left / Right", "%.2f / %.2f", frontLeftPower, frontRightPower);
                telemetry.addData("Rear Left / Right", "%.2f / %.2f", rearLeftPower, rearRightPower);
                telemetry.addData("Intake", intake == null ? "Not configured" : String.format("%.2f", intakePower));
                telemetry.addData("Battery", "%.2f V", batteryVoltage());
                if (getRuntime() < resetConfirmationUntil) {
                    telemetry.addLine("*** HEADING RESET: current direction is field forward ***");
                } else {
                    telemetry.addLine("Press START / OPTIONS / Y to reset field forward");
                }
                telemetry.update();

                idle();
            }
        } finally {
            stopMotors();
            if (intake != null) {
                intake.setPower(0.0);
            }
            log("Stopped motors");
        }
    }

    private void setDirections() {
        frontRight.setDirection(DcMotorSimple.Direction.REVERSE);
        frontLeft.setDirection(DcMotorSimple.Direction.FORWARD);
        rearLeft.setDirection(DcMotorSimple.Direction.FORWARD);
        rearRight.setDirection(DcMotorSimple.Direction.REVERSE);
    }

    private void setRunMode(DcMotor.RunMode mode) {
        frontRight.setMode(mode);
        frontLeft.setMode(mode);
        rearLeft.setMode(mode);
        rearRight.setMode(mode);
    }

    private void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        frontRight.setZeroPowerBehavior(behavior);
        frontLeft.setZeroPowerBehavior(behavior);
        rearLeft.setZeroPowerBehavior(behavior);
        rearRight.setZeroPowerBehavior(behavior);
    }

    private double applyDeadZone(double input) {
        double magnitude = Math.abs(input);
        if (magnitude <= STICK_DEAD_ZONE) {
            return 0.0;
        }
        return Math.copySign((magnitude - STICK_DEAD_ZONE) / (1.0 - STICK_DEAD_ZONE), input);
    }

    private void setDrivePowers(double frontLeftPower, double rearLeftPower,
                                double frontRightPower, double rearRightPower) {
        frontLeft.setPower(frontLeftPower);
        rearLeft.setPower(rearLeftPower);
        frontRight.setPower(frontRightPower);
        rearRight.setPower(rearRightPower);
    }

    private void stopMotors() {
        setDrivePowers(0.0, 0.0, 0.0, 0.0);
    }

    private double batteryVoltage() {
        double result = Double.POSITIVE_INFINITY;
        for (VoltageSensor sensor : hardwareMap.voltageSensor) {
            double voltage = sensor.getVoltage();
            if (voltage > 0.0) {
                result = Math.min(result, voltage);
            }
        }
        return result == Double.POSITIVE_INFINITY ? 0.0 : result;
    }

    private void log(String format, Object... args) {
        String message = String.format(format, args);
        Log.i(TAG, message);
        RobotLog.ii(TAG, message);
    }
}
