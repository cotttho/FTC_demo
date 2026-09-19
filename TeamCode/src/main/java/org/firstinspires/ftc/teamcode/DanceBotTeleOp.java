package org.firstinspires.ftc.teamcode;

import android.util.Log;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.Range;
import com.qualcomm.robotcore.util.RobotLog;

@TeleOp(name = "DanceBot TeleOp", group = "Demo")
public class DanceBotTeleOp extends LinearOpMode {
    private static final String TAG = "DanceBotTeleOp";
    private static final double NORMAL_SCALE = 1.00;
    private static final double SLOW_SCALE = 0.30;
    private static final double STICK_DEAD_ZONE = 0.08;

    private DcMotor frontRight;
    private DcMotor frontLeft;
    private DcMotor rearLeft;
    private DcMotor rearRight;
    private DcMotor intake;
    private boolean fourMotorDrive;

    @Override
    public void runOpMode() {
        frontRight = hardwareMap.get(DcMotor.class, "front_right");
        DcMotor configuredRearRight = hardwareMap.get(DcMotor.class, "rear_right");
        DcMotor configuredRearLeft = hardwareMap.tryGet(DcMotor.class, "rear_left");
        DcMotor configuredFrontLeft = hardwareMap.tryGet(DcMotor.class, "front_left");
        intake = hardwareMap.tryGet(DcMotor.class, "intake");

        if ((configuredRearLeft == null) != (configuredFrontLeft == null)) {
            throw new IllegalStateException(
                    "Drive configuration must contain front_right/rear_right or all four named drive motors");
        }

        fourMotorDrive = configuredRearLeft != null;
        if (fourMotorDrive) {
            rearRight = configuredRearRight;
            rearLeft = configuredRearLeft;
            frontLeft = configuredFrontLeft;
        } else {
            frontLeft = configuredRearRight;
        }

        setDirections();
        setRunMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        if (intake != null) {
            intake.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            intake.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            intake.setPower(0.0);
        }
        stopMotors();

        log("Initialized %s robot-centric mecanum drive; battery=%.2fV",
                fourMotorDrive ? "4-motor (FR=0 BR=1 BL=2 FL=3)" : "2-motor (R=0 L=1)",
                batteryVoltage());
        telemetry.addData("Status", "Ready (%d motors)", fourMotorDrive ? 4 : 2);
        telemetry.addData("Drive", "LS: drive/strafe; RS X: turn; RB: slow");
        telemetry.addData("Operator", "Gamepad 2 RT: intake; LT: reverse; B: intake stop");
        telemetry.addData("Battery", "%.2f V", batteryVoltage());
        telemetry.update();

        waitForStart();

        try {
            while (opModeIsActive()) {
                double scale = gamepad1.right_bumper ? SLOW_SCALE : NORMAL_SCALE;

                // Recommended robot-centric mecanum map: left stick translates,
                // while right-stick X rotates the robot.
                double drive = applyDeadZone(-gamepad1.left_stick_y);
                double strafe = fourMotorDrive ? applyDeadZone(gamepad1.left_stick_x) : 0.0;
                double turn = applyDeadZone(gamepad1.right_stick_x);

                double intakePower = 0.0;
                if (intake != null && !gamepad2.b) {
                    intakePower = applyDeadZone(gamepad2.right_trigger)
                            - applyDeadZone(gamepad2.left_trigger);
                }

                double frontLeftPower = drive + strafe + turn;
                double frontRightPower = drive - strafe - turn;
                double rearLeftPower = drive - strafe + turn;
                double rearRightPower = drive + strafe - turn;

                // Normalize all four wheels together so the requested direction is preserved.
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

                telemetry.addData("Mode", gamepad1.right_bumper ? "Slow" : "Normal");
                telemetry.addData("Speed", "%.2f", drive);
                telemetry.addData("Turn", "%.2f", turn);
                telemetry.addData("Strafe", "%.2f", strafe);
                telemetry.addData("Front Left", "%.2f", frontLeftPower);
                telemetry.addData("Front Right", "%.2f", frontRightPower);
                telemetry.addData("Rear Left", "%.2f", rearLeftPower);
                telemetry.addData("Rear Right", "%.2f", rearRightPower);
                telemetry.addData("Intake", intake == null ? "Not configured" : String.format("%.2f", intakePower));
                telemetry.addData("Battery", "%.2f V", batteryVoltage());
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
        if (fourMotorDrive) {
            rearLeft.setDirection(DcMotorSimple.Direction.FORWARD);
            rearRight.setDirection(DcMotorSimple.Direction.REVERSE);
        }
    }

    private void setRunMode(DcMotor.RunMode mode) {
        frontRight.setMode(mode);
        frontLeft.setMode(mode);
        if (fourMotorDrive) {
            rearLeft.setMode(mode);
            rearRight.setMode(mode);
        }
    }

    private void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        frontRight.setZeroPowerBehavior(behavior);
        frontLeft.setZeroPowerBehavior(behavior);
        if (fourMotorDrive) {
            rearLeft.setZeroPowerBehavior(behavior);
            rearRight.setZeroPowerBehavior(behavior);
        }
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
        frontRight.setPower(frontRightPower);
        if (fourMotorDrive) {
            rearLeft.setPower(rearLeftPower);
            rearRight.setPower(rearRightPower);
        }
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
