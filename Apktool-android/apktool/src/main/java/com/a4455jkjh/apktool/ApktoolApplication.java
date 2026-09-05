package com.a4455jkjh.apktool;

import android.app.Application;
import com.a4455jkjh.apktool.util.Settings;
import org.mozilla.geckoview.GeckoRuntime;
import java.security.Security;
import sun1.security.provider.JavaProvider;

public class ApktoolApplication extends Application {
	private static GeckoRuntime geckoRuntime;

	@Override
	public void onCreate() {
		super.onCreate();
		Security.addProvider(new JavaProvider());
		geckoRuntime = GeckoRuntime.create(this);
		/*new Thread(){
			@Override
			public void run() {
				
			}
		}.start();*/
		Settings.init(ApktoolApplication.this);
	}

	public static GeckoRuntime getGeckoRuntime() {
		return geckoRuntime;
	}

}
