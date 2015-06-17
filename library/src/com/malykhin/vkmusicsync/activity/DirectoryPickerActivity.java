package com.malykhin.vkmusicsync.activity;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.malykhin.vkmusicsync.Application;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.util.Analytics;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class DirectoryPickerActivity extends SherlockFragmentActivity {

	public static final String START_INTENT_EXTRA_OWNER_ID = 
			Application.getContext().getPackageName() + ".owner_id";
	public static final String START_INTENT_EXTRA_OWNER_GROUP = 
			Application.getContext().getPackageName() + ".owner_group";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.directory_picker_activity);
		
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
