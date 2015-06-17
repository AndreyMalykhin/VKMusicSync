package com.malykhin.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class CommonUtils {
	
	public static boolean isInternetEnabled(Context appContext) {
		ConnectivityManager connectionManager = 
				(ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectionManager.getActiveNetworkInfo();
		
		return networkInfo != null && networkInfo.isConnected();
	}
	
	public static boolean isAppDebuggable(Context appContext) {
		return (appContext.getApplicationInfo().flags 
				& ApplicationInfo.FLAG_DEBUGGABLE) != 0;
	}

	public static void scanMediaFiles(Context context) {
		context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, 
				Uri.fromFile(Environment.getExternalStorageDirectory())));
	}
	
	/**
	 * 
	 * @throws NameNotFoundException If application package name can not be found on the system
	 */
	public static int getAppVersion(Context appContext) throws NameNotFoundException {
		return appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0)
				.versionCode;
	}
}
