package org.firstinspires.ftc.teamcode;

import android.util.Log;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;

@Autonomous(name = "DanceBot Auto", group = "Demo")
public class DanceBotAuto extends LinearOpMode {
    private static final String TAG = "DanceBotAuto";
    private static final double POWER = 0.35;
    private static final double STEP_SECONDS = 2.0;

    private DcMotor frontRight;
    private DcMotor frontLeft;
    private DcMotor backLeft;
    private DcMotor backRight;

    @Override
    public void runOpMode() {
        frontRight = hardwareMap.get(DcMotor.class, "motor0");
        frontLeft = hardwareMap.get(DcMotor.class, "motor1");
        backLeft = hardwareMap.get(DcMotor.class, "motor2");
        backRight = hardwareMap.get(DcMotor.class, "motor3");

        setDirections();
        setRunMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        log("Initialized drive map FR=motor0 FL=motor1 BL=motor2 BR=motor3; battery=%.2fV; waiting for start",
                batteryVoltage());
        telemetry.addData("Status", "Ready");
        telemetry.addData("Battery", "%.2f V", batteryVoltage());
        telemetry.addData("Power", "%.2f", POWER);
        telemetry.update();

        waitForStart();
        log("Started dance sequence");

        try {
            if (opModeIsActive()) {
                runArcadeStep( POWER,  0.0);
                runArcadeStep(-POWER,  0.0);
                runArcadeStep( 0.0,    POWER);
                runArcadeStep( 0.0,   -POWER);
                runArcadeStep( POWER,  POWER);
                runArcadeStep(-POWER, -POWER);
                runArcadeStep( 0.0,    POWER);
                runArcadeStep( 0.0,   -POWER);
                runArcadeStep( POWER, -POWER);
                runArcadeStep(-POWER,  POWER);
            }
        } finally {
            stopMotors();
            log("Stopped motors");
        }

        telemetry.addData("Status", "Complete");
        telemetry.update();
        log("Dance sequence complete");
    }

    /** Uses the same arcade-drive convention as DanceBotTeleOp. */
    private void runArcadeStep(double drive, double turn) {
        if (!opModeIsActive()) {
            return;
        }

        double leftPower = drive + turn;
        double rightPower = drive - turn;
        double maxMagnitude = Math.max(1.0, Math.max(Math.abs(leftPower), Math.abs(rightPower)));
        leftPower /= maxMagnitude;
        rightPower /= maxMagnitude;

        setDrivePowers(leftPower, rightPower);
        log("Arcade step drive %.2f turn %.2f; left %.2f right %.2f; battery=%.2fV",
                drive, turn, leftPower, rightPower, batteryVoltage());
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < STEP_SECONDS) {
            telemetry.addData("Dance", "%.1f / %.1f", timer.seconds(), STEP_SECONDS);
            telemetry.addData("Drive / Turn", "%.2f / %.2f", drive, turn);
            telemetry.addData("Left / Right", "%.2f / %.2f", leftPower, rightPower);
            telemetry.addData("Battery", "%.2f V", batteryVoltage());
            telemetry.update();
            idle();
        }
    }

    private void setDirections() {
        frontRight.setDirection(DcMotorSimple.Direction.REVERSE);
        frontLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        backLeft.setDirection(DcMotorSimple.Direction.FORWARD);
        backRight.setDirection(DcMotorSimple.Direction.FORWARD);
    }

    private void setRunMode(DcMotor.RunMode mode) {
        frontRight.setMode(mode);
        frontLeft.setMode(mode);
        backLeft.setMode(mode);
        backRight.setMode(mode);
    }

    private void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        frontRight.setZeroPowerBehavior(behavior);
        frontLeft.setZeroPowerBehavior(behavior);
        backLeft.setZeroPowerBehavior(behavior);
        backRight.setZeroPowerBehavior(behavior);
    }

    private void setWheelPowers(double frontRightPower, double frontLeftPower,
                                double backLeftPower, double backRightPower) {
        frontRight.setPower(frontRightPower);
        frontLeft.setPower(frontLeftPower);
        backLeft.setPower(backLeftPower);
        backRight.setPower(backRightPower);
    }

    private void setDrivePowers(double leftPower, double rightPower) {
        setWheelPowers(rightPower, leftPower, leftPower, rightPower);
    }

    private void stopMotors() {
        setWheelPowers(0.0, 0.0, 0.0, 0.0);
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
