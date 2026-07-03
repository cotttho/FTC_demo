package org.firstinspires.ftc.teamcode;

import android.util.Log;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;

@Autonomous(name = "DanceBot Motor Test", group = "Demo")
public class DanceBotMotorTest extends LinearOpMode {
    private static final String TAG = "DanceBotMotorTest";
    private static final double POWER = 0.35;
    private static final double STEP_SECONDS = 2.0;
    private static final double PAUSE_SECONDS = 0.5;

    private DcMotor[] motors;
    private String[] names;

    @Override
    public void runOpMode() {
        names = new String[] {"motor0", "motor1", "motor2", "motor3"};
        motors = new DcMotor[names.length];

        for (int i = 0; i < names.length; i++) {
            motors[i] = hardwareMap.get(DcMotor.class, names[i]);
            motors[i].setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motors[i].setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        log("Ready; battery=%.2fV; testing one motor at a time at %.2f power",
                batteryVoltage(), POWER);
        telemetry.addData("Status", "Ready");
        telemetry.addData("Test", "One motor at a time");
        telemetry.addData("Battery", "%.2f V", batteryVoltage());
        telemetry.addData("Power", "%.2f", POWER);
        telemetry.update();

        waitForStart();
        log("Started motor port test");

        try {
            for (int i = 0; opModeIsActive() && i < motors.length; i++) {
                runMotor(names[i], motors[i], POWER);
                pause();
                runMotor(names[i], motors[i], -POWER);
                pause();
            }
        } finally {
            stopAll();
            log("Stopped all motors");
        }

        telemetry.addData("Status", "Complete");
        telemetry.update();
        log("Motor port test complete");
    }

    private void runMotor(String name, DcMotor motor, double power) {
        stopAll();
        motor.setPower(power);
        log("%s power %.2f; battery=%.2fV", name, power, batteryVoltage());

        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < STEP_SECONDS) {
            telemetry.addData("Motor", name);
            telemetry.addData("Power", "%.2f", power);
            telemetry.addData("Battery", "%.2f V", batteryVoltage());
            telemetry.update();
            idle();
        }
    }

    private void pause() {
        stopAll();
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < PAUSE_SECONDS) {
            telemetry.addData("Motor", "pause");
            telemetry.addData("Battery", "%.2f V", batteryVoltage());
            telemetry.update();
            idle();
        }
    }

    private void stopAll() {
        if (motors == null) {
            return;
        }

        for (DcMotor motor : motors) {
            if (motor != null) {
                motor.setPower(0.0);
            }
        }
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
