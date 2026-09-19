package org.firstinspires.ftc.teamcode.pedro;

import com.pedropathing.tuning.autotune.Procedure;
import com.pedropathing.tuning.autotune.Tuner;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.localizers.ThreeWheelLocalizer;

import org.firstinspires.ftc.teamcode.pedro.procedures.ForesightTuner;
import org.firstinspires.ftc.teamcode.pedro.procedures.MecanumTuner;
import org.firstinspires.ftc.teamcode.pedro.procedures.Tests;
import org.firstinspires.ftc.teamcode.pedro.procedures.ThreeWheelTuner;

/** Procedures shown by Pedro AutoTune at http://192.168.43.1:10158. */
public final class Tuning {
    private Tuning() {
    }

    @Tuner
    public static Procedure mecanumTuner() {
        return new MecanumTuner();
    }

    @Tuner
    public static Procedure threeWheelTuner() {
        return new ThreeWheelTuner();
    }

    @Tuner
    public static Procedure foresightTuner() {
        return new ForesightTuner(
                hardwareMap -> new ThreeWheelLocalizer(hardwareMap, DuckPedroConstants.LOCALIZER),
                hardwareMap -> new Mecanum(hardwareMap, DuckPedroConstants.DRIVETRAIN));
    }

    @Tuner
    public static Procedure tests() {
        return new Tests(
                hardwareMap -> new Mecanum(hardwareMap, DuckPedroConstants.DRIVETRAIN),
                hardwareMap -> new ThreeWheelLocalizer(hardwareMap, DuckPedroConstants.LOCALIZER),
                () -> new com.pedropathing.algorithm.Foresight(DuckPedroConstants.FORESIGHT));
    }
}
