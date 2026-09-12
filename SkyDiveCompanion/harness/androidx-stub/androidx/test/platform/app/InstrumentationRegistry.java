package androidx.test.platform.app;
import android.app.Instrumentation; import android.os.Bundle;
public final class InstrumentationRegistry {
  private static volatile Instrumentation instrumentation; private static volatile Bundle arguments;
  private InstrumentationRegistry() {}
  public static Instrumentation getInstrumentation() {
    Instrumentation i = instrumentation;
    if (i == null) throw new IllegalStateException("No instrumentation registered.");
    return i;
  }
  public static void registerInstance(Instrumentation i, Bundle b) { instrumentation = i; arguments = b; }
  public static Bundle getArguments() { Bundle b = arguments; return b == null ? new Bundle() : new Bundle(b); }
}
