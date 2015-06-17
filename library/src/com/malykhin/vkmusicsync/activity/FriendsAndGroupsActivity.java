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
public class FriendsAndGroupsActivity extends SherlockFragmentActivity {
	
	public static final String RESULT_INTENT_EXTRA_OWNER_ID = 
			Application.getContext().getPackageName() + ".owner_id";
	public static final String RESULT_INTENT_EXTRA_OWNER_GROUP = 
			Application.getContext().getPackageName() + ".owner_group";
	public static final String RESULT_INTENT_EXTRA_OWNER_NAME = 
			Application.getContext().getPackageName() + ".owner_name";
	
	private static final String TAG = FriendsAndGroupsActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.friends_and_groups_activity);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Analytics.start(this);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		Log.d(TAG, "onStop()");
		
		Analytics.end(this);
	}
}
