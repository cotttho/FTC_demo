package org.firstinspires.ftc.teamcode.pedro;

import static com.pedropathing.api.Paths.line;

import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

/**
 * Duck Pedro autonomous: forward 20 inches, pause 20 seconds, turn left 90 degrees,
 * pause 20 seconds, then drive forward another 20 inches.
 */
@Autonomous(name = "Duck Pedro Auto (tune first)", group = "Duck")
public class DuckPedroAuto extends OpMode {
    private static final Pose START = new Pose(0, 0, 0);
    private static final Pose FIRST_STOP = new Pose(20, 0, 0);
    private static final Pose TURNED_LEFT = new Pose(20, 0, Math.toRadians(90));
    private static final Pose FINISH = new Pose(20, 20, Math.toRadians(90));
    private static final double PAUSE_SECONDS = 20.0;

    private final ElapsedTime stateTimer = new ElapsedTime();
    private Follower follower;
    private Path firstForward;
    private Path secondForward;
    private State state = State.LOCKED;

    @Override
    public void init() {
        if (!DuckPedroConstants.PEDRO_TUNING_COMPLETE) {
            telemetry.addLine("LOCKED: Pedro tuning is not complete.");
            telemetry.addLine("Run AutoTune before allowing this OpMode to move.");
            telemetry.addLine("This protects the robot from unverified directions/gains.");
            telemetry.update();
            return;
        }

        follower = DuckPedroConstants.createFollower(hardwareMap);
        follower.setPose(START);
        firstForward = line(START, FIRST_STOP).constant(START);
        secondForward = line(TURNED_LEFT, FINISH).constant(TURNED_LEFT);
        state = State.READY;
        telemetry.addLine("Ready: 20 in, pause, left 90, pause, 20 in");
        telemetry.update();
    }

    @Override
    public void start() {
        if (state == State.READY) {
            follower.follow(firstForward);
            state = State.FIRST_FORWARD;
        }
    }

    @Override
    public void loop() {
        if (follower == null) {
            telemetry.addLine("Pedro Auto remains locked until tuning is complete.");
            telemetry.update();
            return;
        }

        follower.update();
        switch (state) {
            case FIRST_FORWARD:
                if (!follower.isBusy()) beginPause(State.PAUSE_AFTER_FIRST);
                break;
            case PAUSE_AFTER_FIRST:
                if (stateTimer.seconds() >= PAUSE_SECONDS) {
                    follower.hold(TURNED_LEFT, false);
                    state = State.TURN_LEFT;
                    stateTimer.reset();
                }
                break;
            case TURN_LEFT:
                double error = Math.abs(angleDifference(TURNED_LEFT.heading(), follower.pose().heading()));
                if (error < Math.toRadians(3) && stateTimer.seconds() > 0.5) {
                    follower.stop();
                    beginPause(State.PAUSE_AFTER_TURN);
                }
                break;
            case PAUSE_AFTER_TURN:
                if (stateTimer.seconds() >= PAUSE_SECONDS) {
                    follower.follow(secondForward);
                    state = State.SECOND_FORWARD;
                }
                break;
            case SECOND_FORWARD:
                if (!follower.isBusy()) {
                    follower.stop();
                    state = State.DONE;
                }
                break;
            default:
                break;
        }

        Pose pose = follower.pose();
        telemetry.addData("state", state);
        telemetry.addData("x", "%.2f in", pose.x());
        telemetry.addData("y", "%.2f in", pose.y());
        telemetry.addData("heading", "%.1f deg", Math.toDegrees(pose.heading()));
        telemetry.addData("pause remaining", "%.1f sec", pauseRemaining());
        telemetry.update();
    }

    @Override
    public void stop() {
        if (follower != null) follower.stop();
    }

    private void beginPause(State pauseState) {
        follower.stop();
        state = pauseState;
        stateTimer.reset();
    }

    private double pauseRemaining() {
        if (state != State.PAUSE_AFTER_FIRST && state != State.PAUSE_AFTER_TURN) return 0;
        return Math.max(0, PAUSE_SECONDS - stateTimer.seconds());
    }

    private static double angleDifference(double target, double current) {
        return Math.atan2(Math.sin(target - current), Math.cos(target - current));
    }

    private enum State {
        LOCKED, READY, FIRST_FORWARD, PAUSE_AFTER_FIRST,
        TURN_LEFT, PAUSE_AFTER_TURN, SECOND_FORWARD, DONE
    }
}
