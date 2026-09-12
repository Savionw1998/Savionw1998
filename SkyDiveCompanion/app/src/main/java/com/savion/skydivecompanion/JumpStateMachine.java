package com.savion.skydivecompanion;

/**
 * Dwell-timed jump phase detector. All inputs are feet and feet per minute.
 * A candidate phase must hold continuously for its dwell time before the
 * transition happens, so a gust or a door bump cannot flip the state.
 */
public final class JumpStateMachine {

    public enum Phase { GROUND, AIRCRAFT, FREEFALL, CANOPY, LANDED }

    private Phase phase = Phase.GROUND;
    private Phase candidate = null;
    private long candidateSinceMs = -1;

    public long takeoffMs = -1, exitMs = -1, openMs = -1, landMs = -1;
    public double exitAglFt = Double.NaN, openAglFt = Double.NaN;
    public double maxAglFt = 0, minVsFpm = 0, maxVsFpm = 0;

    public Phase phase() { return phase; }

    /** @return the new phase if a transition happened on this update, else null. */
    public Phase update(double aglFt, double vsFpm, long nowMs) {
        if (Double.isNaN(aglFt) || Double.isNaN(vsFpm)) return null;
        if (aglFt > maxAglFt) maxAglFt = aglFt;
        if (vsFpm < minVsFpm) minVsFpm = vsFpm;
        if (vsFpm > maxVsFpm) maxVsFpm = vsFpm;

        Phase want = null; long dwell = 0;
        switch (phase) {
            case GROUND:
                if (vsFpm > 300 && aglFt > 150) { want = Phase.AIRCRAFT; dwell = 4000; }
                break;
            case AIRCRAFT:
                if (vsFpm < -3000) { want = Phase.FREEFALL; dwell = 1500; }
                break;
            case FREEFALL:
                if (vsFpm > -2500 && vsFpm < 300) { want = Phase.CANOPY; dwell = 3000; }
                break;
            case CANOPY:
                if (aglFt < 100 && Math.abs(vsFpm) < 300) { want = Phase.LANDED; dwell = 5000; }
                break;
            default:
                break;
        }
        if (want == null) { candidate = null; candidateSinceMs = -1; return null; }
        if (candidate != want) { candidate = want; candidateSinceMs = nowMs; return null; }
        if (nowMs - candidateSinceMs < dwell) return null;
        return transition(want, aglFt, nowMs);
    }

    /** Manual override from the UI: force FREEFALL now. */
    public Phase forceFreefall(double aglFt, long nowMs) {
        if (phase == Phase.FREEFALL) return null;
        return transition(Phase.FREEFALL, aglFt, nowMs);
    }

    private Phase transition(Phase to, double aglFt, long nowMs) {
        candidate = null; candidateSinceMs = -1;
        phase = to;
        switch (to) {
            case AIRCRAFT: takeoffMs = nowMs; break;
            case FREEFALL: exitMs = nowMs; exitAglFt = Math.max(aglFt, maxAglFt); break; // exit = aircraft altitude
            case CANOPY: openMs = nowMs; openAglFt = aglFt; break;
            case LANDED: landMs = nowMs; break;
            default: break;
        }
        return to;
    }

    public long freefallSeconds() { return exitMs > 0 && openMs > 0 ? (openMs - exitMs) / 1000 : -1; }
    public long canopySeconds() { return openMs > 0 && landMs > 0 ? (landMs - openMs) / 1000 : -1; }
    public boolean inAir() { return phase == Phase.AIRCRAFT || phase == Phase.FREEFALL || phase == Phase.CANOPY; }
}
