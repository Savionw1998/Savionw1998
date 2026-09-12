package androidx.test.espresso;
import android.os.Looper;
import java.util.*;
public final class IdlingRegistry {
  private static final IdlingRegistry INSTANCE = new IdlingRegistry();
  private final Set<IdlingResource> resources = Collections.synchronizedSet(new HashSet<IdlingResource>());
  private final Set<Looper> loopers = Collections.synchronizedSet(new HashSet<Looper>());
  public static IdlingRegistry getInstance() { return INSTANCE; }
  public Collection<IdlingResource> getResources() { synchronized (resources) { return new ArrayList<>(resources); } }
  public Collection<Looper> getLoopers() { synchronized (loopers) { return new ArrayList<>(loopers); } }
  public boolean register(IdlingResource... rs) { return resources.addAll(Arrays.asList(rs)); }
  public boolean unregister(IdlingResource... rs) { return resources.removeAll(Arrays.asList(rs)); }
  public boolean registerLooperAsIdlingResource(Looper l) { return loopers.add(l); }
  public boolean unregisterLooperAsIdlingResource(Looper l) { return loopers.remove(l); }
}
