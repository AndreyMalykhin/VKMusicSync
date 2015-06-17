package com.malykhin.vkmusicsync.activity;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Application;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.util.Analytics;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class AlbumsActivity extends SherlockFragmentActivity {
	
	public static final String START_INTENT_EXTRA_OWNER_ID = 
			Application.getContext().getPackageName() + ".owner_id";
	public static final String START_INTENT_EXTRA_OWNER_GROUP = 
			Application.getContext().getPackageName() + ".owner_group";
	public static final String RESULT_INTENT_EXTRA_ALBUM_ID = 
			Application.getContext().getPackageName() + ".album_id";
	public static final String RESULT_INTENT_EXTRA_ALBUM_TITLE = 
			Application.getContext().getPackageName() + ".album_title";
	
	private static final String TAG = AlbumsActivity.class.getSimpleName();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "onCreate()");
		
		setContentView(R.layout.albums_activity);
		
		if (getIntent().getLongExtra(START_INTENT_EXTRA_OWNER_ID, 0) == 0) {
			throw new RuntimeException("Owner ID is not set via intent");
		}
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
