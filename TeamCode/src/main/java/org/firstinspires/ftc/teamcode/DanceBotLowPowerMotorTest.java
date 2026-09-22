package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

/** Compares each drive motor's low-power breakaway behavior with the robot on blocks. */
@Autonomous(name = "DanceBot Low Power Motor Test", group = "Diagnostics")
public class DanceBotLowPowerMotorTest extends LinearOpMode {
    private static final double[] POWERS = {0.10, 0.15, 0.20};
    private static final double RUN_SECONDS = 1.5;
    private static final double PAUSE_SECONDS = 0.5;

    private final String[] names = {
            "front_right", "back_right", "back_left", "front_left"
    };
    private final DcMotor[] motors = new DcMotor[names.length];

    @Override
    public void runOpMode() {
        for (int i = 0; i < names.length; i++) {
            motors[i] = hardwareMap.get(DcMotor.class, names[i]);
            motors[i].setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motors[i].setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            motors[i].setPower(0.0);
        }

        telemetry.addLine("ROBOT MUST BE SECURELY ON BLOCKS");
        telemetry.addLine("Tests each wheel alone at 10%, 15%, and 20%");
        telemetry.addLine("Each power runs forward, then backward");
        telemetry.update();

        waitForStart();
        if (isStopRequested()) return;

        try {
            for (int motorIndex = 0;
                 motorIndex < motors.length && opModeIsActive(); motorIndex++) {
                for (double power : POWERS) {
                    if (!opModeIsActive()) break;
                    runStep(motorIndex, power);
                    pause();
                    runStep(motorIndex, -power);
                    pause();
                }
            }
        } finally {
            stopAll();
        }

        telemetry.addData("Status", "Complete");
        telemetry.update();
    }

    private void runStep(int motorIndex, double power) {
        stopAll();
        motors[motorIndex].setPower(power);
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < RUN_SECONDS) {
            telemetry.addData("Motor", names[motorIndex]);
            telemetry.addData("Commanded power", "%+.0f%%", power * 100.0);
            telemetry.addData("Direction", power > 0 ? "positive" : "negative");
            telemetry.addData("Time", "%.1f / %.1f sec", timer.seconds(), RUN_SECONDS);
            telemetry.addLine("Record: stopped / weak / normal");
            telemetry.update();
            idle();
        }
    }

    private void pause() {
        stopAll();
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < PAUSE_SECONDS) idle();
    }

    private void stopAll() {
        for (DcMotor motor : motors) {
            if (motor != null) motor.setPower(0.0);
        }
    }
}
