package com.malykhin.vkmusicsync.activity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.malykhin.orm.AbstractDomainModel;
import com.malykhin.orm.DomainModelMapper;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicDirectoryMapper;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.Synchronizer;
import com.malykhin.vkmusicsync.model.scanner.MusicLibraryScanner.MusicLibraryScannerResult;
import com.malykhin.vkmusicsync.model.track.LocalToRemoteSyncableTrack;
import com.malykhin.vkmusicsync.model.track.LocalToRemoteSyncableTrackCollection;
import com.malykhin.vkmusicsync.model.track.LocalToRemoteSyncableTrackMapper;
import com.malykhin.vkmusicsync.model.track.NotSyncedLocalTrack;

/**
 * 
 * @author Andrey Malykhin
 */
public class LocalTrackListFragment extends AbstractTrackListFragment
{
	protected static class DeleteTrackTask extends AbstractDeleteTrackTask {

		public DeleteTrackTask(AbstractTrackListFragment fragment) {
			super(fragment);
		}

		@Override
		protected boolean doDelete(TrackListItem track) {
			
			if (track.file == null) {
				return false;
			}

			LocalToRemoteSyncableTrackMapper localToRemoteSyncableTrackMapper = 
					LocalToRemoteSyncableTrackMapper.getInstance();
			localToRemoteSyncableTrackMapper.beginTransaction();
			
			try {
				((AbstractTrackListFragment) fragment).deleteSyncableTrack(track);
				
				if (!track.file.delete()) {
					return false;
				}
				
				localToRemoteSyncableTrackMapper.setTransactionSuccessful();
			} finally {
				localToRemoteSyncableTrackMapper.endTransaction();
			}

			TrackListAdapter trackListAdapter = 
					((AbstractTrackListFragment) fragment).getListAdapter();
			trackListAdapter.deleteItem(track);
			
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
			Set<NotSyncedLocalTrack> notSyncedLocalTracks = 
					musicLibraryScannerResult.trackScannerResult.notSyncedLocalEntities;
			LocalToRemoteSyncableTrackCollection localToRemoteSyncableTracks = 
        			LocalToRemoteSyncableTrackMapper.getInstance().getAllByOwner(owner);
			ArrayList<TrackListItem> items = 
					new ArrayList<TrackListItem>(notSyncedLocalTracks.size());
			File musicDir = 
					MusicDirectoryMapper.getInstance().getOneByOwner(owner).getDirectory();
			
			for (NotSyncedLocalTrack notSyncedLocalTrack : notSyncedLocalTracks) {
				
				TrackListItem item = new TrackListItem(
						0,
						null,
						notSyncedLocalTrack.getFullTitle(), 
						notSyncedLocalTrack.getFile(),
						notSyncedLocalTrack.isSyncable(musicDir, localToRemoteSyncableTracks),
						null,
						0,
						notSyncedLocalTrack.getAlbum(musicDir)
				);
				
				items.add(item);
			}

			return items;
		}
		
		@Override
		protected String getLogTag() {
			return LocalTrackListFragment.class.getSimpleName() + "." + 
					LoadTrackListTask.class.getSimpleName();
		}
	}

	protected static class LocalTrackListAdapter extends TrackListAdapter {

		public LocalTrackListAdapter(AbstractTrackListFragment fragment) {
			super(fragment);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View itemView = null;
			TrackListItemViewHolder itemViewHolder = null;
			
			if (convertView == null) {
				itemView = View.inflate(fragment.getActivity(), R.layout.local_track_list_item, 
						null);
				itemViewHolder = new TrackListItemViewHolder();
				itemViewHolder.titleText = (TextView)itemView.findViewById(R.id.title_text);
				itemViewHolder.checkbox = (CheckBox)itemView.findViewById(R.id.checkbox);
				itemView.setTag(itemViewHolder);
			} else {
				itemView = convertView;
				itemViewHolder = (TrackListItemViewHolder) itemView.getTag();
			}
			
			TrackListItem item = getItem(position);
			
			itemViewHolder.titleText.setText(item.title);

			itemViewHolder.checkbox.setTag(item);
			itemViewHolder.checkbox.setOnCheckedChangeListener(onCheckedChangeListener);
			itemViewHolder.checkbox.setChecked(item.syncable);

			return itemView;
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		menu.findItem(R.id.copy_to_me).setVisible(false);
	}
	
	@Override
	protected boolean setOwner(MusicOwner owner, String name, boolean silently) {
		setEmptyListMsg(owner);
		return super.setOwner(owner, name, silently);
	}

	@Override
	protected void deleteSyncableTrack(TrackListItem track) {
		File musicDir = MusicDirectoryMapper.getInstance().getOneByOwner(owner).getDirectory();
		File albumDir = track.file.getParentFile();
		String album = albumDir.equals(musicDir) ? null : albumDir.getName();
		LocalToRemoteSyncableTrackMapper.getInstance().deleteByOwnerAndAlbumAndFilename(owner, 
				album, track.file.getName());
	}

	@Override
	protected DomainModelMapper<LocalToRemoteSyncableTrack> getSyncableTrackMapper() {
		return LocalToRemoteSyncableTrackMapper.getInstance();
	}

	@Override
	protected AbstractDomainModel createSyncableTrack(TrackListItem track) {
		LocalToRemoteSyncableTrack localToRemoteSyncableTrack = new LocalToRemoteSyncableTrack();
		MusicDirectory musicDir = MusicDirectoryMapper.getInstance().getOneByOwner(owner);
		File albumDir = track.file.getParentFile();
		String album = null;
		
		if (musicDir != null && !musicDir.getDirectory().equals(albumDir)) {
			album = albumDir.getName(); 
		}
		
		localToRemoteSyncableTrack.setOwner(owner)
			.setFilename(track.file.getName())
			.setAlbum(album);
		
		return localToRemoteSyncableTrack;
	}

	@Override
	protected AbstractLoadTrackListTask createLoadTrackListTask() {
		return new LoadTrackListTask((TrackListActivity) getActivity(), this);
	}

	@Override
	protected String getLogTag() {
		return LocalTrackListFragment.class.getSimpleName();
	}
	
	@Override
	protected LocalTrackListAdapter createTrackListAdapter() {
		return new LocalTrackListAdapter(this);
	}

	@Override
	protected DeleteTrackTask createDeleteTrackTask() {
		return new DeleteTrackTask(this);
	}

	@Override
	protected Collection<TrackListItem> filterTracksByAlbum(List<TrackListItem> tracks, 
			long albumId, String albumTitle) 
	{
		List<TrackListItem> filteredTracks = new LinkedList<TrackListItem>();
		
		if (albumId != 0 && albumTitle == null) {
			return filteredTracks;
		}
		
		albumTitle = Synchronizer.generateAlbumDirName(albumTitle);
		
		for (TrackListItem track : tracks) {
			
			if (TextUtils.equals(track.albumTitle, albumTitle)) {
				filteredTracks.add(track);
			}
		}
		
		return filteredTracks;
	}
	
	private void setEmptyListMsg(MusicOwner owner) {
		int emptyListMessageId;
		
		if (!owner.isGroup() && owner.getId() == Preferences.getInstance().getUserId()) {
			emptyListMessageId = R.string.empty_local_track_list;
		} else {
			emptyListMessageId = R.string.sync_in_this_direction_is_possible_only_for_your_music;
		}

		((TextView) getView().findViewById(android.R.id.empty)).setText(emptyListMessageId);
	}
}