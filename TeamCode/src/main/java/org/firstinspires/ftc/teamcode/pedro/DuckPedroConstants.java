package org.firstinspires.ftc.teamcode.pedro;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Vector2D;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.pedropathing.revhub.localizers.Encoder;
import com.pedropathing.revhub.localizers.ThreeWheelConfig;
import com.pedropathing.revhub.localizers.ThreeWheelLocalizer;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

/** Pedro Pathing configuration for the Automa Duck robot. */
public final class DuckPedroConstants {
    private DuckPedroConstants() {
    }

    // Change to true only after all three AutoTune stages have been applied and tested.
    public static final boolean PEDRO_TUNING_COMPLETE = false;

    public static final MecanumConfig DRIVETRAIN = new MecanumConfig(c -> {
        c.frontLeftName.set("front_left");
        c.backLeftName.set("rear_left");
        c.frontRightName.set("front_right");
        c.backRightName.set("rear_right");
        c.frontLeftDirection.set(DcMotorSimple.Direction.FORWARD);
        c.backLeftDirection.set(DcMotorSimple.Direction.FORWARD);
        c.frontRightDirection.set(DcMotorSimple.Direction.REVERSE);
        c.backRightDirection.set(DcMotorSimple.Direction.REVERSE);
        c.manualBrakeMode.set(true);
    });

    // Initial values recovered from the Duck's original robot app. AutoTune should
    // replace these values because wheel wear and exact pod placement affect them.
    public static final ThreeWheelConfig LOCALIZER = new ThreeWheelConfig(c -> {
        c.leftEncoderName.set("front_left");
        c.rightEncoderName.set("rear_right");
        c.strafeEncoderName.set("front_right");
        c.leftPodY.set(6.6555118);
        c.rightPodY.set(-6.6555118);
        c.strafePodX.set(-2.9527559);
        c.forwardTicksToInches.set(0.001979);
        c.strafeTicksToInches.set(0.001979);
        c.turnTicksToRadians.set(0.001979);
        c.leftEncoderDirection.set(Encoder.FORWARD);
        c.rightEncoderDirection.set(Encoder.REVERSE);
        c.strafeEncoderDirection.set(Encoder.FORWARD);
    });

    // Safe compilation defaults only. Replace the entire block with the Java output
    // from Pedro's Foresight AutoTuner before enabling the autonomous.
    public static final ForesightConfig FORESIGHT = new ForesightConfig(c -> {
        c.forwardTranslational.set(Controller.proportional(0.08));
        c.strafeTranslational.set(Controller.proportional(0.08));
        c.headingFeedback.set(Controller.proportional(1.0));
        c.coast.set(Controller.proportionalFeedforward(0.02));
        c.brake.set(Controller.proportionalFeedforward(0.02));
        c.linearBrakeCoefficients.set(Matrix.diag(0.02, 0.02));
        c.quadraticBrakeCoefficients.set(Matrix.diag(0.001, 0.001));
        c.headingBrakeCoefficients.set(Vector2D.cartesian(0.02, 0.001));
        c.maxAchievableForwardVelocity.set(40.0);
        c.maxAchievableStrafeVelocity.set(35.0);
        c.naturalForwardDeceleration.set(30.0);
        c.naturalStrafeDeceleration.set(25.0);
        c.maxPathSpeed.set(0.35);
    });

    public static Follower createFollower(HardwareMap hardwareMap) {
        Localizer localizer = new ThreeWheelLocalizer(hardwareMap, LOCALIZER);
        Drivetrain drivetrain = new Mecanum(hardwareMap, DRIVETRAIN);
        return new Follower(localizer, drivetrain, new Foresight(FORESIGHT));
    }
}
