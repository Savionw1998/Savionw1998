package androidx.test.internal.runner.lifecycle;
import android.app.Application;
import androidx.test.runner.lifecycle.*;
import java.lang.ref.WeakReference;
import java.util.*;
public class ApplicationLifecycleMonitorImpl implements ApplicationLifecycleMonitor {
  private final List<WeakReference<ApplicationLifecycleCallback>> callbacks = new ArrayList<>();
  public ApplicationLifecycleMonitorImpl() {}
  @Override public synchronized void addLifecycleCallback(ApplicationLifecycleCallback c) {
    if (c == null) return;
    for (WeakReference<ApplicationLifecycleCallback> r : callbacks) if (r.get() == c) return;
    callbacks.add(new WeakReference<>(c));
  }
  @Override public synchronized void removeLifecycleCallback(ApplicationLifecycleCallback c) {
    for (Iterator<WeakReference<ApplicationLifecycleCallback>> it = callbacks.iterator(); it.hasNext();) {
      ApplicationLifecycleCallback v = it.next().get();
      if (v == null || v == c) it.remove();
    }
  }
  public void signalLifecycleChange(Application app, ApplicationStage stage) {
    List<ApplicationLifecycleCallback> snapshot = new ArrayList<>();
    synchronized (this) { for (WeakReference<ApplicationLifecycleCallback> r : callbacks) { ApplicationLifecycleCallback c = r.get(); if (c != null) snapshot.add(c); } }
    for (ApplicationLifecycleCallback c : snapshot) c.onApplicationLifecycleChanged(app, stage);
  }
}
