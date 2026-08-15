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
    private DcMotor backLeft;
    private DcMotor backRight;
    private DcMotor motor4;
    private boolean fourMotorDrive;

    @Override
    public void runOpMode() {
        frontRight = hardwareMap.get(DcMotor.class, "motor0");
        DcMotor motor1 = hardwareMap.get(DcMotor.class, "motor1");
        DcMotor motor2 = hardwareMap.tryGet(DcMotor.class, "motor2");
        DcMotor motor3 = hardwareMap.tryGet(DcMotor.class, "motor3");
        motor4 = hardwareMap.tryGet(DcMotor.class, "motor4");

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
        if (motor4 != null) {
            motor4.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motor4.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            motor4.setPower(0.0);
        }
        stopMotors();

        log("Initialized %s arcade drive; battery=%.2fV",
                fourMotorDrive ? "4-motor (FR=0 BR=1 BL=2 FL=3)" : "2-motor (R=0 L=1)",
                batteryVoltage());
        telemetry.addData("Status", "Ready (%d motors)", fourMotorDrive ? 4 : 2);
        telemetry.addData("Controls", "Left stick: drive/turn; RB: motor4");
        telemetry.addData("Battery", "%.2f V", batteryVoltage());
        telemetry.update();

        waitForStart();

        try {
            while (opModeIsActive()) {
                double scale = gamepad1.left_bumper ? SLOW_SCALE : NORMAL_SCALE;

                // Arcade drive: use the left stick for forward/reverse and turning.
                // The right stick remains available for strafing in four-motor mode.
                double drive = applyDeadZone(-gamepad1.left_stick_y);
                double turn = applyDeadZone(gamepad1.left_stick_x);
                double strafe = fourMotorDrive ? applyDeadZone(gamepad1.right_stick_x) : 0.0;
                double motor4Power = motor4 != null && gamepad1.right_bumper ? scale : 0.0;

                double frontLeftPower = drive + strafe + turn;
                double frontRightPower = drive - strafe - turn;
                double backLeftPower = drive - strafe + turn;
                double backRightPower = drive + strafe - turn;

                // Normalize all four wheels together so the requested direction is preserved.
                double maxMagnitude = Math.max(1.0,
                        Math.max(Math.max(Math.abs(frontLeftPower), Math.abs(frontRightPower)),
                                Math.max(Math.abs(backLeftPower), Math.abs(backRightPower))));
                frontLeftPower = Range.clip((frontLeftPower / maxMagnitude) * scale, -1.0, 1.0);
                frontRightPower = Range.clip((frontRightPower / maxMagnitude) * scale, -1.0, 1.0);
                backLeftPower = Range.clip((backLeftPower / maxMagnitude) * scale, -1.0, 1.0);
                backRightPower = Range.clip((backRightPower / maxMagnitude) * scale, -1.0, 1.0);

                setDrivePowers(frontLeftPower, backLeftPower, frontRightPower, backRightPower);
                if (motor4 != null) {
                    motor4.setPower(motor4Power);
                }

                telemetry.addData("Mode", gamepad1.left_bumper ? "Slow" : "Normal");
                telemetry.addData("Speed", "%.2f", drive);
                telemetry.addData("Turn", "%.2f", turn);
                telemetry.addData("Strafe", "%.2f", strafe);
                telemetry.addData("Front Left", "%.2f", frontLeftPower);
                telemetry.addData("Front Right", "%.2f", frontRightPower);
                telemetry.addData("Back Left", "%.2f", backLeftPower);
                telemetry.addData("Back Right", "%.2f", backRightPower);
                telemetry.addData("Motor 4", motor4 == null ? "Not configured" : String.format("%.2f", motor4Power));
                telemetry.addData("Battery", "%.2f V", batteryVoltage());
                telemetry.update();

                idle();
            }
        } finally {
            stopMotors();
            if (motor4 != null) {
                motor4.setPower(0.0);
            }
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

    private double applyDeadZone(double input) {
        double magnitude = Math.abs(input);
        if (magnitude <= STICK_DEAD_ZONE) {
            return 0.0;
        }
        return Math.copySign((magnitude - STICK_DEAD_ZONE) / (1.0 - STICK_DEAD_ZONE), input);
    }

    private void setDrivePowers(double frontLeftPower, double backLeftPower,
                                double frontRightPower, double backRightPower) {
        frontLeft.setPower(frontLeftPower);
        frontRight.setPower(frontRightPower);
        if (fourMotorDrive) {
            backLeft.setPower(backLeftPower);
            backRight.setPower(backRightPower);
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
