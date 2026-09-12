package androidx.test.platform.ui;
import android.view.MotionEvent;
public interface UiController {
  boolean injectMotionEvent(MotionEvent event) throws InjectEventSecurityException;
  boolean injectKeyEvent(android.view.KeyEvent event) throws InjectEventSecurityException;
  boolean injectString(String str) throws InjectEventSecurityException;
  void loopMainThreadUntilIdle();
  void loopMainThreadForAtLeast(long millisDelay);
}
