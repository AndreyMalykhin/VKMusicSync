package com.malykhin.vkmusicsync.util;

import android.content.Context;
import android.view.Gravity;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class ToastUtils {

	public static void show(final Context context, final int messageResourceId) {
		com.malykhin.widget.ToastUtils.show(context, messageResourceId, Gravity.CENTER);
	}

	public static void show(final Context context, final String msg) {
		com.malykhin.widget.ToastUtils.show(context, msg, Gravity.CENTER);
	}
}
