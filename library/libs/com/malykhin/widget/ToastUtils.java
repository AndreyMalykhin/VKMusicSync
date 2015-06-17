package com.malykhin.widget;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class ToastUtils {

	/**
	 * 
	 * Same as {@link #show(Context, int, int)}, but uses bottom gravity.
	 */
	public static void show(final Context context, final int messageResourceId) {
		show(context, messageResourceId, Gravity.BOTTOM);
	}
	
	/**
	 * Shows toast. 
	 */
	public static void show(final Context context, final int messageResourceId, final int gravity) {
		
		if (context == null) {
    		return;
    	}
		
		show(context, context.getString(messageResourceId), gravity);
	}

	/**
	 * 
	 * Same as {@link #show(Context, String, int)}, but uses bottom gravity.
	 */
	public static void show(final Context context, final String msg) {
		show(context, msg, Gravity.BOTTOM);
	}
	
	/**
	 * Shows toast. 
	 */
	public static void show(final Context context, final String msg, final int gravity) {
		
		if (context == null) {
    		return;
    	}

		if (context instanceof Activity) {
			
			((Activity) context).runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
					toast.setGravity(gravity, 0, 0);
					toast.show();
				}
				
			});
		} else {
			Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
			toast.setGravity(gravity, 0, 0);
			toast.show();
		}
	}
}
