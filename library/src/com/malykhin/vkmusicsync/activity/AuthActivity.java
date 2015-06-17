package com.malykhin.vkmusicsync.activity;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.util.Analytics;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class AuthActivity extends SherlockFragmentActivity {
	
	private static final String TAG = AuthActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "onCreate()");
		
		setContentView(R.layout.auth_activity);
	}
	
	@Override
	public void onBackPressed() {
		super.onBackPressed();
	
		Log.d(TAG, "onBackPressed()");
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		Log.d(TAG, "onDestroy()");
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Analytics.start(this);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		Analytics.end(this);
	}
}
