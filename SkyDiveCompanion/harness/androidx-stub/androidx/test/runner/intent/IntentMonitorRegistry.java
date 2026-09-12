package androidx.test.runner.intent;
public final class IntentMonitorRegistry {
  private static volatile IntentMonitor instance;
  private IntentMonitorRegistry() {}
  public static IntentMonitor getInstance() {
    IntentMonitor m = instance;
    if (m == null) throw new IllegalStateException("No intent monitor registered.");
    return m;
  }
  public static void registerInstance(IntentMonitor m) { instance = m; }
}
