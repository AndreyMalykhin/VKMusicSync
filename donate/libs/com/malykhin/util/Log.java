package com.malykhin.util;


/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Log {
	private static boolean enabled = false;
	
	public static void setEnabled(boolean flag) {
		enabled = flag;
	}
	
	public static void d(String tag, String msg) {
		
		if (!enabled) {
			return;
		}
		
		android.util.Log.d(tag, msg);
	}

	public static void e(String tag, String msg, Throwable exception) {
		
		if (!enabled) {
			return;
		}
		
		android.util.Log.e(tag, msg, exception);
	}
}
