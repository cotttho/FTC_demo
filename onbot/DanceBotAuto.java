package org.firstinspires.ftc.teamcode;

import android.util.Log;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;

@Autonomous(name = "DanceBot Auto", group = "Demo")
public class DanceBotAuto extends LinearOpMode {
    private static final String TAG = "DanceBotAuto";
    private static final double POWER = 0.35;
    private static final double STEP_SECONDS = 2.0;

    private DcMotor frontRight;
    private DcMotor backRight;
    private DcMotor backLeft;
    private DcMotor frontLeft;

    @Override
    public void runOpMode() {
        frontRight = hardwareMap.get(DcMotor.class, "front_right");
        backRight = hardwareMap.get(DcMotor.class, "back_right");
        backLeft = hardwareMap.get(DcMotor.class, "back_left");
        frontLeft = hardwareMap.get(DcMotor.class, "front_left");

        setRunMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        log("Initialized named drive motors; battery=%.2fV; waiting for start", batteryVoltage());
        telemetry.addData("Status", "Ready");
        telemetry.addData("Battery", "%.2f V", batteryVoltage());
        telemetry.addData("Power", "%.2f", POWER);
        telemetry.update();

        waitForStart();
        log("Started dance sequence");

        try {
            if (opModeIsActive()) {
                runStep( POWER,  POWER,  POWER,  POWER);
                runStep(-POWER, -POWER, -POWER, -POWER);
                runStep( POWER, -POWER,  POWER, -POWER);
                runStep(-POWER,  POWER, -POWER,  POWER);
                runStep( POWER,  0.0,  -POWER, 0.0);
                runStep( 0.0,   -POWER, 0.0,   POWER);
                runStep( POWER, -POWER, -POWER, POWER);
                runStep(-POWER,  POWER,  POWER, -POWER);
                runStep( POWER,  0.0,   POWER, 0.0);
                runStep( 0.0,   -POWER, 0.0,  -POWER);
            }
        } finally {
            stopMotors();
            log("Stopped motors");
        }

        telemetry.addData("Status", "Complete");
        telemetry.update();
        log("Dance sequence complete");
    }

    private void runStep(double p0, double p1, double p2, double p3) {
        if (!opModeIsActive()) {
            return;
        }

        setPowers(p0, p1, p2, p3);
        log("Step powers: %.2f %.2f %.2f %.2f; battery=%.2fV", p0, p1, p2, p3, batteryVoltage());
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < STEP_SECONDS) {
            telemetry.addData("Dance", "%.1f / %.1f", timer.seconds(), STEP_SECONDS);
            telemetry.addData("Powers", "%.2f %.2f %.2f %.2f", p0, p1, p2, p3);
            telemetry.addData("Battery", "%.2f V", batteryVoltage());
            telemetry.update();
            idle();
        }
    }

    private void setRunMode(DcMotor.RunMode mode) {
        frontRight.setMode(mode);
        backRight.setMode(mode);
        backLeft.setMode(mode);
        frontLeft.setMode(mode);
    }

    private void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        frontRight.setZeroPowerBehavior(behavior);
        backRight.setZeroPowerBehavior(behavior);
        backLeft.setZeroPowerBehavior(behavior);
        frontLeft.setZeroPowerBehavior(behavior);
    }

    private void setPowers(double p0, double p1, double p2, double p3) {
        frontRight.setPower(p0);
        backRight.setPower(p1);
        backLeft.setPower(p2);
        frontLeft.setPower(p3);
    }

    private void stopMotors() {
        setPowers(0.0, 0.0, 0.0, 0.0);
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
