package com.malykhin.vkmusicsync.activity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;

import com.malykhin.app.PausableAsyncTask;
import com.malykhin.app.ProgressDialogUtils;
import com.malykhin.gateway.vk.Album;
import com.malykhin.gateway.vk.Track;
import com.malykhin.orm.AbstractDomainModel;
import com.malykhin.orm.DomainModelCollection;
import com.malykhin.orm.DomainModelMapper;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.VkGatewayHelper;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.scanner.MusicLibraryScanner.MusicLibraryScannerResult;
import com.malykhin.vkmusicsync.model.track.RemoteToLocalSyncableTrack;
import com.malykhin.vkmusicsync.model.track.RemoteToLocalSyncableTrackMapper;
import com.malykhin.vkmusicsync.model.track.SyncedTrack;
import com.malykhin.vkmusicsync.model.track.SyncedTrackMapper;
import com.malykhin.vkmusicsync.util.ToastUtils;

/**
 * @author Andrey Malykhin
 */
public class RemoteTrackListFragment extends AbstractTrackListFragment
{
	protected static class CopyTrackToLoggedUserTask 
		extends PausableAsyncTask<Long, Void, Boolean> 
	{
		private static final String TAG = CopyTrackToLoggedUserTask.class.getSimpleName();
		private int progressDialogRequestId;
	
		public static boolean canCopyTo(MusicOwner owner) {
			return owner.isGroup() || Preferences.getInstance().getUserId() != owner.getId();
		}
		
		public CopyTrackToLoggedUserTask(RemoteTrackListFragment fragment) {
			super(fragment);
		}
	
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			progressDialogRequestId = ProgressDialogUtils.show(fragment.getFragmentManager(), 
					fragment.getString(R.string.copying), fragment);
		}
		
		@Override
		protected Boolean doInBackground(Long... params) {
			Log.d(TAG, "doInBackground()");
			
			MusicOwner owner = ((RemoteTrackListFragment) fragment).owner;
			long trackId = params[0];
			boolean isCopiedSuccessfully = true;
			
			try {
				VkGatewayHelper.getGateway().copyTrack(trackId, owner.getId(), owner.isGroup());
			} catch (Exception exception) {
				Log.e(TAG, null, exception);
				isCopiedSuccessfully = false;
			}
			
			doPause();
			
			return isCopiedSuccessfully;
		}
	
		@Override
		protected void onPostExecute(Boolean isAddedSuccessfully) {
			super.onPostExecute(isAddedSuccessfully);
			
			ProgressDialogUtils.hide(fragment.getFragmentManager(), progressDialogRequestId, 
					fragment);
			
			if (!isAddedSuccessfully) {
				ToastUtils.show(fragment.getActivity(), 
						R.string.error_while_copying_track_to_logged_user);
				return;
			}
			
			ToastUtils.show(fragment.getActivity(), R.string.track_copied);
		}
	}
	
	protected static class DeleteTrackTask extends AbstractDeleteTrackTask {

		private static final String TAG = DeleteTrackTask.class.getSimpleName();
		
		public DeleteTrackTask(AbstractTrackListFragment fragment) {
			super(fragment);
		}

		@Override
		protected boolean doDelete(TrackListItem track) {
			
			SyncedTrackMapper syncedTrackMapper = SyncedTrackMapper.getInstance();
			MusicOwner owner = ((AbstractTrackListFragment) fragment).owner;
			boolean isLoggedUserOwner = 
					!owner.isGroup() && owner.getId() == Preferences.getInstance().getUserId();
			syncedTrackMapper.beginTransaction();
			
			try {
				syncedTrackMapper.delete(track.id);
				RemoteToLocalSyncableTrackMapper.getInstance().delete(track.id);
				
				if (isLoggedUserOwner) {
					boolean isTrackSuccessfullyDeleted = 
							VkGatewayHelper.getGateway().deleteTrack(owner.getId(), track.id);
					
					if (!isTrackSuccessfullyDeleted) {
						return false;
					}
				}
				
				if (track.file != null) {
					track.file.delete();
				}
				
				syncedTrackMapper.setTransactionSuccessful();
			} catch (Exception exception) {
				Log.e(TAG, null, exception);
				return false;
			} finally {
				syncedTrackMapper.endTransaction();
			}
			
			if (isLoggedUserOwner) {
				TrackListAdapter trackListAdapter = 
						((AbstractTrackListFragment) fragment).getListAdapter();
				trackListAdapter.deleteItem(track);
			} else {
				track.file = null;
				track.syncable = false;
			}
			
			return true;
		}
		
	}
	
	protected static class LoadTrackListTask extends AbstractLoadTrackListTask {

		public LoadTrackListTask(TrackListActivity activity, AbstractTrackListFragment fragment) {
			super(activity, fragment);
		}

		@Override
		protected ArrayList<TrackListItem> getTrackListItems(MusicOwner owner,
				MusicLibraryScannerResult musicLibraryScannerResult) 
		{
			DomainModelCollection<SyncedTrack> syncedTracks = 
					musicLibraryScannerResult.trackScannerResult.syncedEntities;
    		DomainModelCollection<RemoteToLocalSyncableTrack> remoteToLocalSyncableTracks = 
        			RemoteToLocalSyncableTrackMapper.getInstance().getAllByOwner(owner);
    		ArrayList<TrackListItem> items = new ArrayList<TrackListItem>(
    				musicLibraryScannerResult.trackScannerResult.remoteEntities.size());
			Map<Long, Album> remoteAlbums = new HashMap<Long, Album>(
					musicLibraryScannerResult.albumScannerResult.remoteEntities.size()); 
    		
    		for (Album remoteAlbum : musicLibraryScannerResult.albumScannerResult.remoteEntities) {
    			remoteAlbums.put(remoteAlbum.id, remoteAlbum);
    		}
    		
			for (Track remoteTrack : musicLibraryScannerResult.trackScannerResult.remoteEntities) {
				SyncedTrack syncedTrack = syncedTracks.get(remoteTrack.id);
				Album remoteAlbum = remoteAlbums.get(remoteTrack.albumId);
				boolean isTrackSyncable = remoteToLocalSyncableTracks.has(remoteTrack.id);
				
				TrackListItem item = new TrackListItem(
						remoteTrack.id,
						remoteTrack.artist,
						remoteTrack.title,
						syncedTrack == null ? null : syncedTrack.getFile(),
						isTrackSyncable,
						remoteTrack.url,
						remoteTrack.albumId == null ? 0 : remoteTrack.albumId,
						remoteAlbum == null ? null : remoteAlbum.title
				);
				
				items.add(item);
			}

			return items;
		}

		@Override
		protected String getLogTag() {
			return RemoteTrackListFragment.class.getSimpleName() + "." + 
					LoadTrackListTask.class.getSimpleName();
		}

	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		menu.findItem(R.id.copy_to_me).setVisible(CopyTrackToLoggedUserTask.canCopyTo(owner));
	}

	protected void doOnContextItemSelected(int itemId, TrackListItem track) {
		
		if (itemId == R.id.copy_to_me) {
			copyTrackToLoggedUser(track.id);
		}
	}
	
	protected void copyTrackToLoggedUser(long trackId) {
		new CopyTrackToLoggedUserTask(this).execute(trackId);
	}

	@Override
	protected void deleteSyncableTrack(TrackListItem track) {
		RemoteToLocalSyncableTrackMapper.getInstance().delete(track.id);
	}

	@Override
	protected DomainModelMapper<RemoteToLocalSyncableTrack> getSyncableTrackMapper() {
		return RemoteToLocalSyncableTrackMapper.getInstance();
	}

	@Override
	protected AbstractDomainModel createSyncableTrack(TrackListItem track) {
		RemoteToLocalSyncableTrack remoteToLocalSyncableTrack = new RemoteToLocalSyncableTrack();
		remoteToLocalSyncableTrack.setId(track.id)
			.setOwner(owner);
		
		return remoteToLocalSyncableTrack;
	}

	@Override
	protected AbstractLoadTrackListTask createLoadTrackListTask() {
		return new LoadTrackListTask((TrackListActivity) getActivity(), this);
	}

	@Override
	protected String getLogTag() {
		return RemoteTrackListFragment.class.getSimpleName();
	}

	@Override
	protected AbstractDeleteTrackTask createDeleteTrackTask() {
		return new DeleteTrackTask(this);
	}

	@Override
	protected Collection<TrackListItem> filterTracksByAlbum(List<TrackListItem> tracks, 
			long albumId, String albumTitle) 
	{
		List<TrackListItem> filteredTracks = new LinkedList<TrackListItem>();

		if (albumId == 0 && albumTitle != null) {
			return filteredTracks;
		}
		
		for (TrackListItem track : tracks) {
			
			if (track.albumId == albumId) {
				filteredTracks.add(track);
			}
		}
		
		return filteredTracks;
	}

}