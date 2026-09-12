package com.savion.skydivecompanion;

/**
 * Barometric altitude engine.
 *
 * Pure Java, no Android imports, so it can be exercised from a plain main()
 * with synthetic pressure traces (see app/src/test/.../AltitudeEngineTest).
 *
 * Pipeline per pressure sample:
 *   raw hPa -> median-of-5 spike filter -> ISA pressure altitude (metres)
 *           -> 2-state Kalman filter (altitude, vertical speed)
 *           -> AGL against a median ground baseline
 *           -> temperature correction -> feet
 *
 * Every unit conversion lives here. Callers only ever see feet and ft/min.
 */
public final class AltitudeEngine {

    public static final double FT_PER_M = 3.280839895;
    public static final double ISA_P0_HPA = 1013.25;
    public static final double ISA_T0_K = 288.15;
    public static final double ISA_LAPSE_K_PER_M = 0.0065;
    private static final double HYPSO_K = 44330.77;
    private static final double HYPSO_EXP = 0.190263;

    /** Result snapshot. Values in feet, ft/min, hPa. NaN when unknown. */
    public static final class Estimate {
        public double aglFt = Double.NaN;
        public double mslFt = Double.NaN;
        public double pressureAltFt = Double.NaN;
        public double verticalSpeedFpm = Double.NaN;
        public double rawHpa = Double.NaN;
        public double filteredHpa = Double.NaN;
        public double baselineHpa = Double.NaN;
        public double noiseFt = Double.NaN;
        public double baselineAgeSec = Double.NaN;
        public double gpsAltFt = Double.NaN;
        public double gpsAccuracyFt = Double.NaN;
        public double gpsSpeedMph = Double.NaN;
        public double gpsBaroDeltaFt = Double.NaN;
        public double dzElevationFt = Double.NaN;
        public String dzSource = "none";
        public boolean baselineReady;
        public boolean calibrating;
        public double calibrationProgress;
        public int confidence; // 0..4
        public boolean temperatureCorrected;
    }

    // ---- configuration -------------------------------------------------
    private double groundTempC = 15.0;
    private boolean hasGroundTemp = false;
    private double dzElevationFtUser = Double.NaN;
    private boolean autoRezero = true;
    private boolean gpsCrossCheck = true;
    private boolean groundMode = true;          // set by the state machine
    private double sigmaAccelGround = 0.35;     // m/s^2 process noise on the ground
    private double sigmaAccelAir = 3.0;         // m/s^2 process noise in flight
    private double sigmaMeasM = 0.5;            // metres, replaced by measured noise

    // ---- spike filter ---------------------------------------------------
    private final double[] med = new double[5];
    private int medFill = 0, medIdx = 0;

    // ---- Kalman state (ISA pressure altitude, metres) -------------------
    private double h = Double.NaN, v = 0;
    private double p00 = 25, p01 = 0, p11 = 4;
    private long lastNs = -1;
    private double lastDt = 0.02;
    private double lastRawHpa = Double.NaN, lastFilteredHpa = Double.NaN;

    // ---- ground baseline ------------------------------------------------
    private double hGround = Double.NaN;          // ISA metres at the ground
    private boolean baselineReady = false;
    private long baselineNs = -1;
    private double noiseM = Double.NaN;

    private boolean calibrating = false;
    private long calibStartNs = -1;
    private long calibDurationNs = 8_000_000_000L;
    private static final long CALIB_SKIP_NS = 1_500_000_000L;
    private final double[] calibBuf = new double[4096];
    private int calibCount = 0;

    private long stillSinceNs = -1;

    // ---- GPS ------------------------------------------------------------
    private double gpsAltM = Double.NaN, gpsAccM = Double.NaN, gpsSpeedMps = Double.NaN;
    private long gpsMs = -1;
    private double gpsGroundSumW = 0, gpsGroundSumWA = 0;   // weighted DZ estimate
    private double dzElevationFtGps = Double.NaN;

    // ---- unit helpers (public, static, the single source of truth) -------
    public static double pressureToMetres(double hpa, double refHpa) {
        return HYPSO_K * (1.0 - Math.pow(hpa / refHpa, HYPSO_EXP));
    }
    public static double metresToPressure(double m, double refHpa) {
        return refHpa * Math.pow(1.0 - m / HYPSO_K, 1.0 / HYPSO_EXP);
    }
    public static double metresToFeet(double m) { return m * FT_PER_M; }
    public static double feetToMetres(double ft) { return ft / FT_PER_M; }
    public static double fahrenheitToCelsius(double f) { return (f - 32.0) * 5.0 / 9.0; }

    /** True height ~= indicated height x (T_actual / T_isa at the ground). */
    public static double temperatureCorrection(double groundTempC, double groundAltM) {
        double tIsa = ISA_T0_K - ISA_LAPSE_K_PER_M * groundAltM;
        double tAct = 273.15 + groundTempC;
        double k = tAct / tIsa;
        if (k < 0.85) k = 0.85; if (k > 1.15) k = 1.15;
        return k;
    }

    // ---- configuration setters ----------------------------------------
    public void setGroundTemperatureC(double c) { groundTempC = c; hasGroundTemp = !Double.isNaN(c); }
    public void clearGroundTemperature() { hasGroundTemp = false; groundTempC = 15.0; }
    public void setDzElevationFt(double ft) { dzElevationFtUser = ft; }
    public void setAutoRezero(boolean on) { autoRezero = on; }
    public void setGpsCrossCheck(boolean on) { gpsCrossCheck = on; }
    /** GROUND phase => low process noise and auto re-zero allowed. */
    public void setGroundMode(boolean ground) { groundMode = ground; if (!ground) stillSinceNs = -1; }
    public void setCalibrationDurationMs(long ms) { calibDurationNs = ms * 1_000_000L; }
    public boolean isGroundMode() { return groundMode; }

    // ---- calibration ----------------------------------------------------
    public void startCalibration(long nowNs) {
        calibrating = true; calibStartNs = nowNs; calibCount = 0;
    }
    public boolean isCalibrating() { return calibrating; }
    public boolean isBaselineReady() { return baselineReady; }

    private void finishCalibration(long nowNs) {
        calibrating = false;
        if (calibCount < 8) {
            // Too few samples: fall back to the current filtered altitude.
            if (!Double.isNaN(h)) { hGround = h; baselineReady = true; baselineNs = nowNs; }
            return;
        }
        double[] sorted = new double[calibCount];
        System.arraycopy(calibBuf, 0, sorted, 0, calibCount);
        java.util.Arrays.sort(sorted);
        double median = calibCount % 2 == 1 ? sorted[calibCount / 2]
                : 0.5 * (sorted[calibCount / 2 - 1] + sorted[calibCount / 2]);
        double ss = 0;
        for (int i = 0; i < calibCount; i++) { double d = sorted[i] - median; ss += d * d; }
        noiseM = Math.sqrt(ss / Math.max(1, calibCount - 1));
        // Adopt measured noise (bounded) as the Kalman measurement sigma.
        sigmaMeasM = Math.min(3.0, Math.max(0.15, noiseM));
        hGround = median;
        baselineReady = true;
        baselineNs = nowNs;
        stillSinceNs = nowNs;
    }

    /** Immediate manual re-zero to the current filtered altitude. */
    public void rezero(long nowNs) {
        if (Double.isNaN(h)) return;
        hGround = h; baselineReady = true; baselineNs = nowNs; calibrating = false;
    }

    // ---- main input -----------------------------------------------------
    public void addPressure(double hpa, long tNs) {
        if (!(hpa > 300 && hpa < 1100)) return;   // discard impossible readings
        lastRawHpa = hpa;

        // Median-of-5 spike filter.
        med[medIdx] = hpa; medIdx = (medIdx + 1) % 5; if (medFill < 5) medFill++;
        double filtered = hpa;
        if (medFill == 5) {
            double[] c = med.clone(); java.util.Arrays.sort(c); filtered = c[2];
        }
        lastFilteredHpa = filtered;

        double z = pressureToMetres(filtered, ISA_P0_HPA);

        if (Double.isNaN(h) || lastNs < 0) {
            h = z; v = 0; p00 = 4; p01 = 0; p11 = 4; lastNs = tNs;
        } else {
            double dt = (tNs - lastNs) / 1e9;
            if (dt <= 0) dt = 0.001; if (dt > 2.0) dt = 2.0;
            lastNs = tNs; lastDt = dt;

            // Predict (constant velocity).
            double sa = groundMode ? sigmaAccelGround : sigmaAccelAir;
            double q = sa * sa;
            h += v * dt;
            double q00 = q * dt * dt * dt * dt / 4, q01 = q * dt * dt * dt / 2, q11 = q * dt * dt;
            double n00 = p00 + dt * (p01 + p01) + dt * dt * p11 + q00;
            double n01 = p01 + dt * p11 + q01;
            double n11 = p11 + q11;
            p00 = n00; p01 = n01; p11 = n11;

            // Update.
            double r = sigmaMeasM * sigmaMeasM;
            double y = z - h;
            double s = p00 + r;
            // Manoeuvre detection: a large innovation while on the ground
            // means we are actually moving; open the filter up.
            if (groundMode && Math.abs(y) > 6 * Math.sqrt(s)) {
                p00 += 100; p11 += 25; s = p00 + r;
            }
            double k0 = p00 / s, k1 = p01 / s;
            h += k0 * y; v += k1 * y;
            double o00 = p00, o01 = p01;
            p00 = (1 - k0) * o00;
            p01 = (1 - k0) * o01;
            p11 = p11 - k1 * o01;
        }

        // Calibration sampling.
        if (calibrating) {
            if (tNs - calibStartNs >= CALIB_SKIP_NS && calibCount < calibBuf.length) calibBuf[calibCount++] = z;
            if (tNs - calibStartNs >= calibDurationNs) finishCalibration(tNs);
        }

        // Slow auto re-zero while genuinely still on the ground.
        if (baselineReady && !calibrating && autoRezero && groundMode) {
            double aglM = h - hGround;
            boolean still = Math.abs(v) < 0.3 && Math.abs(aglM) < 12;   // ~60 ft/min, ~40 ft
            if (still) {
                if (stillSinceNs < 0) stillSinceNs = tNs;
                if (tNs - stillSinceNs > 20_000_000_000L) {
                    double dt = lastDt;
                    double tau = 180.0;
                    hGround += (h - hGround) * (dt / tau);
                    baselineNs = tNs;
                }
            } else {
                stillSinceNs = -1;
            }
        }
    }

    /** GPS fix. altM: MSL metres when available, else WGS84. */
    public void addGps(double altM, double accuracyM, double speedMps, long nowMs) {
        gpsAltM = altM; gpsAccM = accuracyM; gpsSpeedMps = speedMps; gpsMs = nowMs;
        if (groundMode && !Double.isNaN(altM) && accuracyM > 0 && accuracyM < 25) {
            double w = 1.0 / (accuracyM * accuracyM);
            gpsGroundSumW = gpsGroundSumW * 0.98 + w;
            gpsGroundSumWA = gpsGroundSumWA * 0.98 + w * altM;
            dzElevationFtGps = metresToFeet(gpsGroundSumWA / gpsGroundSumW);
        }
    }

    // ---- outputs --------------------------------------------------------
    public double dzElevationFt() {
        if (!Double.isNaN(dzElevationFtUser)) return dzElevationFtUser;
        return dzElevationFtGps;
    }
    public String dzSource() {
        if (!Double.isNaN(dzElevationFtUser)) return "user";
        if (!Double.isNaN(dzElevationFtGps)) return "gps";
        return "none";
    }

    private double correctionFactor() {
        if (!hasGroundTemp) return 1.0;
        double groundAltM = Double.isNaN(hGround) ? 0 : hGround;
        double dz = dzElevationFt();
        if (!Double.isNaN(dz)) groundAltM = feetToMetres(dz);
        return temperatureCorrection(groundTempC, groundAltM);
    }

    public Estimate snapshot(long nowNs) {
        Estimate e = new Estimate();
        e.rawHpa = lastRawHpa; e.filteredHpa = lastFilteredHpa;
        e.calibrating = calibrating;
        if (calibrating) e.calibrationProgress = Math.min(1.0, (nowNs - calibStartNs) / (double) calibDurationNs);
        e.baselineReady = baselineReady;
        e.dzElevationFt = dzElevationFt(); e.dzSource = dzSource();
        e.temperatureCorrected = hasGroundTemp;
        if (!Double.isNaN(h)) e.pressureAltFt = metresToFeet(h);
        if (!Double.isNaN(gpsAltM)) {
            e.gpsAltFt = metresToFeet(gpsAltM);
            e.gpsAccuracyFt = metresToFeet(gpsAccM);
            if (!Double.isNaN(gpsSpeedMps)) e.gpsSpeedMph = gpsSpeedMps * 2.2369363;
        }
        if (baselineReady && !Double.isNaN(h)) {
            double k = correctionFactor();
            double aglM = (h - hGround) * k;
            double aglFt = metresToFeet(aglM);
            if (Math.abs(aglFt) < 3) aglFt = 0;          // dead-band on the ground
            if (aglFt < 0 && groundMode) aglFt = 0;
            e.aglFt = aglFt;
            double fpm = metresToFeet(v * k) * 60.0;
            if (fpm > 25000) fpm = 25000; else if (fpm < -25000) fpm = -25000;
            e.verticalSpeedFpm = fpm;
            e.baselineHpa = metresToPressure(hGround, ISA_P0_HPA);
            e.baselineAgeSec = baselineNs > 0 ? (nowNs - baselineNs) / 1e9 : Double.NaN;
            e.noiseFt = Double.isNaN(noiseM) ? Double.NaN : metresToFeet(noiseM);
            double dz = dzElevationFt();
            if (!Double.isNaN(dz)) e.mslFt = dz + aglFt;
            if (!Double.isNaN(e.mslFt) && !Double.isNaN(e.gpsAltFt)) e.gpsBaroDeltaFt = e.gpsAltFt - e.mslFt;
        }
        e.confidence = confidence(e, nowNs);
        return e;
    }

    private int confidence(Estimate e, long nowNs) {
        if (!e.baselineReady) return 0;
        int c = 1;
        if (!Double.isNaN(noiseM) && noiseM < 0.6) c++;                 // quiet sensor
        if (e.temperatureCorrected || !Double.isNaN(e.dzElevationFt)) c++; // field setup done
        if (gpsCrossCheck && !Double.isNaN(e.gpsBaroDeltaFt) && gpsMs > 0) {
            double tol = Math.max(metresToFeet(30), 3 * e.gpsAccuracyFt);
            if (Math.abs(e.gpsBaroDeltaFt) <= tol) c++; else c = Math.max(1, c - 1);
        } else {
            c++; // no GPS to disagree with
        }
        return Math.min(4, c);
    }
}
