package androidx.test.internal.runner.lifecycle;
import android.app.Activity;
import androidx.test.runner.lifecycle.*;
import java.lang.ref.WeakReference;
import java.util.*;
public class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {
  private final List<WeakReference<ActivityLifecycleCallback>> callbacks = new ArrayList<>();
  private final Map<Activity, Stage> stages = Collections.synchronizedMap(new WeakHashMap<Activity, Stage>());
  public ActivityLifecycleMonitorImpl() {}
  public ActivityLifecycleMonitorImpl(boolean declawed) {}
  @Override public synchronized void addLifecycleCallback(ActivityLifecycleCallback c) {
    if (c == null) return;
    for (WeakReference<ActivityLifecycleCallback> r : callbacks) if (r.get() == c) return;
    callbacks.add(new WeakReference<>(c));
  }
  @Override public synchronized void removeLifecycleCallback(ActivityLifecycleCallback c) {
    for (Iterator<WeakReference<ActivityLifecycleCallback>> it = callbacks.iterator(); it.hasNext();) {
      ActivityLifecycleCallback v = it.next().get();
      if (v == null || v == c) it.remove();
    }
  }
  @Override public Stage getLifecycleStageOf(Activity a) {
    Stage s = stages.get(a);
    if (s == null) throw new IllegalArgumentException("Unknown activity: " + a);
    return s;
  }
  @Override public Collection<Activity> getActivitiesInStage(Stage stage) {
    List<Activity> out = new ArrayList<>();
    synchronized (stages) { for (Map.Entry<Activity, Stage> e : stages.entrySet()) if (stage == e.getValue()) out.add(e.getKey()); }
    return out;
  }
  public void signalLifecycleChange(Stage stage, Activity activity) {
    stages.put(activity, stage);
    List<ActivityLifecycleCallback> snapshot = new ArrayList<>();
    synchronized (this) { for (WeakReference<ActivityLifecycleCallback> r : callbacks) { ActivityLifecycleCallback c = r.get(); if (c != null) snapshot.add(c); } }
    for (ActivityLifecycleCallback c : snapshot) c.onActivityLifecycleChanged(activity, stage);
  }
}
