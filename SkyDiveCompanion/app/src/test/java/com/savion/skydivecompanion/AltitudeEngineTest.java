package com.savion.skydivecompanion;

import java.util.Locale;
import java.util.Random;

/**
 * Plain-main test: no JUnit dependency so it runs with `java` alone.
 *
 *   javac -d out app/src/main/java/com/savion/skydivecompanion/{AltitudeEngine,JumpStateMachine}.java \
 *         app/src/test/java/com/savion/skydivecompanion/AltitudeEngineTest.java
 *   java -cp out com.savion.skydivecompanion.AltitudeEngineTest
 *
 * Simulates a 50 Hz barometer with realistic noise through a full jump:
 * 8 s calibration, ground wait with weather drift, climb to 12,500 ft AGL,
 * freefall, canopy, landing. Asserts AGL accuracy and phase transitions.
 */
public class AltitudeEngineTest {

    static int failures = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        unitConversions();
        fullJump(0.04, 42, 800.0);   // quiet sensor, 800 ft DZ
        fullJump(0.12, 7, 15.0);     // noisy sensor, near sea level
        temperatureCorrection();
        if (failures > 0) { System.out.println(failures + " FAILURE(S)"); System.exit(1); }
        System.out.println("ALL PASSED");
    }

    static void unitConversions() {
        System.out.println("Unit conversions");
        double m = AltitudeEngine.pressureToMetres(696.8, AltitudeEngine.ISA_P0_HPA);
        double ft = AltitudeEngine.metresToFeet(m);
        check(Math.abs(ft - 10000) < 40, String.format(Locale.US, "696.8 hPa is ~10,000 ft pressure altitude (got %.0f ft)", ft));
        double back = AltitudeEngine.metresToPressure(m, AltitudeEngine.ISA_P0_HPA);
        check(Math.abs(back - 696.8) < 0.01, "pressure <-> metres round trip");
        check(Math.abs(AltitudeEngine.metresToFeet(304.8) - 1000) < 1e-6, "304.8 m == 1000 ft");
    }

    static void temperatureCorrection() {
        System.out.println("Temperature correction");
        double k = AltitudeEngine.temperatureCorrection(35, 0);
        check(k > 1.06 && k < 1.08, String.format(Locale.US, "35 C at sea level scales height by %.3f (expect ~1.069)", k));
        double k2 = AltitudeEngine.temperatureCorrection(15, 0);
        check(Math.abs(k2 - 1.0) < 1e-9, "ISA day is 1.000");
    }

    static void fullJump(double noiseHpa, long seed, double dzFt) {
        System.out.println(String.format(Locale.US, "Full jump  noise=%.2f hPa  DZ=%.0f ft", noiseHpa, dzFt));
        Random rnd = new Random(seed);
        AltitudeEngine eng = new AltitudeEngine();
        JumpStateMachine sm = new JumpStateMachine();
        eng.setDzElevationFt(dzFt);
        eng.setGroundTemperatureC(15);      // ISA day so truth is exact

        double dzM = AltitudeEngine.feetToMetres(dzFt);
        double hz = 50; long dtNs = (long) (1e9 / hz);
        long tNs = 1_000_000_000L;

        // Phase timeline in seconds.
        double tCalibEnd = 8, tWaitEnd = 120, climbRate = AltitudeEngine.feetToMetres(1000) / 60.0;
        double topM = AltitudeEngine.feetToMetres(12500);
        double tClimbEnd = tWaitEnd + topM / climbRate;
        double tLevelEnd = tClimbEnd + 30;
        double tExit = tLevelEnd;
        double termV = AltitudeEngine.feetToMetres(176);   // m/s
        double openM = AltitudeEngine.feetToMetres(4000);
        double canopyV = AltitudeEngine.feetToMetres(18);

        eng.startCalibration(tNs);
        double weatherDriftHpaPerS = 1.0 / 3600.0;   // 1 hPa per hour, falling

        double trueAgl = 0, vel = 0; double t = 0;
        double maxGroundErr = 0, maxClimbRelErr = 0, maxFreefallRelErr = 0, maxCanopyErr = 0;
        long exitT = -1, openT = -1, landT = -1, takeoffT = -1;
        double exitAglEst = 0;
        boolean landed = false;
        int steps = 0;
        double lastVsFpmTrue = 0;

        while (t < tExit + 400 && !(landed && t > landT / 1e3 + 15)) {
            double dt = 1.0 / hz; t += dt; tNs += dtNs; steps++;
            long nowMs = tNs / 1_000_000L;

            // Truth model.
            if (t < tWaitEnd) { vel = 0; }
            else if (t < tClimbEnd) { vel = climbRate; }
            else if (t < tExit) { vel = 0; }
            else if (trueAgl > openM && (vel < 0 || t < tExit + 0.5)) {
                // freefall: v approaches terminal with ~9.81 decel-limited approach
                double target = -termV;
                vel += (target - vel) * Math.min(1, dt * 9.81 / termV * 3);
            } else if (trueAgl > 0) {
                // canopy: decelerate to canopy descent over 3 s then hold
                vel += (-canopyV - vel) * Math.min(1, dt / 1.0);
            } else { vel = 0; trueAgl = 0; landed = true; if (landT < 0) landT = nowMs; }
            trueAgl += vel * dt; if (trueAgl < 0) trueAgl = 0;
            lastVsFpmTrue = AltitudeEngine.metresToFeet(vel) * 60;

            double groundP = AltitudeEngine.metresToPressure(dzM, AltitudeEngine.ISA_P0_HPA) - weatherDriftHpaPerS * t;
            double truePressure = AltitudeEngine.metresToPressure(dzM + trueAgl, AltitudeEngine.ISA_P0_HPA) - weatherDriftHpaPerS * t;
            double measured = truePressure + rnd.nextGaussian() * noiseHpa;
            if (rnd.nextInt(400) == 0) measured += (rnd.nextBoolean() ? 1 : -1) * 0.8;  // occasional spike

            eng.addPressure(measured, tNs);
            AltitudeEngine.Estimate e = eng.snapshot(tNs);
            JumpStateMachine.Phase tr = sm.update(e.aglFt, e.verticalSpeedFpm, nowMs);
            eng.setGroundMode(sm.phase() == JumpStateMachine.Phase.GROUND || sm.phase() == JumpStateMachine.Phase.LANDED);
            if (tr != null) {
                System.out.println(String.format(Locale.US, "    t=%7.1fs  %-8s  AGL est %6.0f ft  true %6.0f ft  vs %6.0f fpm", t, tr, e.aglFt, AltitudeEngine.metresToFeet(trueAgl), e.verticalSpeedFpm));
                if (tr == JumpStateMachine.Phase.AIRCRAFT) takeoffT = nowMs;
                if (tr == JumpStateMachine.Phase.FREEFALL) { exitT = nowMs; exitAglEst = sm.exitAglFt; }
                if (tr == JumpStateMachine.Phase.CANOPY) openT = nowMs;
            }

            if (!e.baselineReady) continue;
            double trueAglFt = AltitudeEngine.metresToFeet(trueAgl);
            double err = e.aglFt - trueAglFt;
            if (t > tCalibEnd + 2 && t < tWaitEnd) maxGroundErr = Math.max(maxGroundErr, Math.abs(err));
            if (t > tWaitEnd + 20 && t < tClimbEnd) maxClimbRelErr = Math.max(maxClimbRelErr, Math.abs(err) / Math.max(200, trueAglFt));
            if (sm.phase() == JumpStateMachine.Phase.FREEFALL && t > tExit + 12 && trueAglFt > 4500) maxFreefallRelErr = Math.max(maxFreefallRelErr, Math.abs(err) / trueAglFt);
            if (sm.phase() == JumpStateMachine.Phase.CANOPY && t > (openT / 1e3) + 5 && trueAglFt > 200) maxCanopyErr = Math.max(maxCanopyErr, Math.abs(err));
        }

        System.out.println(String.format(Locale.US, "    ground max |err| %.1f ft, climb max rel err %.2f%%, freefall max rel err %.2f%%, canopy max |err| %.0f ft",
                maxGroundErr, maxClimbRelErr * 100, maxFreefallRelErr * 100, maxCanopyErr));
        check(maxGroundErr < 8, "ground AGL error under 8 ft despite 1 hPa/h weather drift");
        check(maxClimbRelErr < 0.01, "climb AGL error under 1%");
        check(maxFreefallRelErr < 0.02, "freefall AGL error under 2% (filter lag at 176 ft/s)");
        check(maxCanopyErr < 60, "canopy AGL error under 60 ft");
        check(takeoffT > 0 && Math.abs(takeoffT / 1e3 - (tWaitEnd + 4 + 9)) < 12, "AIRCRAFT detected within ~12 s of takeoff");
        check(exitT > 0 && exitT / 1e3 - tExit < 6 && exitT / 1e3 - tExit > 0, "FREEFALL detected within 6 s of exit");
        check(exitT > 0 && Math.abs(exitAglEst - 12500) < 200, String.format(Locale.US, "exit altitude (aircraft altitude at exit) logged within 200 ft (got %.0f)", exitAglEst));
        check(openT > 0 && Math.abs(sm.openAglFt - 4000) < 700, String.format(Locale.US, "opening altitude logged within 700 ft (got %.0f, 3 s dwell at canopy speed)", sm.openAglFt));
        check(sm.phase() == JumpStateMachine.Phase.LANDED, "LANDED reached");
        check(sm.freefallSeconds() > 40 && sm.freefallSeconds() < 70, "freefall time plausible: " + sm.freefallSeconds() + " s");
    }
}
