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

    private DcMotor motor0;
    private DcMotor motor1;
    private DcMotor motor2;
    private DcMotor motor3;

    @Override
    public void runOpMode() {
        motor0 = hardwareMap.get(DcMotor.class, "motor0");
        motor1 = hardwareMap.get(DcMotor.class, "motor1");
        motor2 = hardwareMap.get(DcMotor.class, "motor2");
        motor3 = hardwareMap.get(DcMotor.class, "motor3");

        setRunMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        log("Initialized motors motor0..motor3; battery=%.2fV; waiting for start", batteryVoltage());
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
        motor0.setMode(mode);
        motor1.setMode(mode);
        motor2.setMode(mode);
        motor3.setMode(mode);
    }

    private void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        motor0.setZeroPowerBehavior(behavior);
        motor1.setZeroPowerBehavior(behavior);
        motor2.setZeroPowerBehavior(behavior);
        motor3.setZeroPowerBehavior(behavior);
    }

    private void setPowers(double p0, double p1, double p2, double p3) {
        motor0.setPower(p0);
        motor1.setPower(p1);
        motor2.setPower(p2);
        motor3.setPower(p3);
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
