package com.malykhin.vkmusicsync;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOExceptionWithCause;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;

import com.crittercism.app.Crittercism;
import com.malykhin.io.CreateDirectoryException;
import com.malykhin.io.NotWritableDirectoryException;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.util.CommonUtils;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicDirectoryMapper;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.SyncedAlbum;
import com.malykhin.vkmusicsync.model.track.SyncedTrack;
import com.malykhin.vkmusicsync.model.track.SyncedTrackMapper;
import com.malykhin.vkmusicsync.util.Analytics;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Preferences {
	
	public interface OnMusicDirChangedListener {
		public void onMusicDirChanged(MusicDirectory newMusicDir);
	}
	
	/**
	 * User name
	 */
	public static final String ACCOUNT = "account";
	
	/**
	 * VK access token
	 */
	public static final String ACCESS_TOKEN = "access_token";
	
	/**
	 * App version
	 */
	public static final String VERSION = "version";
	
	public static final String USER_ID = "user_id";
	
	/**
	 * @deprecated
	 */
	public static final String MUSIC_DIRECTORY = "music_dir";
	
	public static final String LOG_ERRORS = "log_errors";
	public static final String ADS_ENABLED = "ads_enabled";
	public static final String WRITE_TO_DEVELOPER = "write_to_developer";
	public static final String DONATE = "donate";
	public static final String SYNC_DONE_SOUND_URI = "sync_done_sound_uri";
	public static final long VK_APP_ID = 2732041;
	public static final boolean DEBUG_MODE_ENABLED = CommonUtils.isAppDebuggable(
			Application.getContext());
	
	private static final String TAG = Preferences.class.getSimpleName();
	private static final Preferences instance = new Preferences();
	
	private SharedPreferences sharedPreferences = 
			PreferenceManager.getDefaultSharedPreferences(Application.getContext());
	private boolean inited = false;
	private Map<OnMusicDirChangedListener, Boolean> onMusicDirChangedListeners = 
			new WeakHashMap<OnMusicDirChangedListener, Boolean>(2);
	
	public static Preferences getInstance() {
		return instance;
	}
	
	/**
	 * 
	 * @throws IllegalStateException If preferences are not inited via {@link #init()}
	 */
	public boolean isAdsEnabled() {
		
		if (!inited) {
			throw new IllegalStateException("Preferences are not inited. See init()");
		}
		
		return sharedPreferences.getBoolean(ADS_ENABLED, true);
	}
	
	public void addOnMusicDirChangedListener(OnMusicDirChangedListener listener) {
		onMusicDirChangedListeners.put(listener, true);
	}
	
	public void deleteOnMusicDirChangedListener(OnMusicDirChangedListener listener) {
		onMusicDirChangedListeners.remove(listener);
	}
	
	/**
	 * 
	 * @throws IllegalStateException If preferences are not inited via {@link #init()}
	 */
	public SharedPreferences getSharedPreferences() {
		
		if (!inited) {
			throw new IllegalStateException("Preferences are not inited. See init()");
		}
		
		return sharedPreferences;
	}
	
	/**
	 * 
	 * @throws UpgradeException If failed to upgrade preferences to new version
	 */
	public void init() throws UpgradeException {

		if (inited) {
			return;
		}

		initLogging();
		upgradeIfNeeded();
		
		inited = true;
	}
	
	/**
	 * 
	 * @throws IllegalStateException If preferences are not inited via {@link #init()}
	 * @return 0 if user is not set
	 */
	public long getUserId() {
		
		if (!inited) {
			throw new IllegalStateException("Preferences are not inited. See init()");
		}
		
//		if (DEBUG_MODE_ENABLED) {
//			return 4150051L;
//		}
		
		return sharedPreferences.getLong(USER_ID, 0);
	}

	/**
	 * 
	 * @throws IllegalStateException If preferences are not inited via {@link #init()}
	 */
	public boolean isUserAuthed() {
		
		if (!inited) {
			throw new IllegalStateException("Preferences are not inited. See init()");
		}
		
		return sharedPreferences.getString(ACCESS_TOKEN, null) != null;
	}
	
	/**
	 * @throws CreateDirectoryException
	 * @throws SQLException If failed to save music dir 
	 */
	public synchronized MusicDirectory getCurrentOrAddDefaultMusicDir(MusicOwner owner) 
			throws CreateDirectoryException, SQLException 
	{
		Log.d(TAG, "getCurrentOrAddDefaultMusicDir()");
		
		MusicDirectory musicDir = MusicDirectoryMapper.getInstance().getOneByOwner(owner);
		
		if (musicDir != null) {
			
			if (!musicDir.getDirectory().exists() 
				&& !musicDir.getDirectory().mkdirs() 
				&& !musicDir.getDirectory().mkdirs()) 
			{
				throw new CreateDirectoryException(musicDir.getDirectory().toString());
			}
			
			return musicDir;
		}
		
		musicDir = new MusicDirectory();
		musicDir.setOwner(owner)
			.setDirectory(MusicDirectory.getDefaultDirectory());
		MusicDirectoryMapper musicDirMapper = MusicDirectoryMapper.getInstance();
		musicDirMapper.beginTransaction();
		
		try {
			musicDirMapper.add(musicDir);
			
			if (!musicDir.getDirectory().exists() 
				&& !musicDir.getDirectory().mkdirs()
				&& !musicDir.getDirectory().mkdirs()) 
			{
				throw new CreateDirectoryException(musicDir.getDirectory().toString());
			}
			
			musicDirMapper.setTransactionSuccessful();
		} finally {
			musicDirMapper.endTransaction();
		}

		return musicDir;
	}
	
	/**
	 * Changes music dir and moves synced tracks there.
	 * 
	 * @return False if not changed, because old directory equals new directory
	 * @throws CreateDirectoryException If failed to create new directory
	 * @throws NotWritableDirectoryException If new directory is not writable
	 * @throws IOException If failed to move some track to new directory
	 */
	public boolean changeMusicDir(MusicDirectory musicDir, File newMusicDir) 
			throws CreateDirectoryException, NotWritableDirectoryException, IOException 
	{
		File oldMusicDir = musicDir.getDirectory();
		
		if (oldMusicDir.equals(newMusicDir)) {
			return false;
		}

		if (!newMusicDir.canWrite()) {
			throw new NotWritableDirectoryException(newMusicDir.toString());
		}
		
		DomainModelCollection<SyncedTrack> syncedTracks = 
				SyncedTrackMapper.getInstance().getAllByOwner(musicDir.getOwner());
		
		// key - old file, value - new file
		Map<File, File> processedTrackFiles = new HashMap<File, File>();
		
		// key - old dir, value - new dir
		Map<File, File> processedAlbumDirs = new HashMap<File, File>();
		
		musicDir.setDirectory(newMusicDir);
		MusicDirectoryMapper musicDirMapper = MusicDirectoryMapper.getInstance();
		musicDirMapper.beginTransaction();
		
		try {
			musicDirMapper.update(musicDir);
			
			if (!newMusicDir.exists() && !newMusicDir.mkdirs()) {
				throw new CreateDirectoryException(newMusicDir.toString());
			}
			
			// move tracks from old dir to new
			for (SyncedTrack syncedTrack : syncedTracks) {
				SyncedAlbum album = syncedTrack.getAlbum();
				File oldTrackFile = null;
				
				if (album == null) {
					oldTrackFile = FileUtils.getFile(oldMusicDir, syncedTrack.getFilename());
				} else {
					oldTrackFile = FileUtils.getFile(oldMusicDir, album.getDirName(), 
							syncedTrack.getFilename());
				}
				
				if (!oldTrackFile.exists()) {
					continue;
				}
	
				File newTrackFile = syncedTrack.getFile();
				
				try {
					
					if (newTrackFile.exists()) {
						
						if (newTrackFile.delete()) {
							FileUtils.moveFile(oldTrackFile, newTrackFile);
						} else {
							FileUtils.copyFile(oldTrackFile, newTrackFile);
							oldTrackFile.delete();
						}
						
					} else {
						FileUtils.moveFile(oldTrackFile, newTrackFile);
					}
					
				} catch (Exception exception) {
					
					// rollback move of files
					for (Entry<File, File> processedTrackFile : processedTrackFiles.entrySet()) {
						File oldProcessedTrackFile = processedTrackFile.getKey();
						File newProcessedTrackFile = processedTrackFile.getValue();
						
						try {
							FileUtils.moveFile(newProcessedTrackFile, oldProcessedTrackFile);
						} catch (Exception rollbackException) {
						}
					}
					
					for (File newProcessedAlbumDir : processedAlbumDirs.values()) {
						newProcessedAlbumDir.delete();
					}
					
					throw new IOExceptionWithCause(exception);
				}
				
				processedTrackFiles.put(oldTrackFile, newTrackFile);
				
				if (album != null) {
					File oldAlbumDir = FileUtils.getFile(oldMusicDir, album.getDirName());
					File newAlbumDir = FileUtils.getFile(newMusicDir, album.getDirName());
					processedAlbumDirs.put(oldAlbumDir, newAlbumDir);
				}
			}
			
			for (File oldProcessedAlbumDir : processedAlbumDirs.keySet()) {
				oldProcessedAlbumDir.delete();
			}
			
			musicDirMapper.setTransactionSuccessful();
		} finally {
			musicDirMapper.endTransaction();
		}
		
		CommonUtils.scanMediaFiles(Application.getContext());
		
		for (OnMusicDirChangedListener listener : onMusicDirChangedListeners.keySet()) {
			listener.onMusicDirChanged(musicDir);
		}
		
		return true;
	}
	
	/**
	 * Upgrades preferences to new version if needed.
	 * 
	 * @throws UpgradeException If upgrade failed
	 */
	private void upgradeIfNeeded() throws UpgradeException {
		int currentAppVersion = 1;
		
		try {
			currentAppVersion = CommonUtils.getAppVersion(Application.getContext());
		} catch (NameNotFoundException exception) {
			Log.e(TAG, null, exception);
		}
		
		int oldAppVersion = sharedPreferences.getInt(VERSION, 21);
		
		if (currentAppVersion <= oldAppVersion) {
			return;
		}
		
		Editor preferenceEditor = sharedPreferences.edit();
		
		Log.d(TAG, "Upgrading...");
		
		switch (oldAppVersion) {
			case 21:
				preferenceEditor.remove(ACCESS_TOKEN);
				oldAppVersion++;
			case 22:
			case 23:
			case 24:
			case 25:
			case 26:
			case 27:
				oldAppVersion++;
			case 28:
				preferenceEditor.remove(ACCESS_TOKEN);
				oldAppVersion++;
			case 29:
			case 30:	
				oldAppVersion++;
		}
		
		preferenceEditor.putInt(VERSION, currentAppVersion);
		
		if (!preferenceEditor.commit()) {
			throw new UpgradeException("Failed to upgrade preferences");
		}
	}

	private void initLogging() {
		Log.setEnabled(DEBUG_MODE_ENABLED);
		
		Crittercism.init(Application.getContext(), "4f30e8e7b093150ce400065a");
		long userId = sharedPreferences.getLong(USER_ID, 0);
		Crittercism.setUsername(String.valueOf(userId));
		
		if (DEBUG_MODE_ENABLED) {
			Analytics.setEnabled(true);
			Crittercism.setOptOutStatus(true);
		} else {
			boolean logErrors = sharedPreferences.getBoolean(LOG_ERRORS, true);
			Analytics.setEnabled(logErrors);
			Crittercism.setOptOutStatus(!logErrors);
		}
		
		Analytics.setUserId(userId);
		
		sharedPreferences.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
			
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				
				if (key.equals(USER_ID)) {
					Analytics.setUserId(sharedPreferences.getLong(USER_ID, 0));
					Crittercism.setUsername(String.valueOf(sharedPreferences.getLong(USER_ID, 0)));
				}
			}
			
		});
	}
	
	private Preferences() {}

}