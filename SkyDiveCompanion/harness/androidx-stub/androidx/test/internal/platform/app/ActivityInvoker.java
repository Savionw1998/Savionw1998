package androidx.test.internal.platform.app;
import android.app.Activity; import android.content.Intent; import android.os.Bundle;
public interface ActivityInvoker {
  default Intent getIntentForActivity(Class<? extends Activity> activityClass) { return null; }
  void startActivity(Intent intent, Bundle activityOptions);
  void startActivity(Intent intent);
  void resumeActivity(Activity activity);
  void pauseActivity(Activity activity);
  void stopActivity(Activity activity);
  void recreateActivity(Activity activity);
  void finishActivity(Activity activity);
}
