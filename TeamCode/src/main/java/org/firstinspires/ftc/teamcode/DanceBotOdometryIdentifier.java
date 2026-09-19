package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;

/**
 * Read-only diagnostic for the three REV odometry pods connected to the
 * Control Hub motor encoder inputs. This OpMode never applies motor power.
 */
@TeleOp(name = "DanceBot Odometry Identifier", group = "Diagnostics")
public class DanceBotOdometryIdentifier extends LinearOpMode {
    private static final String[] PORT_LABELS = {
            "Port 0 / front_right",
            "Port 1 / back_right",
            "Port 2 / back_left",
            "Port 3 / front_left"
    };

    private static final String[] MOTOR_NAMES = {
            "front_right",
            "back_right",
            "back_left",
            "front_left"
    };

    @Override
    public void runOpMode() {
        DcMotor[] encoderPorts = new DcMotor[MOTOR_NAMES.length];
        int[] baseline = new int[MOTOR_NAMES.length];

        for (int i = 0; i < MOTOR_NAMES.length; i++) {
            encoderPorts[i] = hardwareMap.get(DcMotor.class, MOTOR_NAMES[i]);
            encoderPorts[i].setPower(0.0);
            encoderPorts[i].setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            baseline[i] = encoderPorts[i].getCurrentPosition();
        }

        telemetry.setMsTransmissionInterval(50);
        telemetry.addLine("READ ONLY: motors will not be powered");
        telemetry.addLine("Spin one pod by hand and watch which delta changes.");
        telemetry.addLine("Press A after each test to reset deltas.");
        telemetry.update();

        waitForStart();
        if (isStopRequested()) {
            return;
        }

        captureBaseline(encoderPorts, baseline);
        boolean previousA = false;

        try {
            while (opModeIsActive()) {
                boolean resetPressed = gamepad1.a;
                if (resetPressed && !previousA) {
                    captureBaseline(encoderPorts, baseline);
                }
                previousA = resetPressed;

                telemetry.addLine("Spin one physical pod; the matching delta will change.");
                telemetry.addLine("A = zero deltas   STOP = exit");
                for (int i = 0; i < encoderPorts.length; i++) {
                    int raw = encoderPorts[i].getCurrentPosition();
                    telemetry.addData(PORT_LABELS[i], "raw %d   delta %+d",
                            raw, raw - baseline[i]);
                }
                telemetry.update();
                idle();
            }
        } finally {
            for (DcMotor encoderPort : encoderPorts) {
                encoderPort.setPower(0.0);
            }
        }
    }

    private void captureBaseline(DcMotor[] encoderPorts, int[] baseline) {
        for (int i = 0; i < encoderPorts.length; i++) {
            baseline[i] = encoderPorts[i].getCurrentPosition();
        }
    }
}
