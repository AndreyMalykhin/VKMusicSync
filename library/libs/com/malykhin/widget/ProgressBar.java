package com.malykhin.widget;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.malykhin.util.Log;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class ProgressBar {
	
	private final static String TAG = ProgressBar.class.getSimpleName();
	private View container;
	private android.widget.ProgressBar progressBar;
	private TextView text;
	private boolean visible = false;
	private int showRequestsCount = 0;

	public synchronized ProgressBar setProgressBar(android.widget.ProgressBar progressBar) {
		this.progressBar = progressBar;
		return this;
	}

	public synchronized ProgressBar setText(TextView text) {
		this.text = text;
		return this;
	}

	public synchronized ProgressBar setContainer(View container) {
		this.container = container;
		this.container.setVisibility(visible ? View.VISIBLE : View.GONE);
		return this;
	}

	public synchronized void show(Activity activity) {
		showRequestsCount++;
    	
//		Log.d(TAG, "show(); showRequestsCount=" + showRequestsCount);
		
    	if (activity == null) {
    		return;
    	}
    	
    	activity.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				visible = true;
				container.setVisibility(View.VISIBLE);
			}
			
		});
	}

	/**
	 * 
	 * @param message
	 * @param progressPercentage If null, progress bar will be hidden
	 * @param activity
	 */
	public synchronized void update(final String message, final Integer progressPercentage, 
			Activity activity) 
	{
		if (activity == null) {
			return;
		}
		
		activity.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				text.setText(message);
				
				if (progressPercentage == null) {
					progressBar.setVisibility(View.GONE);
				} else {
					progressBar.setVisibility(View.VISIBLE);
					progressBar.setProgress(progressPercentage);
				}
			}
			
		});
	}
	
	public synchronized void hide(Activity activity) {
		showRequestsCount--;
		
//		Log.d(TAG, "hide(); showRequestsCount=" + showRequestsCount);
		
		if (showRequestsCount > 0 || activity == null) {
			return;
		}

		activity.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				visible = false;
				container.setVisibility(View.GONE);
			}
			
		});
	}
}
