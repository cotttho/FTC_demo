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
    private static final double NORMAL_SCALE = 0.60;
    private static final double SLOW_SCALE = 0.30;

    private DcMotor frontRight;
    private DcMotor frontLeft;
    private DcMotor backLeft;
    private DcMotor backRight;
    private boolean fourMotorDrive;

    @Override
    public void runOpMode() {
        frontRight = hardwareMap.get(DcMotor.class, "motor0");
        DcMotor motor1 = hardwareMap.get(DcMotor.class, "motor1");
        DcMotor motor2 = hardwareMap.tryGet(DcMotor.class, "motor2");
        DcMotor motor3 = hardwareMap.tryGet(DcMotor.class, "motor3");

        if ((motor2 == null) != (motor3 == null)) {
            throw new IllegalStateException(
                    "Drive configuration must contain motor0/motor1 or motor0..motor3");
        }

        fourMotorDrive = motor2 != null;
        if (fourMotorDrive) {
            backRight = motor1;
            backLeft = motor2;
            frontLeft = motor3;
        } else {
            frontLeft = motor1;
        }

        setDirections();
        setRunMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        log("Initialized %s arcade drive; battery=%.2fV",
                fourMotorDrive ? "4-motor (FR=0 BR=1 BL=2 FL=3)" : "2-motor (R=0 L=1)",
                batteryVoltage());
        telemetry.addData("Status", "Ready (%d motors)", fourMotorDrive ? 4 : 2);
        telemetry.addData("Controls", "Right Y: speed, Left X: turn");
        telemetry.addData("Battery", "%.2f V", batteryVoltage());
        telemetry.update();

        waitForStart();

        try {
            while (opModeIsActive()) {
                double scale = gamepad1.left_bumper ? SLOW_SCALE : NORMAL_SCALE;

                // Arcade drive: point the left stick left/right to set the robot's
                // orientation, and use the right stick up/down for speed and direction.
                double drive = -gamepad1.right_stick_y;
                double turn = gamepad1.left_stick_x;
                double frontLeftPower = drive + turn;
                double frontRightPower = drive - turn;
                double backLeftPower = drive + turn;
                double backRightPower = drive - turn;

                // Preserve the requested direction when driving and turning together.
                double leftMaxMagnitude = Math.max(1.0, Math.max(Math.abs(frontLeftPower), Math.abs(backLeftPower)));
                frontLeftPower = Range.clip((frontLeftPower / leftMaxMagnitude) * scale, -1.0, 1.0);
                backLeftPower = Range.clip((backLeftPower / leftMaxMagnitude) * scale, -1.0, 1.0);
                double rightMaxMagnitude = Math.max(1.0, Math.max(Math.abs(frontRightPower), Math.abs(backRightPower)));
                frontRightPower = Range.clip((frontRightPower / rightMaxMagnitude) * scale, -1.0, 1.0);
                backRightPower = Range.clip((backRightPower / rightMaxMagnitude) * scale, -1.0, 1.0);

                setDrivePowers(frontLeftPower, backLeftPower, frontRightPower, backRightPower);

                telemetry.addData("Mode", gamepad1.left_bumper ? "Slow" : "Normal");
                telemetry.addData("Speed", "%.2f", drive);
                telemetry.addData("Turn", "%.2f", turn);
                telemetry.addData("Front Left", "%.2f", frontLeftPower);
                telemetry.addData("Back Left", "%.2f", backLeftPower);
                telemetry.addData("Front Right", "%.2f", frontRightPower);
                telemetry.addData("Back Right", "%.2f", frontRightPower);
                telemetry.addData("Battery", "%.2f V", batteryVoltage());
                telemetry.update();

                idle();
            }
        } finally {
            stopMotors();
            log("Stopped motors");
        }
    }

    private void setDirections() {
        frontRight.setDirection(DcMotorSimple.Direction.REVERSE);
        frontLeft.setDirection(DcMotorSimple.Direction.FORWARD);
        if (fourMotorDrive) {
            backLeft.setDirection(DcMotorSimple.Direction.FORWARD);
            backRight.setDirection(DcMotorSimple.Direction.REVERSE);
        }
    }

    private void setRunMode(DcMotor.RunMode mode) {
        frontRight.setMode(mode);
        frontLeft.setMode(mode);
        if (fourMotorDrive) {
            backLeft.setMode(mode);
            backRight.setMode(mode);
        }
    }

    private void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        frontRight.setZeroPowerBehavior(behavior);
        frontLeft.setZeroPowerBehavior(behavior);
        if (fourMotorDrive) {
            backLeft.setZeroPowerBehavior(behavior);
            backRight.setZeroPowerBehavior(behavior);
        }
    }

    private void setDrivePowers(double frontLeftPower, double backLeftPower, double frontRightPower, double backRightPower) {
        frontLeft.setPower(frontLeftPower);
        frontRight.setPower(frontRightPower);
        if (fourMotorDrive) {
            backLeft.setPower(backLeftPower);
            backRight.setPower(backRightPower);
        }
    }

    private void stopMotors() {
        setDrivePowers(0.0, 0.0,  0.0,0.0);
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
