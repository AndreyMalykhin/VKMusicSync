package com.malykhin.vkmusicsync.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;

import android.content.Context;

import com.flurry.android.FlurryAgent;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Analytics {

	public static void setEnabled(boolean enabled) {
		FlurryAgent.setLogEnabled(enabled);
	}
	
	public static void setUserId(long userId) {
		FlurryAgent.setUserId(String.valueOf(userId));
	}
	
	public static void start(Context context) {
		
		if (context == null) {
			return;
		}
		
		FlurryAgent.onStartSession(context, "I5KHRI98HMDLZH7FFJK8");
	}

	public static void logException(Exception exception) {
		FlurryAgent.onError("caught", ExceptionUtils.getStackTrace(exception), 
				exception.getClass().getSimpleName());
	}
	
	public static void logSearchTracks() {
		FlurryAgent.logEvent("search_tracks");
	}
	
	public static void logDisableAdsCheat() {
		FlurryAgent.logEvent("disable_ads_cheat");
	}
	
	public static void logSyncStop() {
		FlurryAgent.logEvent("sync_stop");
	}
	
	public static void end(Context context) {
		
		if (context == null) {
			return;
		}
		
		FlurryAgent.onEndSession(context);
	}

	public static void logSync(
			int remoteTracksToAddLocally, 
			int remoteTracksToUpdateLocally, 
			int localTracksToAddRemotely, 
			int remoteAlbumsToAddLocally, 
			int remoteAlbumsToUpdateLocally, 
			int localAlbumsToAddRemotely,
			boolean isSyncingMusicOfFriend,
			boolean isSyncingMusicOfGroup) 
	{
		Map<String, String> params = new HashMap<String, String>(7);
		params.put("remote_tracks_to_add_locally", formatCount(remoteTracksToAddLocally));
		params.put("remote_tracks_to_update_locally", formatCount(remoteTracksToUpdateLocally));
		params.put("local_tracks_to_add_remotely", formatCount(localTracksToAddRemotely));
		params.put("remote_albums_to_add_locally", formatCount(remoteAlbumsToAddLocally));
		params.put("remote_albums_to_update_locally", formatCount(remoteAlbumsToUpdateLocally));
		params.put("local_albums_to_add_remotely", formatCount(localAlbumsToAddRemotely));
		params.put("is_music_of_friend", String.valueOf(isSyncingMusicOfFriend));
		params.put("is_music_of_group", String.valueOf(isSyncingMusicOfGroup));
		FlurryAgent.logEvent("sync", params);
	}
	
	public static void logMusicLibraryScan(
			int remoteTracks, 
			int syncedTracks, 
			int notSyncedLocalTracks, 
			int remoteAlbums, 
			int syncedAlbums, 
			int notSyncedLocalAlbums) 
	{
		Map<String, String> params = new HashMap<String, String>(6);
		params.put("remote_tracks", formatCount(remoteTracks));
		params.put("synced_tracks", formatCount(syncedTracks));
		params.put("not_synced_local_tracks", formatCount(notSyncedLocalTracks));
		params.put("remote_albums", formatCount(remoteAlbums));
		params.put("synced_albums", formatCount(syncedAlbums));
		params.put("not_synced_local_albums", formatCount(notSyncedLocalAlbums));
		FlurryAgent.logEvent("music_library_scan", params);
	}
	
	public static void logPlayTrack() {
		FlurryAgent.logEvent("play_track");
	}

	public static void logDeleteTrack() {
		FlurryAgent.logEvent("delete_track");
	}

	public static void logMusicDirChange() {
		FlurryAgent.logEvent("music_dir_change");
	}

	private static String formatCount(int count) {
		
		if (count >= 10000) {
			return "10000+";
		} else if (count >= 1000) {
			return "1000-10000";
		} else if (count >= 100) {
			return "100-1000";
		} else if (count >= 10) {
			return "10-100";
		} else if (count >= 1) {
			return "1-10";
		}
		
		return "0";
	}
}
