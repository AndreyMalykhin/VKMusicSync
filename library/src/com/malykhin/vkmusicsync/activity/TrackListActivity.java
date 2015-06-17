package com.malykhin.vkmusicsync.activity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.view.ViewPager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.malykhin.app.TabsAdapter;
import com.malykhin.util.CommonUtils;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.service.SyncService;
import com.malykhin.vkmusicsync.util.Analytics;
import com.pad.android.iappad.AdController;
import com.pad.android.listener.AdListener;

/**
 * TODO fix bug: when entered wrong account and then pressed back key, AuthActivity will be shown 
 * again with infinite progress bar
 * TODO refactor: split EventListener into multiple interfaces
 * 
 * @author Andrey Malykhin
 *
 */
public class TrackListActivity extends SherlockFragmentActivity {

	public interface EventListener {
		
		/**
		 * 
		 * @return False to prevent finishing of activity
		 */
		public boolean onBackPressed();
		
		/**
		 * 
		 * @return False to prevent showing of search view
		 */
		public boolean onShowSearchView();
		
		public void onSearch(String query);
		
		public void onUserAuthed(long userId);
		
		public void onOwnerChanged(MusicOwner owner, String name);

		public void onAlbumChanged(long id, String title);
	}

	public static class SyncServiceConnection implements ServiceConnection {

		private static final String TAG = TrackListActivity.TAG + "." + 
				SyncServiceConnection.class.getSimpleName();
		private Set<ServiceConnection> connectionListeners = new HashSet<ServiceConnection>(2);
		private ComponentName serviceName;
		private IBinder binder;
		private Boolean connected;

		public void addListener(ServiceConnection listener) {
			
			if (connectionListeners.add(listener) && connected != null) {
				
				if (connected) {
					listener.onServiceConnected(serviceName, binder);
				} else {
					listener.onServiceDisconnected(serviceName);
				}
			}
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			Log.d(TAG, "onServiceConnected()");
			
			connected = true;
			this.binder = binder;
			serviceName = name;
			
			for (ServiceConnection listener : connectionListeners) {
				listener.onServiceConnected(serviceName, this.binder);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "onServiceDisconnected()");

			connected = false;
			serviceName = name;
			binder = null;
			
			for (ServiceConnection listener : connectionListeners) {
				listener.onServiceDisconnected(serviceName);
			}
		}
	}
	
	public static final int AUTH_ACTIVITY_REQUEST_CODE = 1;
	
	private static final String TAG = TrackListActivity.class.getSimpleName();
	
	private Map<String, AbstractTrackListFragment> eventListeners = 
			new HashMap<String, AbstractTrackListFragment>(2);
	private SyncServiceConnection syncServiceConnection = new SyncServiceConnection();
	private TabHost tabHost;
	private AdController ad;
	
	public SyncServiceConnection getSyncServiceConnection() {
		return syncServiceConnection;
	}

	public void notifyAboutChangedOwner(AbstractTrackListFragment notifier, MusicOwner owner, 
			String ownerName) 
	{
		for (EventListener eventListener : eventListeners.values()) {
			
			if (eventListener == notifier) {
				continue;
			}
			
			eventListener.onOwnerChanged(owner, ownerName);
		}
	}
	
	public void notifyAboutChangedAlbum(AbstractTrackListFragment notifier, long ownerId, 
			String ownerName) 
	{
		for (EventListener eventListener : eventListeners.values()) {
			
			if (eventListener == notifier) {
				continue;
			}
			
			eventListener.onAlbumChanged(ownerId, ownerName);
		}
	}
	
	public void addEventListener(AbstractTrackListFragment eventListener) {
		eventListeners.put(eventListener.getClass().getSimpleName(), eventListener);
	}

	@Override
	public void onBackPressed() {
		Log.d(TAG, "onBackPressed()");
		
		AbstractTrackListFragment activeEventListener = eventListeners.get(
				tabHost.getCurrentTabTag());
		
		if (activeEventListener != null) {
			if (!activeEventListener.onBackPressed()) {
				return;
			}
		}
		
		super.onBackPressed();
	}
	
	@Override
	public boolean onSearchRequested() {
		
		AbstractTrackListFragment activeEventListener = eventListeners.get(
				tabHost.getCurrentTabTag());
		
		if (activeEventListener != null) {
			if (!activeEventListener.onShowSearchView()) {
				return false;
			}
		}
		
		return super.onSearchRequested();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.track_list_activity_menu, menu);
		return true;
	}
	
	public void gotoAuth() {
		Log.d(TAG, "gotoAuth()");
		
		Intent intent = new Intent(this, AuthActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    	startActivityForResult(intent, AUTH_ACTIVITY_REQUEST_CODE);
	}

	@Override
	protected void onStart() {
		super.onStart();
		
		Log.d(TAG, "onStart()");
		
		Analytics.start(this);
		bindService(new Intent(this, SyncService.class), syncServiceConnection, 0);
	}

	@Override
	protected void onStop() {
		super.onStop();
		
		Log.d(TAG, "onStop()");
	
		unbindService(syncServiceConnection);
		Analytics.end(this);
	}
	
	@Override
    protected void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
    	
    	if (intent == null || intent.getAction() == null) {
    		return;
    	}
    	
    	if (intent.getAction().equals(Intent.ACTION_SEARCH)) {
    		AbstractTrackListFragment activeEventListener = 
    				eventListeners.get(tabHost.getCurrentTabTag());
    		
    		if (activeEventListener != null) {
    			activeEventListener.onSearch(intent.getStringExtra(SearchManager.QUERY));
    		}
    	}
    }
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	Log.d(TAG, "onActivityResult(); requestCode=" + requestCode + "; resultCode=" + resultCode);
    	
    	if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
    		
    		if (resultCode == Activity.RESULT_OK) {

    			for (EventListener eventListener : eventListeners.values()) {
    				eventListener.onUserAuthed(Preferences.getInstance().getUserId());
    			}
    			
    		} else if (resultCode == Activity.RESULT_CANCELED) {
    			finish();
    		}
    		
    	}
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.track_list_activity);
		
		initTabs();
		initAds();
		
		if (!Preferences.getInstance().isUserAuthed()) {
			gotoAuth();
		}
	}
	
	@Override
	protected void onDestroy() {
		Log.d(TAG, "onDestroy()");
		
		if (ad != null) {
			ad.destroyAd();
		}
		
		super.onDestroy();
	}
	
	private void initTabs() {
		tabHost = (TabHost) findViewById(android.R.id.tabhost);
		tabHost.setup();
		ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
		TabsAdapter tabsAdapter = new TabsAdapter(this, tabHost, viewPager);
		
		TabSpec remoteToLocalSyncTab = tabHost.newTabSpec(
				RemoteTrackListFragment.class.getSimpleName());
		remoteToLocalSyncTab.setIndicator(createTab(R.drawable.ic_tab_remote_to_local_sync));
		tabsAdapter.addTab(remoteToLocalSyncTab, RemoteTrackListFragment.class, null);
		
		TabSpec localToRemoteSyncTab = tabHost.newTabSpec(
				LocalTrackListFragment.class.getSimpleName());
		localToRemoteSyncTab.setIndicator(createTab(R.drawable.ic_tab_local_to_remote_sync));
		tabsAdapter.addTab(localToRemoteSyncTab, LocalTrackListFragment.class, null);
	}
	
	private View createTab(int iconResourceId) {
		View tab = getLayoutInflater().inflate(R.layout.tab, null);
		((ImageView) tab.findViewById(R.id.icon)).setImageResource(iconResourceId);
		
		return tab;
	}
	
	private void initAds() {
		
		if (!Preferences.getInstance().isAdsEnabled() 
			|| !CommonUtils.isInternetEnabled(getApplicationContext())) 
		{
			return;
		}

		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		int screenWidth = displayMetrics.widthPixels;
		String adId = null;
		int adHeight = 0;

		if (screenWidth >= 720) {
			adId = "384963422";
			adHeight = 90;
		} else if (screenWidth >= 640) {
			adId = "310994609";
			adHeight = 100;
		} else if (screenWidth >= 468) {
			adId = "300178804";
			adHeight = 60;
		} else {
			adId = "530444700";
			adHeight = 50;
		}
		
		final int finalAdHeight = adHeight;
		
		ad = new AdController(this, adId, new AdListener() {
			
			@Override
			public void onAdResumed() {
			}
			
			@Override
			public void onAdProgress() {
			}
			
			@Override
			public void onAdPaused() {
			}
			
			@Override
			public void onAdLoaded() {
				Log.d(TAG, "onAdLoaded()");
				
				LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, 
						LayoutParams.MATCH_PARENT);
				layoutParams.gravity = Gravity.TOP;
				layoutParams.setMargins(0, finalAdHeight, 0, 0);
				findViewById(android.R.id.tabhost).setLayoutParams(layoutParams);
			}
			
			@Override
			public void onAdHidden() {
			}
			
			@Override
			public void onAdFailed() {
				Log.d(TAG, "onAdFailed()");

				runOnUiThread(new Runnable() {
					
					@Override
					public void run() {
						if (ad != null) {
							ad.destroyAd();
						}
					}
				});
			}
			
			@Override
			public void onAdCompleted() {
			}
			
			@Override
			public void onAdClosed() {
			}
			
			@Override
			public void onAdClicked() {
			}
			
			@Override
			public void onAdAlreadyCompleted() {
			}
		});

		ad.loadAd();
	}
}