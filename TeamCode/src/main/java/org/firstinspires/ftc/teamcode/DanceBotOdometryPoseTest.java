package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;

/** Read-only three-wheel odometry validation using the calibrated DanceBot pods. */
@TeleOp(name = "DanceBot Odometry Pose Test", group = "Diagnostics")
public class DanceBotOdometryPoseTest extends LinearOpMode {
    private static final double PARALLEL_TICKS_PER_INCH = 1757.55;
    private static final double CENTER_TICKS_PER_INCH = 1768.34;
    private static final double LEFT_POD_Y_INCHES = 4.14;
    private static final double RIGHT_POD_Y_INCHES = -4.14;
    private static final double CENTER_POD_X_INCHES = 0.0;

    private DcMotor leftPod;
    private DcMotor rightPod;
    private DcMotor centerPod;

    private int previousLeft;
    private int previousRight;
    private int previousCenter;

    private double x;
    private double y;
    private double heading;

    @Override
    public void runOpMode() {
        leftPod = hardwareMap.get(DcMotor.class, "back_left");       // CH encoder port 2
        rightPod = hardwareMap.get(DcMotor.class, "front_right");   // CH encoder port 0
        centerPod = hardwareMap.get(DcMotor.class, "back_right");   // CH encoder port 1

        prepareEncoderPort(leftPod);
        prepareEncoderPort(rightPod);
        prepareEncoderPort(centerPod);
        resetPose();

        telemetry.setMsTransmissionInterval(50);
        telemetry.addLine("READ ONLY: motors will not be powered");
        telemetry.addLine("A = reset pose to X=0, Y=0, heading=0");
        telemetry.update();

        waitForStart();
        if (isStopRequested()) {
            return;
        }

        resetPose();
        boolean previousA = false;

        try {
            while (opModeIsActive()) {
                boolean resetPressed = gamepad1.a;
                if (resetPressed && !previousA) {
                    resetPose();
                }
                previousA = resetPressed;

                updatePose();

                telemetry.addData("X (forward)", "%+.2f in", x);
                telemetry.addData("Y (left)", "%+.2f in", y);
                telemetry.addData("Heading (CCW+)", "%+.1f deg", Math.toDegrees(heading));
                telemetry.addData("Pods L/R/C", "%d / %d / %d",
                        readLeft(), readRight(), readCenter());
                telemetry.addLine("Expected: forward => +X; left => +Y; CCW => +heading");
                telemetry.update();
                idle();
            }
        } finally {
            leftPod.setPower(0.0);
            rightPod.setPower(0.0);
            centerPod.setPower(0.0);
        }
    }

    private void prepareEncoderPort(DcMotor motor) {
        motor.setPower(0.0);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
    }

    private void updatePose() {
        int currentLeft = readLeft();
        int currentRight = readRight();
        int currentCenter = readCenter();

        double deltaLeft = (currentLeft - previousLeft) / PARALLEL_TICKS_PER_INCH;
        double deltaRight = (currentRight - previousRight) / PARALLEL_TICKS_PER_INCH;
        double deltaCenter = (currentCenter - previousCenter) / CENTER_TICKS_PER_INCH;

        previousLeft = currentLeft;
        previousRight = currentRight;
        previousCenter = currentCenter;

        double deltaHeading = (deltaRight - deltaLeft)
                / (LEFT_POD_Y_INCHES - RIGHT_POD_Y_INCHES);
        double forward = (deltaLeft + deltaRight) / 2.0;
        double lateral = deltaCenter - CENTER_POD_X_INCHES * deltaHeading;
        double midpointHeading = heading + deltaHeading / 2.0;

        x += forward * Math.cos(midpointHeading) - lateral * Math.sin(midpointHeading);
        y += forward * Math.sin(midpointHeading) + lateral * Math.cos(midpointHeading);
        heading = normalizeRadians(heading + deltaHeading);
    }

    private void resetPose() {
        previousLeft = readLeft();
        previousRight = readRight();
        previousCenter = readCenter();
        x = 0.0;
        y = 0.0;
        heading = 0.0;
    }

    private int readLeft() {
        return leftPod.getCurrentPosition();
    }

    private int readRight() {
        return -rightPod.getCurrentPosition();
    }

    private int readCenter() {
        return centerPod.getCurrentPosition();
    }

    private double normalizeRadians(double angle) {
        while (angle > Math.PI) {
            angle -= 2.0 * Math.PI;
        }
        while (angle <= -Math.PI) {
            angle += 2.0 * Math.PI;
        }
        return angle;
    }
}
