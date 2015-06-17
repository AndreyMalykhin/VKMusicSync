package com.malykhin.vkmusicsync.activity;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.ToStringBuilder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.MenuItem;
import com.malykhin.app.DialogFragment;
import com.malykhin.app.PausableAsyncTask;
import com.malykhin.app.ProgressDialogUtils;
import com.malykhin.gateway.vk.InvalidAccessTokenException;
import com.malykhin.gateway.vk.VkGatewayException;
import com.malykhin.orm.AbstractDomainModel;
import com.malykhin.orm.DomainModelMapper;
import com.malykhin.util.CommonUtils;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Application;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.Preferences.OnMusicDirChangedListener;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.Synchronizer;
import com.malykhin.vkmusicsync.model.Synchronizer.OnDoneListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnProcessTrackProgressUpdateListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnRunListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnSearchingDifferenesListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnStartProcessingAlbumListener;
import com.malykhin.vkmusicsync.model.Synchronizer.OnStartProcessingTrackListener;
import com.malykhin.vkmusicsync.model.Synchronizer.Status;
import com.malykhin.vkmusicsync.model.scanner.MusicLibraryScanner;
import com.malykhin.vkmusicsync.model.scanner.MusicLibraryScanner.MusicLibraryScannerResult;
import com.malykhin.vkmusicsync.model.track.LocalToRemoteSyncableTrackMapper;
import com.malykhin.vkmusicsync.model.track.RemoteToLocalSyncableTrackMapper;
import com.malykhin.vkmusicsync.service.SyncService;
import com.malykhin.vkmusicsync.service.SyncService.Binder;
import com.malykhin.vkmusicsync.util.Analytics;
import com.malykhin.vkmusicsync.util.ToastUtils;
import com.malykhin.widget.ProgressBar;

/**
 * TODO refactor: extract SearchManager inner class
 * TODO fix bug: progress bar is not hiding sometimes
 * 
 * @author Andrey Malykhin
 *
 */
public abstract class AbstractTrackListFragment extends SherlockListFragment implements 
	TrackListActivity.EventListener, OnMusicDirChangedListener
{
	protected static class DeleteTrackDlg extends DialogFragment {
		
		private AbstractTrackListFragment trackListFragment;
		private TrackListItem track;
		
		public DeleteTrackDlg() {
			super();
		}

		public DeleteTrackDlg(AbstractTrackListFragment trackListFragment, 
				TrackListItem track) 
		{
			this.trackListFragment = trackListFragment;
			this.track = track;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			
			return new AlertDialog.Builder(getActivity())
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener(){

					@Override
					public void onClick(DialogInterface dialog, int which) {
						DeleteTrackDlg.this.dismissAllowingStateLoss();
					}
					
				})
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener(){

					@Override
					public void onClick(DialogInterface dialog, int which) {
						trackListFragment.deleteTrack(track);
						DeleteTrackDlg.this.dismissAllowingStateLoss();
					}
					
				})
				.setMessage(R.string.delete_track_msg)
				.setTitle(R.string.delete_track_title)
				.create();
		}
	}
	
	protected static class TrackListItemViewHolder {
		public TextView artistText;
		public TextView titleText;
		public CheckBox checkbox;
		public ImageView statusImg;
	}

	protected static class TrackListItem {
	
		public final long id;
		public final String artist;
		public final String title;
		public final String url;
		public File file;
		public boolean syncable = false;
		public long albumId;
		public String albumTitle;
	
		/**
		 * 
		 * @param id 0 means no ID
		 * @param artist Can be null
		 * @param title
		 * @param file Can be null
		 * @param isSyncable
		 * @param url Can be null
		 * @param albumId 0 means no album
		 * @param albumTitle Can be null
		 */
		public TrackListItem(long id, String artist, String title, File file, boolean isSyncable, 
				String url, long albumId, String albumTitle) 
		{
			this.artist = artist;
			this.title = title;
			this.id = id;
			this.file = file;
			this.url = url;
			this.albumId = albumId;
			this.albumTitle = albumTitle;
			syncable = isSyncable;
		}
		
		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this);
		}
	}
	
	protected abstract static class AbstractDeleteTrackTask 
		extends PausableAsyncTask<TrackListItem, Void, Boolean>{

		private int progressDialogRequestId;
		
		public static boolean canBeDeleted(TrackListItem track, MusicOwner owner) {
			return track.file != null 
					|| (!owner.isGroup() && owner.getId() == Preferences.getInstance().getUserId());
		}
		
		public AbstractDeleteTrackTask(AbstractTrackListFragment fragment) {
			super(fragment);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			progressDialogRequestId = ProgressDialogUtils.show(fragment.getFragmentManager(), 
					fragment.getString(R.string.deleting), fragment);
		}
		
		@Override
		protected Boolean doInBackground(TrackListItem... params) {
			TrackListItem track = params[0];
			Boolean isDeletedSuccessfully = doDelete(track);
			
			if (isDeletedSuccessfully) {
				((AbstractTrackListFragment) fragment).unfilteredTrackListItems.remove(track);
			}
			
			doPause();
			
			return isDeletedSuccessfully;
		}

		@Override
		protected void onPostExecute(Boolean isDeletedSuccessfully) {
			super.onPostExecute(isDeletedSuccessfully);
			
			ProgressDialogUtils.hide(fragment.getFragmentManager(), progressDialogRequestId, 
					fragment);
			
			if (!isDeletedSuccessfully) {
				ToastUtils.show(fragment.getActivity(), R.string.error_while_deleting_track);
				return;
			}
			
			TrackListAdapter trackListAdapter = 
					((AbstractTrackListFragment) fragment).getListAdapter();
			trackListAdapter.notifyDataSetChanged();
		}

		protected abstract boolean doDelete(TrackListItem track);
	}
	
	protected abstract static class AbstractLoadTrackListTask 
		extends PausableAsyncTask<MusicOwner, Void, ArrayList<TrackListItem>> 
	{
		protected final String TAG = getLogTag();
		private boolean running = false;

		public AbstractLoadTrackListTask(TrackListActivity activity, 
				AbstractTrackListFragment fragment) 
		{
			super(activity, fragment);
		}

		public boolean isRunning() {
			return running;
		}

		/**
		 * Must be called when instance is not needed anymore.
		 */
		public void cleanup() {
			Log.d(TAG, "cleanup()");
			
			MusicLibraryScanner.getInstance().clearCache();
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			Log.d(TAG, "onPreExecute()");

			running = true;
			getFragment().updateOptionsMenu();
			getFragment().trackListProgressBar.show(activity);
		}
		
		protected ArrayList<TrackListItem> doInBackground(MusicOwner... params) {
			Log.d(TAG, "doInBackground()");

			ArrayList<TrackListItem> trackListItems = null;
			MusicOwner owner = params[0];
			MusicDirectory musicDir = null;
			
        	try {
    			musicDir = Preferences.getInstance().getCurrentOrAddDefaultMusicDir(owner);
    		} catch (Exception exception) {
    			Log.e(TAG, null, exception);
    			
    			ToastUtils.show(getFragment().getActivity(), 
    					R.string.error_while_creating_directory_for_music);
    		}
	        
        	if (musicDir != null) {
        		MusicLibraryScannerResult musicLibraryScannerResult = null;
        		
	        	try {
	        		musicLibraryScannerResult = 
		        			MusicLibraryScanner.getInstance().scan(musicDir);
	        	} catch (VkGatewayException exception) {
					Log.e(TAG, null, exception);

					if (exception.getVkException() != null 
						&& exception.getVkException() instanceof InvalidAccessTokenException) 
					{
						((TrackListActivity) activity).gotoAuth();
					}
					else
					{
						ToastUtils.show(getFragment().getActivity(), 
							R.string.error_while_communicating_with_vkontake);
					}
				}
		        
		        if (musicLibraryScannerResult != null) {
		        	trackListItems = getTrackListItems(owner, musicLibraryScannerResult);
		        }
        	}
    		
        	doPause();
        	
        	return trackListItems;
		}
		
		@Override
		protected void onPostExecute(ArrayList<TrackListItem> trackListItems) {
			super.onPostExecute(trackListItems);
			
			Log.d(TAG, "onPostExecute()");
	
			getFragment().unfilteredTrackListItems = trackListItems;
			getFragment().applyTrackFilters();
			
			running = false;
			
			getFragment().updateOptionsMenu();
			getFragment().trackListProgressBar.hide(activity);
		}
		
		protected AbstractTrackListFragment getFragment() {
			return (AbstractTrackListFragment) fragment;
		}

		protected abstract String getLogTag();
		
		protected abstract ArrayList<TrackListItem> getTrackListItems(MusicOwner owner, 
				MusicLibraryScannerResult musicLibraryScannerResult);
	}
	
	protected static class TrackListAdapter extends BaseAdapter {

		protected OnCheckedChangeListener onCheckedChangeListener = new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				TrackListItem trackListItem = (TrackListItem) buttonView.getTag();
				
				if (!fragment.setTrackSyncable(trackListItem, isChecked)) {
					buttonView.setChecked(!isChecked);
					ToastUtils.show(fragment.getActivity(), R.string.error_while_saving_settings);
				}
			}
			
		};
		
		protected AbstractTrackListFragment fragment;
		protected ArrayList<TrackListItem> items;

		public TrackListAdapter(AbstractTrackListFragment fragment) {
			super();
			this.fragment = fragment;
		}
		
		/**
		 * 
		 * @return True if successfully
		 */
		public synchronized boolean deleteItem(TrackListItem item) {
			return items.remove(item);
		}
		
		/**
		 * 
		 * @param items Can be null
		 */
		public synchronized void setItems(ArrayList<TrackListItem> items) {
			this.items = items;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View itemView = null;
			TrackListItemViewHolder itemViewHolder = null;
			
			if (convertView == null) {
				itemView = View.inflate(fragment.getActivity(), R.layout.track_list_item, null);
				itemViewHolder = new TrackListItemViewHolder();
				itemViewHolder.artistText = (TextView)itemView.findViewById(R.id.artist_text);
				itemViewHolder.titleText = (TextView)itemView.findViewById(R.id.title_text);
				itemViewHolder.checkbox = (CheckBox)itemView.findViewById(R.id.checkbox);
				itemViewHolder.statusImg = (ImageView)itemView.findViewById(R.id.status_img);
				itemView.setTag(itemViewHolder);
			} else {
				itemView = convertView;
				itemViewHolder = (TrackListItemViewHolder) itemView.getTag();
			}
			
			TrackListItem item = getItem(position);
			
			itemViewHolder.artistText.setText(item.artist);
			itemViewHolder.titleText.setText(item.title);

			itemViewHolder.checkbox.setTag(item);
			itemViewHolder.checkbox.setOnCheckedChangeListener(onCheckedChangeListener);
			itemViewHolder.checkbox.setChecked(item.syncable);

			itemViewHolder.statusImg.setBackgroundResource(item.file == null ? 0 : 
				R.drawable.ic_exists_locally);
			
			return itemView;
		}

		/**
		 * 
		 * @return Null if items were not set
		 */
		public synchronized ArrayList<TrackListItem> getItems() {
			return items;
		}
		
		@Override
		public synchronized int getCount() {
			
			if (items == null) {
				return 0;
			}
			
			return items.size();
		}

		/**
		 * @return Null if items were not set
		 */
		@Override
		public synchronized TrackListItem getItem(int position) {
			
			if (items == null) {
				return null;
			}
			
			return items.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		public synchronized void clearItems() {
			
			if (items == null) {
				return;
			}
			
			items.clear();
		}
    }
	
	protected static class SyncServiceConnection implements ServiceConnection {

		private static final String TAG = SyncServiceConnection.class.getSimpleName();
		private Synchronizer synchronizer;
		private SynchronizerEventListener synchronizerEventListener = 
				new SynchronizerEventListener();
		private AbstractTrackListFragment fragment;
		
		private class SynchronizerEventListener implements OnDoneListener, OnRunListener,
			OnStartProcessingTrackListener, OnSearchingDifferenesListener, 
			OnProcessTrackProgressUpdateListener, OnStartProcessingAlbumListener
		{
			@Override
			public void onRun() {
				fragment.updateOptionsMenu();
				fragment.syncProgressBar.show(fragment.getActivity());
				updateProgressBar(R.string.preparing, null);
			}

			@Override
			public void onDone(boolean anyChanges) {
				fragment.updateOptionsMenu();
				fragment.syncProgressBar.hide(fragment.getActivity());
				fragment.loadTrackList(!anyChanges);
			}

			@Override
			public void onProcessTrackProgressUpdate(String trackTitle, long processedBytesCount, 
					long totalBytesCount) 
			{
				updateProgressBar(trackTitle, 
						(int)((float) processedBytesCount / totalBytesCount * 100));
			}

			@Override
			public void onSearchingDifferences() {
				updateProgressBar(R.string.searching_differences, null);
			}

			@Override
			public void onStartProcessingTrack(String name) {
				updateProgressBar(name, 0);
			}

			@Override
			public void onStartProcessingAlbum(String title) {
				updateProgressBar(title, null);
			}
		}
		
		public SyncServiceConnection(AbstractTrackListFragment fragment) {
			super();
			this.fragment = fragment;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			Log.d(TAG, "onServiceConnected()");
			
			synchronizer = ((Binder) binder).getSynchronizer();
			
			if (synchronizer == null) {
				return;
			}
			
			if (synchronizer.isSearchingDifferences()) {
				synchronizerEventListener.onSearchingDifferences();
			} else if (isServiceRunning()) {
				updateProgressBar(R.string.preparing, null);
			}
			
			synchronizer.addOnRunListener(synchronizerEventListener);
			synchronizer.addOnSearchingDifferencesListener(synchronizerEventListener);
			synchronizer.addOnStartProcessingTrackListener(synchronizerEventListener);
			synchronizer.addOnStartProcessingAlbumListener(synchronizerEventListener);
			synchronizer.addOnProcessTrackProgressUpdateListener(synchronizerEventListener);
			synchronizer.addOnDoneListener(synchronizerEventListener);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "onServiceDisconnected()");
			
			disconnect();
		}

		public boolean isServiceRunning() {
			return synchronizer != null && synchronizer.getStatus() == Status.RUNNING;
		}

		public void stopService() {
			
			if (synchronizer == null) {
				return;
			}
			
			synchronizer.stop();
		}
		
		public void disconnect() {
			
			if (synchronizer == null) {
				return;
			}
			
			synchronizer.deleteOnDoneListener(synchronizerEventListener);
			synchronizer.deleteOnRunListener(synchronizerEventListener);
			synchronizer.deleteOnProcessTrackProgressUpdateListener(synchronizerEventListener);
			synchronizer.deleteOnSearchingDifferencesListener(synchronizerEventListener);
			synchronizer.deleteOnStartProcessingTrackListener(synchronizerEventListener);
			synchronizer = null;
		}
		
		private void updateProgressBar(int messageResourceId, Integer progress) {
			String message = null;
			
			if (fragment.isAdded() && !fragment.isRemoving()) {
				message = fragment.getString(messageResourceId);
			}
			
			updateProgressBar(message, progress);
		}
		
		private void updateProgressBar(String message, Integer progress) {
			String fullMessage = null;
			
			if (fragment.isAdded() && !fragment.isRemoving()) {
				fullMessage = fragment.getString(R.string.synchronizing) + " (" + 
						synchronizer.getProgress() + "%) \n" + message;
			}
			
			fragment.syncProgressBar.update(fullMessage, progress, fragment.getActivity());
		}
	}
	
	protected static final int FRIENDS_AND_GROUPS_ACTIVITY_REQUEST_CODE = 1;
	protected static final int ALBUMS_ACTIVITY_REQUEST_CODE = 2;
	protected static final String DISABLE_ADS_CHEAT = "!ads_off";
	protected final String TAG = getLogTag();
	protected MusicOwner owner;
	protected String ownerName;
	protected long albumId;
	protected String albumTitle;
	protected SyncServiceConnection syncServiceConnection = new SyncServiceConnection(this);
	protected TrackListAdapter trackListAdapter = createTrackListAdapter();
	protected List<TrackListItem> unfilteredTrackListItems;
	protected AbstractLoadTrackListTask loadTrackListTask;
	protected boolean searchResultsVisible = false;
	protected Button clearSearchResultsButton;
	protected String lastSearchQuery;
	protected boolean contextMenuVisible = false;
	protected ProgressBar trackListProgressBar = new ProgressBar();
	protected ProgressBar syncProgressBar = new ProgressBar();
	protected SharedPreferences activityPreferences;
	
	@Override
	public void onMusicDirChanged(MusicDirectory newMusicDir) {
		Log.d(TAG, "onMusicDirChanged()");
		
		if (newMusicDir.getOwner().equals(owner)) {
			loadTrackList(false);
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, 
			Bundle savedInstanceState) 
	{
		Log.d(TAG, "onCreateView()");
		
		return inflater.inflate(R.layout.track_list_fragment, null);
	}
	
	public boolean onShowSearchView() {
	
		if (!isSearchCanBePerformed()) 
		{
			return false;
		}
		
		return true;
	}

	public boolean onBackPressed() {
	
		if (searchResultsVisible) {
			clearSearchResults();
			return false;
		}
		
		return true;
	}

	public void onSearch(String query) {
		
		// if user entered cheat that disables ads 
		if (query.equals(DISABLE_ADS_CHEAT)) {
			Analytics.logDisableAdsCheat();
			Editor preferenceEditor = Preferences.getInstance().getSharedPreferences().edit(); 
			preferenceEditor.putBoolean(Preferences.ADS_ENABLED, false);
			
			if (preferenceEditor.commit()) {
				ToastUtils.show(getSherlockActivity(), R.string.ads_disabled);
			}
			
		} else {
			searchTracks(query);
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		
		PausableAsyncTask.notifyAboutDetachedFragment(loadTrackListTask, this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		Log.d(TAG, "onDestroy()");
		
		if (loadTrackListTask != null) {
			loadTrackListTask.cleanup();
		}

		Preferences.getInstance().deleteOnMusicDirChangedListener(this);
		syncServiceConnection.disconnect();
		PausableAsyncTask.notifyAboutDestroyedFragment(loadTrackListTask, this);
	}

	@Override
	public void onUserAuthed(long userId) {
		Log.d(TAG, "onUserAuthed()");
		
		MusicOwner owner = new MusicOwner();
		owner.setId(userId)
			.setGroup(false);
		
		if (setOwner(owner, null, true)) {
			setAlbum(AlbumsFragment.ALL_TRACKS_ALBUM_ID, null, true);
		} else {
			loadTrackList(false);
		}
	}

	@Override
	public void onOwnerChanged(MusicOwner owner, String name) {
		Log.d(TAG, "onOwnerChanged()");
		
		if (setOwner(owner, name, true)) {
			setAlbum(AlbumsFragment.ALL_TRACKS_ALBUM_ID, null, true);
		}
	}
	
	@Override
	public void onAlbumChanged(long id, String title) {
		Log.d(TAG, "onAlbumChanged()");
		
		setAlbum(id, title, true);
	}
	
	@Override
	public TrackListAdapter getListAdapter() {
		return (TrackListAdapter) super.getListAdapter();
	}

	@Override
	public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
		
		if (item.getItemId() == R.id.toggle_check_all) {
			item.setChecked(!item.isChecked());
			
			if (setAllTracksSyncable(item.isChecked())) {
				item.setIcon(item.isChecked() ? R.drawable.ic_menu_selector_uncheck_all : 
						R.drawable.ic_menu_selector_check_all);
			} else {
				item.setChecked(!item.isChecked());
			}
			
		} else if (item.getItemId() == R.id.preferences) {
			gotoPreferences();
		} else if (item.getItemId() == R.id.sync) {
			
			if (syncServiceConnection.isServiceRunning()) {
				stopSync();
			} else {
				sync();
			}
			
		} else if (item.getItemId() == R.id.search) {
			getActivity().onSearchRequested();
		} else if (item.getItemId() == R.id.friends_and_groups){
			gotoFriendsAndGroups();
		} else if (item.getItemId() == R.id.albums) {
			gotoAlbums();
		} else {
			return super.onOptionsItemSelected(item);
		}
		
		return true;
	}

	@Override
	public void onPrepareOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		Log.d(TAG, "onPrepareOptionsMenu()");
		
		MenuItem syncMenuItem = menu.findItem(R.id.sync);
		
		if (syncServiceConnection.isServiceRunning()) {
			syncMenuItem.setIcon(R.drawable.ic_menu_stop)
				.setTitle(R.string.stop_syncronization)
				.setEnabled(true);
		} else {
			syncMenuItem.setIcon(R.drawable.ic_menu_selector_sync)
				.setTitle(R.string.synchronize);
			
			if (isTrackListLoading() || getListAdapter() == null) {
				syncMenuItem.setEnabled(false);
			} else {
				syncMenuItem.setEnabled(true);
			}
		}
		
		menu.findItem(R.id.search).setEnabled(isSearchCanBePerformed());
		
		MenuItem friendsAndGroupsMenuItem = menu.findItem(R.id.friends_and_groups);
		MenuItem albumsMenuItem = menu.findItem(R.id.albums);
		
		if (syncServiceConnection.isServiceRunning() || isTrackListLoading()) {
			friendsAndGroupsMenuItem.setEnabled(false);
			albumsMenuItem.setEnabled(false);
		} else {
			friendsAndGroupsMenuItem.setEnabled(true);
			albumsMenuItem.setEnabled(true);
		}
		
		MenuItem toggleCheckAllTracksMenuItem = menu.findItem(R.id.toggle_check_all);
		
		if (getListAdapter() == null 
			|| getListAdapter().getCount() == 0 
			|| syncServiceConnection.isServiceRunning()
			|| isTrackListLoading()) 
		{
			toggleCheckAllTracksMenuItem.setEnabled(false);
		} else {
			toggleCheckAllTracksMenuItem
				.setEnabled(true)
				.setTitle(toggleCheckAllTracksMenuItem.isChecked() ? R.string.uncheck_all_tracks : 
					R.string.check_all_tracks);
		}
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {

		if (!contextMenuVisible) {
			return super.onContextItemSelected(item);
		}
		
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		TrackListItem track = getListAdapter().getItem(menuInfo.position);
		contextMenuVisible  = false;
		
		if (item.getItemId() == R.id.play) {
			playTrack(track);
			return super.onContextItemSelected(item);
		} else if (item.getItemId() == R.id.delete) {
			showDeleteTrackDlg(track);
			return super.onContextItemSelected(item);
		}
		
		doOnContextItemSelected(item.getItemId(), track);
		
		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		int position = ((AdapterContextMenuInfo) menuInfo).position;
		TrackListItem trackListItem = getListAdapter().getItem(position);
		
		if (trackListItem == null) {
			return;
		}

		getActivity().getMenuInflater().inflate(R.menu.track_menu, menu);
		
		boolean isTrackCanBePlayed = false;
		
		if ((trackListItem.file != null && trackListItem.file.exists()) 
			|| trackListItem.url != null) 
		{
			isTrackCanBePlayed = true;
		}
		
		menu.findItem(R.id.play).setEnabled(isTrackCanBePlayed);
		menu.findItem(R.id.delete).setVisible(
				AbstractDeleteTrackTask.canBeDeleted(trackListItem, owner));
		
		contextMenuVisible = true;
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		
		CheckBox checkbox = (CheckBox) v.findViewById(R.id.checkbox);
		checkbox.setChecked(!checkbox.isChecked());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		Log.d(TAG, "onCreate()");
	
		setRetainInstance(true);
		setHasOptionsMenu(true);
		
		Preferences.getInstance().addOnMusicDirChangedListener(this);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Log.d(TAG, "onActivityCreated()");
		
		setListAdapter(trackListAdapter);
		registerForContextMenu(getListView());
		
		trackListProgressBar.setContainer(
				getView().findViewById(R.id.track_list_progress_bar_container));
		syncProgressBar.setContainer(getView().findViewById(R.id.sync_progress_bar_container))
			.setProgressBar(
					(android.widget.ProgressBar) getView().findViewById(R.id.sync_progress_bar)
			)
			.setText((TextView) getView().findViewById(R.id.sync_progress_bar_text));
		
		clearSearchResultsButton = 
				(Button) getView().findViewById(R.id.clear_search_results_button);
		clearSearchResultsButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				if (searchResultsVisible) {
					clearSearchResults();
				}
			}
			
		});
		
		contextMenuVisible = false;
		TrackListActivity activity = (TrackListActivity) getActivity();
		activity.addEventListener(this);
		activity.getSyncServiceConnection().addListener(syncServiceConnection);
		
		activityPreferences = getActivity().getPreferences(Activity.MODE_PRIVATE);
		long ownerId = activityPreferences.getLong("owner_id", 
				Preferences.getInstance().getUserId());
		
		if (ownerId != 0) {
			boolean isOwnerGroup = activityPreferences.getBoolean("is_owner_group", false);
			String ownerName = activityPreferences.getString("owner_name", null);
			MusicOwner owner = new MusicOwner();
			owner.setId(ownerId)
				.setGroup(isOwnerGroup);
			setOwner(owner, ownerName, true);
		}
		
		long albumId = activityPreferences.getLong("album_id", AlbumsFragment.ALL_TRACKS_ALBUM_ID);
		setAlbum(albumId, activityPreferences.getString("album_title", null), true);
		
		if (searchResultsVisible) {
			clearSearchResultsButton.setVisibility(View.VISIBLE);
		}
		
		renderTitle();
		renderSubtitle();
		
		PausableAsyncTask.notifyAboutCreatedActivity(loadTrackListTask, this, activity);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		Log.d(TAG, "onActivityResult(); requestCode=" + requestCode);
		
		if (requestCode == FRIENDS_AND_GROUPS_ACTIVITY_REQUEST_CODE) {
			
			if (resultCode != Activity.RESULT_OK) {
	    		return;
	    	}
			
			long ownerId = 
					data.getLongExtra(FriendsAndGroupsActivity.RESULT_INTENT_EXTRA_OWNER_ID, 0);
			boolean isOwnerGroup = data.getBooleanExtra(
					FriendsAndGroupsActivity.RESULT_INTENT_EXTRA_OWNER_GROUP, false);
			String ownerName = 
					data.getStringExtra(FriendsAndGroupsActivity.RESULT_INTENT_EXTRA_OWNER_NAME);
			MusicOwner owner = new MusicOwner();
			owner.setId(ownerId)
				.setGroup(isOwnerGroup);
			
			if (setOwner(owner, ownerName, false)) {
				setAlbum(AlbumsFragment.ALL_TRACKS_ALBUM_ID, null, false);
			}
		} else if (requestCode == ALBUMS_ACTIVITY_REQUEST_CODE) {
			
			if (resultCode != Activity.RESULT_OK) {
				return;
			}
			
			long albumId = data.getLongExtra(AlbumsActivity.RESULT_INTENT_EXTRA_ALBUM_ID, 0);
			String albumTitle = data.getStringExtra(AlbumsActivity.RESULT_INTENT_EXTRA_ALBUM_TITLE);
			setAlbum(albumId, albumTitle, false);
		}
	}

	protected void doOnContextItemSelected(int itemId, TrackListItem track) {
	}
	
	protected boolean isTrackListLoading() {
		return loadTrackListTask != null 
				&& loadTrackListTask.isRunning() 
				&& !loadTrackListTask.isCancelled();
	}

	protected void sync() {
		
		if (getActivity() == null) {
			return;
		}
		
		if (!CommonUtils.isInternetEnabled(getActivity())) {
			ToastUtils.show(getActivity(), R.string.please_enable_internet);
			return;
		}
		
		if (!hasSyncableTracks()) 
		{
			ToastUtils.show(getActivity(), R.string.please_select_tracks_to_sync);
			return;
		}

		getActivity().bindService(new Intent(getActivity(), SyncService.class), 
				((TrackListActivity) getActivity()).getSyncServiceConnection(), 0);
		Intent intent = new Intent(getActivity(), SyncService.class);
		intent.putExtra(SyncService.START_INTENT_EXTRA_OWNER_ID, owner.getId());
		intent.putExtra(SyncService.START_INTENT_EXTRA_OWNER_GROUP, owner.isGroup());
		getActivity().startService(intent);
	}

	protected void stopSync() {
		syncServiceConnection.stopService();
		ToastUtils.show(getActivity(), R.string.stopping);
	}

	protected void gotoPreferences() {
		startActivity(new Intent(getActivity(), PreferenceActivity.class));
	}

	protected void playTrack(TrackListItem track) {
		Analytics.logPlayTrack();
		
		if (track == null || (track.file == null && track.url == null)) {
			return;
		}
		
		Intent intent = new Intent(Intent.ACTION_VIEW);
		Uri trackUri = track.file == null ? Uri.parse(track.url) : Uri.fromFile(track.file);
		intent.setDataAndType(trackUri, "audio/*");
		
		startActivity(intent);
	}

	protected synchronized void loadTrackList(boolean fromCache) {
		Log.d(TAG, "loadTrackList()");
		
		if (getActivity() == null 
			|| getActivity().isFinishing() 
			|| owner == null 
			|| syncServiceConnection.isServiceRunning()
			|| (fromCache && unfilteredTrackListItems != null)) 
		{
			return;
		}

		if (!CommonUtils.isInternetEnabled(Application.getContext())) {
			ToastUtils.show(getActivity(), R.string.please_enable_internet);
			return;
		}
		
		if (loadTrackListTask == null 
			|| loadTrackListTask.getStatus() == android.os.AsyncTask.Status.FINISHED) 
		{
			if (loadTrackListTask != null) {
				loadTrackListTask.cleanup();
			}
			
			loadTrackListTask = createLoadTrackListTask();
		}
	
		if (loadTrackListTask.getStatus() == android.os.AsyncTask.Status.PENDING) {
			
			getActivity().runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					loadTrackListTask.execute(owner);
				}
				
			});
		}
	}

	protected void searchTracks(String query) {
		Log.d(TAG, "searchTracks()");
		
		lastSearchQuery = query;
		
		if (TextUtils.isEmpty(query)) {
			return;
		}

		applyTrackFilters(query);
		searchResultsVisible = true;
		clearSearchResultsButton.setVisibility(View.VISIBLE);
		
		Analytics.logSearchTracks();
	}
	
	protected void updateOptionsMenu() {
		
		if (getActivity() == null) {
			return;
		}
		
		getActivity().runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				getSherlockActivity().invalidateOptionsMenu();
			}
			
		});
	}

	protected void clearSearchResults() {
		Log.d(TAG, "clearSearchResults()");
		
		if (!searchResultsVisible) {
			return;
		}
		
		applyTrackFilters(null);
		searchResultsVisible = false;
		clearSearchResultsButton.setVisibility(View.GONE);
	}

	protected boolean isSearchCanBePerformed() {
		return !isTrackListLoading() 
				&& !syncServiceConnection.isServiceRunning() 
				&& unfilteredTrackListItems != null 
				&& unfilteredTrackListItems.size() != 0; 
	}

	protected void gotoFriendsAndGroups() {
		startActivityForResult(new Intent(getActivity(), FriendsAndGroupsActivity.class), 
				FRIENDS_AND_GROUPS_ACTIVITY_REQUEST_CODE);
	}

	protected void gotoAlbums() {
		
		if (!CommonUtils.isInternetEnabled(Application.getContext())) {
			ToastUtils.show(getActivity(), R.string.please_enable_internet);
			return;
		}
		
		Intent intent = new Intent(getActivity(), AlbumsActivity.class);
		intent.putExtra(AlbumsActivity.START_INTENT_EXTRA_OWNER_ID, owner.getId());
		intent.putExtra(AlbumsActivity.START_INTENT_EXTRA_OWNER_GROUP, owner.isGroup());
		startActivityForResult(intent, ALBUMS_ACTIVITY_REQUEST_CODE);
	}
	
	/**
     * 
     * @return False if failed
     */
    protected boolean setTrackSyncable(TrackListItem track, boolean isSyncable) {
    	
    	if (track.syncable == isSyncable) {
			return true;
		}
		
		if (isSyncable) {
			
			AbstractDomainModel syncableTrack = createSyncableTrack(track);
			
			try {
				((DomainModelMapper) getSyncableTrackMapper()).add(syncableTrack);
				track.syncable = true;
			} catch (SQLException exception) {
				Log.e(TAG, null, exception);
				
				return false;
			}
			
		} else {
			deleteSyncableTrack(track);
			track.syncable = false;
		}
		
		return true;
    }

    /**
     * 
     * @return False if failed
     */
    protected boolean setAllTracksSyncable(boolean isSyncable) {

    	if (getListAdapter().getCount() == 0) {
    		return true;
    	}
    	
    	getSyncableTrackMapper().beginTransaction();
    	
    	boolean isErrorHappened = false;
    	
    	for (TrackListItem track : getListAdapter().getItems()) {
    		
    		if (!setTrackSyncable(track, isSyncable)) {
    			isErrorHappened = true;
    			ToastUtils.show(getActivity(), R.string.error_while_saving_settings);
    			break;
    		}
    	}
    	
    	if (!isErrorHappened) {
    		getSyncableTrackMapper().setTransactionSuccessful();
    	}
    	
    	getSyncableTrackMapper().endTransaction();
    	
    	if (isErrorHappened) {
    		return false;
    	}
    	
    	getListAdapter().notifyDataSetChanged();
    	
    	return true;
    }

    protected TrackListAdapter createTrackListAdapter() {
		return new TrackListAdapter(this);
	}

    /**
     * 
     * @param id
     * @param title
     * @param silently If false, will notify about this event
     * @return False, if album is the same as previous
     */
    protected boolean setAlbum(long id, String title, boolean silently) {
    	Log.d(TAG, "setAlbum()");

    	if (id == AlbumsFragment.ALL_TRACKS_ALBUM_ID) {
    		title = null;
    	}
		
		if (id == albumId && TextUtils.equals(title, albumTitle)) {
			return false;
		}
		
		albumId = id;
		albumTitle = title;
		
		long oldAlbumId = activityPreferences.getLong("album_id", 0);
		String oldAlbumTitle = activityPreferences.getString("album_title", 
				getString(R.string.all_tracks));
		
		if (albumId != oldAlbumId || !TextUtils.equals(albumTitle, oldAlbumTitle)) {
			activityPreferences.edit()
				.putLong("album_id", albumId)
				.putString("album_title", albumTitle)
				.commit();
		}

		if (searchResultsVisible) {
			clearSearchResults();
		}
		
		applyTrackFilters(null);
		renderTitle();
		
		if (!silently) {
			((TrackListActivity) getActivity()).notifyAboutChangedAlbum(this, albumId, albumTitle);
		}
		
		return true;
	}

	protected void renderTitle() {
		
		if (albumId == AlbumsFragment.ALL_TRACKS_ALBUM_ID) {
			getSherlockActivity().getSupportActionBar().setTitle(getString(R.string.all_tracks));
		} else {
			getSherlockActivity().getSupportActionBar().setTitle(albumTitle);
		}
	}

	/**
	 * 
     * @param owner
     * @param ownerName
     * @param silently If false, will notify about this event
     * @return False, if owner is the same as previous
     */
    protected boolean setOwner(MusicOwner owner, String ownerName, boolean silently) 
    {
		Log.d(TAG, "setOwner()");

		if (!owner.isGroup() && owner.getId() == Preferences.getInstance().getUserId()) {
			ownerName = null;
		}
		
		if (owner.equals(this.owner) && TextUtils.equals(ownerName, this.ownerName)) {
			return false;
		}
		
		this.owner = owner;
		this.ownerName = ownerName;

		activityPreferences.edit()
			.putLong("owner_id", this.owner.getId())
			.putBoolean("is_owner_group", this.owner.isGroup())
			.putString("owner_name", this.ownerName)
			.commit();

		lastSearchQuery = null;

		if (searchResultsVisible) {
			clearSearchResults();
		}
		
		loadTrackList(false);
		renderSubtitle();
		
		if (!silently) {
			((TrackListActivity) getActivity()).notifyAboutChangedOwner(this, this.owner, 
					this.ownerName);
		}
		
		return true;
	}

	protected void renderSubtitle() {
		getSherlockActivity().getSupportActionBar().setSubtitle(this.ownerName);
	}

    protected boolean hasSyncableTracks() {
    	return LocalToRemoteSyncableTrackMapper.getInstance().getCountByOwner(owner) != 0 
    			|| RemoteToLocalSyncableTrackMapper.getInstance().getCountByOwner(owner) != 0;
    }
    
    protected void deleteTrack(TrackListItem track) {
    	Log.d(TAG, "deleteTrack(); track=" + track);
    	
    	Analytics.logDeleteTrack();
    	createDeleteTrackTask().execute(track);
    }
    
    protected void showDeleteTrackDlg(TrackListItem track) {
		new DeleteTrackDlg(this, track).showAllowingStateLoss(getFragmentManager(), 
				DeleteTrackDlg.class.getSimpleName());
	}
    
    /**
     * Same as {@link #applyTrackFilters(String)}, but uses last search query.
     */
    protected void applyTrackFilters() {
    	applyTrackFilters(lastSearchQuery);
    }

    protected void applyTrackFilters(String searchQuery) {
    	Log.d(TAG, "applyTrackFilters()");
    	
    	ArrayList<TrackListItem> filteredTracks = null;
    	
    	if (unfilteredTrackListItems != null) {
    		filteredTracks = new ArrayList<TrackListItem>();
    		Collection<TrackListItem> filteredTracksBuffer = null;
    		
	    	if (albumId == AlbumsFragment.ALL_TRACKS_ALBUM_ID) {
	    		filteredTracksBuffer = new LinkedList<TrackListItem>(unfilteredTrackListItems);
	    	} else {
	    		filteredTracksBuffer = filterTracksByAlbum(unfilteredTrackListItems, 
	    				albumId, albumTitle);
	    	}
	    	
	    	filteredTracksBuffer = filterTracksByName(filteredTracksBuffer, searchQuery);
	    	filteredTracks.addAll(filteredTracksBuffer);
    	}
    	
		trackListAdapter.setItems(filteredTracks);
		trackListAdapter.notifyDataSetChanged();
	}
    
    protected Collection<TrackListItem> filterTracksByName(Collection<TrackListItem> tracks, 
    		String name) 
    {
    	if (TextUtils.isEmpty(name)) {
    		return new LinkedList<TrackListItem>(tracks);
    	}
    	
    	Collection<TrackListItem> filteredTracks = new LinkedList<TrackListItem>();
		Pattern searchPattern = Pattern.compile(".*" + Pattern.quote(name) + ".*", 
				Pattern.CASE_INSENSITIVE);

		for (TrackListItem track : tracks) {
			
			if (searchPattern.matcher(track.title).matches() 
				|| (track.artist != null 
				&& searchPattern.matcher(track.artist).matches())) 
			{
				filteredTracks.add(track);
			}
		}
		
		return filteredTracks;
    }

    protected abstract Collection<TrackListItem> filterTracksByAlbum(
			List<TrackListItem> tracks, long albumId, String albumTitle);

	protected abstract AbstractDeleteTrackTask createDeleteTrackTask();

	protected abstract String getLogTag();
    
    protected abstract AbstractLoadTrackListTask createLoadTrackListTask();
    
    protected abstract void deleteSyncableTrack(TrackListItem track);

	protected abstract DomainModelMapper<? extends AbstractDomainModel> getSyncableTrackMapper();

	protected abstract AbstractDomainModel createSyncableTrack(TrackListItem track);
}
