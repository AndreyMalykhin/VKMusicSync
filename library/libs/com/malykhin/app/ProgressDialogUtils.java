package com.malykhin.app;

import java.util.Map;
import java.util.WeakHashMap;

import com.malykhin.util.Log;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class ProgressDialogUtils {

	public static final String DEFAULT_TAG = "progress_bar";
	
	private static final String TAG = ProgressDialogUtils.class.getSimpleName();
	private static final Map<Object, Integer> showRequestIds = 
			new WeakHashMap<Object, Integer>();
	
	/**
	 * Creates and immediately shows progress dialog if its not already shown. Allows dialog to 
	 * loose its state.
	 * 
	 * @param manager
	 * @param message
	 * @param context Typically fragment with retained instance
	 * 		{@link Fragment#setRetainInstance(boolean)}, that shows dialog
	 * @param tag
	 * @return request ID
	 */
	public static synchronized int show(FragmentManager manager, String message, Object context, 
			String tag) 
	{
		Integer currentRequestId = showRequestIds.get(context);
		
		if (currentRequestId == null) {
			currentRequestId = 1;
		} else {
			currentRequestId++;
		}
		
		if (manager != null) {
			ProgressDialog dialog = (ProgressDialog) manager.findFragmentByTag(tag);
			
			if (dialog != null) {
				dialog.dismissAllowingStateLoss();
			}
			
			dialog = new ProgressDialog(message);
			dialog.showAllowingStateLoss(manager, tag);
			manager.executePendingTransactions();
		}
		
		showRequestIds.put(context, currentRequestId);
		
		return currentRequestId;
	}
	
	/**
	 * Same as {@link #show(FragmentManager, String, Object, String)}, but uses default tag 
	 * {@link #DEFAULT_TAG}.
	 */
	public static synchronized int show(FragmentManager manager, String message, Object context) {
		return show(manager, message, context, DEFAULT_TAG);
	}
	
	/**
	 * 
	 * Immediately hides progress dialog, that was showed by 
	 * {@link #show(FragmentManager, String, String)}. Allows dialog to loose its state.
	 * 
	 * @param manager
	 * @param requestId ID of the request, returned by {@link #show(FragmentManager, String, String)}
	 * @param context Typically fragment with retained instance
	 * 		{@link Fragment#setRetainInstance(boolean)}, that shows dialog
	 * @param tag
	 */
	public static synchronized void hide(FragmentManager manager, int requestId, Object context, 
			String tag) 
	{
		Integer currentRequestId = showRequestIds.get(context);
		
		if (currentRequestId == null || !currentRequestId.equals(requestId) || manager == null) {
			return;
		}

		ProgressDialog progressDialog = (ProgressDialog) manager.findFragmentByTag(tag);

		if (progressDialog != null) {
			progressDialog.dismissAllowingStateLoss();
			manager.executePendingTransactions();
		}
	}
	
	/**
	 * Same as {@link #show(FragmentManager, String, String)}, but uses default tag 
	 * {@link #DEFAULT_TAG}.
	 */
	public static synchronized void hide(FragmentManager manager, int requestId, Object context) {
		hide(manager, requestId, context, DEFAULT_TAG);
	}
}
