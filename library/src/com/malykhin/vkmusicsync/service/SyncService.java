package com.malykhin.vkmusicsync.service;

import java.lang.ref.WeakReference;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;

import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Application;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.activity.TrackListActivity;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.Synchronizer;
import com.malykhin.vkmusicsync.model.Synchronizer.OnDoneListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnErrorListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnProcessTrackProgressUpdateListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnRunListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnSearchingDifferenesListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnStartProcessingAlbumListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnStartProcessingTrackListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnStopListener;
import com.malykhin.vkmusicsync.util.Analytics;
import com.malykhin.vkmusicsync.util.ToastUtils;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class SyncService extends IntentService {

	public static class Binder extends android.os.Binder {
		
		private WeakReference<Synchronizer> synchronizerRef;
		
		public Binder(Synchronizer synchronizer) {
			synchronizerRef = new WeakReference<Synchronizer>(synchronizer);
		}
		
		/**
		 * 
		 * @return Null if synchronizer has done his job
		 */
		public Synchronizer getSynchronizer() {
			return synchronizerRef.get();
		}
	}
	
	private class SyncEventListener implements OnRunListener, OnDoneListener,
		OnProcessTrackProgressUpdateListener, OnSearchingDifferenesListener,
		OnStartProcessingTrackListener, OnErrorListener, OnStopListener, 
		OnStartProcessingAlbumListener
	{
		@Override
		public void onError(String errorMsg) {
			showMessage(errorMsg);
		}
	
		@Override
		public void onStartProcessingTrack(String name) {
			updateNotification(name, null, synchronizer.getProgress());
		}
		
		@Override
		public void onStartProcessingAlbum(String title) {
			updateNotification(title, null, synchronizer.getProgress());
		}
	
		@Override
		public void onSearchingDifferences() {
			updateNotification(getString(R.string.searching_differences), null, 
					synchronizer.getProgress());
		}
	
		@Override
		public void onProcessTrackProgressUpdate(String trackTitle, long processedBytesCount, 
				long totalBytesCount) 
		{
			updateNotification(
					trackTitle, 
					(int) (((float) processedBytesCount / totalBytesCount) * 100), 
					synchronizer.getProgress()
			);
		}
	
		@Override
		public void onRun() {
			startForeground(NOTIFICATION, createNotification(R.string.preparing));
		}

		@Override
		public void onStop() {
			playSyncDoneSoundEnabled = false;
		}

		@Override
		public void onDone(boolean anyChanges) {
			showMessage(getString(R.string.synchronization_done));
			
			if (playSyncDoneSoundEnabled) {
				playSyncDoneSound();
			}
		}

	}

	public static final String START_INTENT_EXTRA_OWNER_ID = 
			Application.getContext().getPackageName() + ".owner_id";
	public static final String START_INTENT_EXTRA_OWNER_GROUP = 
			Application.getContext().getPackageName() + ".owner_group";
	
	private static final String TAG = SyncService.class.getSimpleName();
	private static final int NOTIFICATION = 1;

	private Synchronizer synchronizer;
	private Notification notification;
	private Handler handler;
	private SyncEventListener syncEventListener;
	private boolean playSyncDoneSoundEnabled;
	private MusicOwner owner;
	
	public SyncService() {
		super(SyncService.class.getSimpleName());
		
		handler = new Handler();
		syncEventListener = new SyncEventListener();
		synchronizer = new Synchronizer(Application.getContext());
		synchronizer.addOnProcessTrackProgressUpdateListener(syncEventListener);
		synchronizer.setOnErrorListener(syncEventListener);
		synchronizer.addOnRunListener(syncEventListener);
		synchronizer.addOnSearchingDifferencesListener(syncEventListener);
		synchronizer.addOnStartProcessingTrackListener(syncEventListener);
		synchronizer.setOnStopListener(syncEventListener);
		synchronizer.addOnDoneListener(syncEventListener);
		owner = new MusicOwner();
		Analytics.start(this);
		setIntentRedelivery(true);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand()");

		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "onBind()");
		
		return new Binder(synchronizer);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		Log.d(TAG, "onDestroy()");
		
		Analytics.end(this);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(TAG, "onHandleIntent()");

		if (synchronizer == null) {
			return;
		}

		playSyncDoneSoundEnabled  = true;
		owner.setId(intent.getLongExtra(START_INTENT_EXTRA_OWNER_ID, 0))
			.setGroup(intent.getBooleanExtra(START_INTENT_EXTRA_OWNER_GROUP, false));
		synchronizer.run(owner);
		synchronizer = null;
		stopSelf();
	}
	
	private void playSyncDoneSound() {

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
				Application.getContext());
		String soundUri = preferences.getString(Preferences.SYNC_DONE_SOUND_URI, null);
		
		if (soundUri == null) {
			return;
		}
		
		final Ringtone syncDoneSound = RingtoneManager.getRingtone(Application.getContext(), 
				Uri.parse(soundUri));
		
		if (syncDoneSound == null) {
			return;
		}
		
		handler.post(new Runnable() {
			
			@Override
			public void run() {
				syncDoneSound.play();
			}
			
		});
	}
	
	private Notification createNotification(int messageResourceId) {
		notification = new Notification(
				R.drawable.ic_stat_sync, 
				getResources().getString(R.string.synchronizing), 
				System.currentTimeMillis()
		);
		Intent intent = new Intent(this, TrackListActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
				PendingIntent.FLAG_UPDATE_CURRENT);

		setNotificationContent(notification, getString(messageResourceId), null, 0);
		notification.contentIntent = pendingIntent;
		
		notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT | 
				Notification.FLAG_ONLY_ALERT_ONCE;
		
		return notification;
	}

	/**
	 * 
	 * @param message
	 * @param currentTrackProgress If null, then progress bar for current track will be hidden
	 * @param overallProgress Overall progress of sync
	 */
	private void updateNotification(String message, Integer currentTrackProgress, 
			int overallProgress
	) {
		setNotificationContent(notification, message, currentTrackProgress, overallProgress);
		NotificationManager notifier = (NotificationManager) getSystemService(
				Context.NOTIFICATION_SERVICE);
		notifier.notify(NOTIFICATION, notification);
	}

	private void setNotificationContent(Notification notification, String message, 
			Integer currentTrackProgress, int overallProgress
	) {
		RemoteViews contentView = new RemoteViews(getPackageName(), 
				R.layout.sync_service_notification);
		contentView.setTextViewText(R.id.title_text, 
				getString(R.string.synchronizing) + " (" + overallProgress + "%)");
		contentView.setTextViewText(R.id.content_text, message);
		
		if (currentTrackProgress == null) {
			contentView.setViewVisibility(R.id.progress_bar_container, View.INVISIBLE);
		} else {
			contentView.setViewVisibility(R.id.progress_bar_container, View.VISIBLE);
			contentView.setProgressBar(R.id.progress_bar, 100, currentTrackProgress, false);
		}
		
		notification.contentView = contentView;
	}
	
	private void showMessage(final String msg) {
		handler.post(new Runnable() {
			
			@Override
			public void run() {
				ToastUtils.show(SyncService.this, msg);
			}
			
		});
	}
}
