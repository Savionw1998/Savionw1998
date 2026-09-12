package androidx.test.runner.lifecycle;
public final class ApplicationLifecycleMonitorRegistry {
  private static volatile ApplicationLifecycleMonitor instance;
  private ApplicationLifecycleMonitorRegistry() {}
  public static ApplicationLifecycleMonitor getInstance() {
    ApplicationLifecycleMonitor m = instance;
    if (m == null) throw new IllegalStateException("No application lifecycle monitor registered.");
    return m;
  }
  public static void registerInstance(ApplicationLifecycleMonitor m) { instance = m; }
}
