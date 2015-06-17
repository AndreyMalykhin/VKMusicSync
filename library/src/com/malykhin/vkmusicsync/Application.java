package com.malykhin.vkmusicsync;

import android.content.Context;

import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.util.Db;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Application extends android.app.Application {

	private static final String TAG = Application.class.getSimpleName();
	private static Context context;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		context = this;

		try {
			Preferences.getInstance().init();
		} catch (UpgradeException exception) {
			Log.e(TAG, null, exception);
			
			throw new RuntimeException(exception);
		}
		
		Db.init(Preferences.getInstance());
	}
	
	public static Context getContext() {
		return context;
	}
}
