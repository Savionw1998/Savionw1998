package androidx.test.internal.platform.os;
import android.view.View;
public interface ControlledLooper {
  ControlledLooper NO_OP_CONTROLLED_LOOPER = new ControlledLooper() {
    @Override public void drainMainThreadUntilIdle() {}
    @Override public void simulateWindowFocus(View view) {}
    @Override public boolean areDrawCallbacksSupported() { return true; }
  };
  void drainMainThreadUntilIdle();
  void simulateWindowFocus(View decorView);
  boolean areDrawCallbacksSupported();
}
