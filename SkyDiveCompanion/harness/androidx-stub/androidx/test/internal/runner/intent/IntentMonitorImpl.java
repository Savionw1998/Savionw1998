package androidx.test.internal.runner.intent;
import android.content.Intent;
import androidx.test.runner.intent.*;
import java.lang.ref.WeakReference;
import java.util.*;
public class IntentMonitorImpl implements IntentMonitor {
  private final List<WeakReference<IntentCallback>> callbacks = new ArrayList<>();
  public IntentMonitorImpl() {}
  @Override public synchronized void addIntentCallback(IntentCallback c) {
    if (c == null) return;
    for (WeakReference<IntentCallback> r : callbacks) if (r.get() == c) return;
    callbacks.add(new WeakReference<>(c));
  }
  @Override public synchronized void removeIntentCallback(IntentCallback c) {
    for (Iterator<WeakReference<IntentCallback>> it = callbacks.iterator(); it.hasNext();) {
      IntentCallback v = it.next().get();
      if (v == null || v == c) it.remove();
    }
  }
  public void signalIntent(Intent intent) {
    List<IntentCallback> snapshot = new ArrayList<>();
    synchronized (this) { for (WeakReference<IntentCallback> r : callbacks) { IntentCallback c = r.get(); if (c != null) snapshot.add(c); } }
    for (IntentCallback c : snapshot) c.onIntentSent(intent);
  }
}
