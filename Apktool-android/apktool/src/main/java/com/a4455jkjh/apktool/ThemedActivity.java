package com.a4455jkjh.apktool;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.app.StatusBarManager;
import android.view.MotionEvent;
import android.view.View;
import androidx.fragment.app.FragmentActivity;
import com.a4455jkjh.apktool.R;
import com.a4455jkjh.apktool.util.Settings;

public abstract class ThemedActivity extends FragmentActivity {
	private float twoFingerStartY;
	private boolean twoFingerTracking;
	private boolean notificationPanelRequested;

	protected abstract void init(Bundle savedInstanceState);
	@Override
	public final void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTheme(this);
		init(savedInstanceState);
	}

	public static void setTheme(Activity act) {
		int flag = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
			View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
			View.SYSTEM_UI_FLAG_LOW_PROFILE;
if (act instanceof MainActivity) {
act.setTheme(Settings.lightTheme
? R.style.LauncherThemeLight
: R.style.LauncherTheme);
} else if (Settings.lightTheme) {
			act.setTheme(R.style.AppThemeLight);
			View view = act.getWindow().getDecorView();
			int flags = view.getSystemUiVisibility() |
				flag;
			view.setSystemUiVisibility(flags);
		} else {
			act.setTheme(R.style.AppTheme);
			if (Build.VERSION.SDK_INT >= 23) {
				View view = act.getWindow().getDecorView();
				int flags = view.getSystemUiVisibility() &
					(~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) |
					flag;
				view.setSystemUiVisibility(flags);
			}
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
				&& event.getPointerCount() >= 2) {
			twoFingerStartY = event.getY();
			twoFingerTracking = true;
			notificationPanelRequested = false;
		} else if (twoFingerTracking
				&& event.getActionMasked() == MotionEvent.ACTION_MOVE
				&& event.getPointerCount() >= 2
				&& !notificationPanelRequested
				&& event.getY() - twoFingerStartY > 80) {
			notificationPanelRequested = true;
			try {
				StatusBarManager statusBar =
						(StatusBarManager) getSystemService(STATUS_BAR_SERVICE);
				java.lang.reflect.Method expand =
						StatusBarManager.class.getMethod("expandNotificationsPanel");
				expand.setAccessible(true);
				expand.invoke(statusBar);
			} catch (Exception ignored) {
				// Android only permits this API to system apps on some devices.
			}
		} else if (event.getActionMasked() == MotionEvent.ACTION_UP
				|| event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
			twoFingerTracking = false;
			notificationPanelRequested = false;
		}
		return super.dispatchTouchEvent(event);
	}



	@Override
	protected void onResume() {
		super.onResume();
		Settings.loadSettings(this);
		if (Settings.isThemeChanged) {
			Settings.isThemeChanged = false;
			recreate();
		}
	}
}
