package com.malykhin.vkmusicsync.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.malykhin.util.CommonUtils;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicDirectoryMapper;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.util.Analytics;
import com.malykhin.vkmusicsync.util.ToastUtils;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class PreferenceActivity extends SherlockPreferenceActivity {

	private static final String TAG = PreferenceActivity.class.getSimpleName();
	private static final int AUTH_ACTIVITY_REQUEST_CODE = 1;
	private static final int DIRECTORY_PICKER_ACTIVITY_REQUEST_CODE = 2;
	
	private SharedPreferences preferences;
	
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.preference_activity);
		addPreferencesFromResource(R.xml.preferences);
		
		preferences = Preferences.getInstance().getSharedPreferences();
		
		initAccountPreference();
		initMusicDirPreference();
		initWriteToDeveloperPreference();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (resultCode != RESULT_OK) {
			return;
		}

		if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
			Preference accountPreference = findPreference(Preferences.ACCOUNT);
			accountPreference.setSummary(preferences.getString(Preferences.ACCOUNT, null));
		} else if (requestCode == DIRECTORY_PICKER_ACTIVITY_REQUEST_CODE) {
			MusicOwner owner = new MusicOwner();
			owner.setId(Preferences.getInstance().getUserId())
				.setGroup(false);
			MusicDirectory musicDir = MusicDirectoryMapper.getInstance().getOneByOwner(owner);
			
			if (musicDir != null) {
				Preference musicDirPreference = findPreference(Preferences.MUSIC_DIRECTORY);
				musicDirPreference.setSummary(musicDir.getDirectory().getAbsolutePath());
			}
		}
	}

	private void initMusicDirPreference() {
		Preference musicDirPreference = findPreference(Preferences.MUSIC_DIRECTORY);
		musicDirPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				gotoMusicDirPicker();
				return true;
			}
			
		});
		
		MusicOwner owner = new MusicOwner();
		owner.setId(Preferences.getInstance().getUserId())
			.setGroup(false);
		MusicDirectory musicDir = null;
		
		try {
			musicDir = Preferences.getInstance().getCurrentOrAddDefaultMusicDir(owner);
		} catch (Exception exception) {
			Log.e(TAG, null, exception);
			
			ToastUtils.show(this, R.string.error_while_creating_directory_for_music);
			return;
		}
		
		if (musicDir != null) {
			musicDirPreference.setSummary(musicDir.getDirectory().getAbsolutePath());
		}
	}
	
	private void gotoMusicDirPicker() {
		Intent intent = new Intent(this, DirectoryPickerActivity.class);
		intent.putExtra(DirectoryPickerActivity.START_INTENT_EXTRA_OWNER_ID, 
				Preferences.getInstance().getUserId());
		intent.putExtra(DirectoryPickerActivity.START_INTENT_EXTRA_OWNER_GROUP, false);
		startActivityForResult(intent, DIRECTORY_PICKER_ACTIVITY_REQUEST_CODE);
	}

	private void initAccountPreference() {
		Preference preference = findPreference(Preferences.ACCOUNT);
		preference.setSummary(preferences.getString(Preferences.ACCOUNT, null));
		preference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				gotoAuth();
				return true;
			}
			
		});
	}
	
	private void initWriteToDeveloperPreference() {
		Preference preference = findPreference(Preferences.WRITE_TO_DEVELOPER);
		preference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				writeToDeveloper();
				return true;
			}
			
		});
	}

	private void gotoAuth() {
		
		if (!CommonUtils.isInternetEnabled(this)) {
			ToastUtils.show(this, R.string.please_enable_internet);
			return;
		}
		
		startActivityForResult(new Intent(this, AuthActivity.class), 
				AUTH_ACTIVITY_REQUEST_CODE);
	}
	
	private void writeToDeveloper() {
		Intent intent = new Intent(Intent.ACTION_SENDTO, 
				Uri.parse("mailto:vk.music.sync@gmail.com"));
		
		startActivity(Intent.createChooser(intent, 
				getResources().getString(R.string.send_email_via)));
	}

}
