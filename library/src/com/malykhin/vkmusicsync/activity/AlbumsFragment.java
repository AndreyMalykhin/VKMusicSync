package com.malykhin.vkmusicsync.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.malykhin.gateway.vk.Album;
import com.malykhin.gateway.vk.VkGatewayException;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.model.NotSyncedLocalAlbum;
import com.malykhin.vkmusicsync.model.scanner.AlbumScanner.AlbumScannerResult;
import com.malykhin.vkmusicsync.model.scanner.MusicLibraryScanner;
import com.malykhin.vkmusicsync.util.ToastUtils;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class AlbumsFragment extends SherlockListFragment {
	
	private static class AlbumListItem {
		
		public final long id;
		public final String title;
		
		/**
		 * 
		 * @param id 0 means no ID
		 * @param title
		 */
		public AlbumListItem(long id, String title) {
			this.id = id;
			this.title = title;
		}
	}
	
	private static class AlbumListAdapter extends BaseAdapter {

		private List<AlbumListItem> items;
		private Fragment fragment;
		
		public AlbumListAdapter(Fragment fragment) {
			super();
			this.fragment = fragment;
		}
		
		@Override
		public int getCount() {
			return items == null ? 0 : items.size();
		}

		/**
		 * @return Null if items not set
		 */
		@Override
		public AlbumListItem getItem(int position) {
			return items == null ? null : items.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View itemView = null;
			
			if (convertView == null) {
				itemView = View.inflate(fragment.getActivity(), R.layout.album_list_item, null);
			} else {
				itemView = convertView;
			}
			
			AlbumListItem item = getItem(position);
			((TextView) itemView.findViewById(R.id.title_text)).setText(item.title);
			
			return itemView;
		}

		public void setItems(List<AlbumListItem> items) {
			this.items = items;
		}

		/**
		 * 
		 * @return Null if items not set
		 */
		public List<AlbumListItem> getItems() {
			return items;
		}
		
	}

	public static final long ALL_TRACKS_ALBUM_ID = -1;
	private static final String TAG = AlbumsFragment.class.getSimpleName();

	private MusicOwner owner = new MusicOwner();
	private AlbumListAdapter albumListAdapter = new AlbumListAdapter(this);
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "onCreate()");
		
		setRetainInstance(true);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Log.d(TAG, "onActivityCreated()");
		
		setListAdapter(albumListAdapter);
		long ownerId = getActivity().getIntent().getLongExtra(
				AlbumsActivity.START_INTENT_EXTRA_OWNER_ID, 0);
		boolean isOwnerGroup = getActivity().getIntent().getBooleanExtra(
				AlbumsActivity.START_INTENT_EXTRA_OWNER_GROUP, false);
		owner.setId(ownerId)
			.setGroup(isOwnerGroup);
		loadAlbums();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, 
			Bundle savedInstanceState) 
	{
		return inflater.inflate(R.layout.albums_fragment, null);
	}

	@Override
	public AlbumListAdapter getListAdapter() {
		return (AlbumListAdapter) super.getListAdapter();
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		
		AlbumListItem album = getListAdapter().getItem(position);
		Intent intent = new Intent();
		intent.putExtra(AlbumsActivity.RESULT_INTENT_EXTRA_ALBUM_ID, album.id);
		intent.putExtra(AlbumsActivity.RESULT_INTENT_EXTRA_ALBUM_TITLE, album.title);
		getActivity().setResult(Activity.RESULT_OK, intent);
		getActivity().finish();
	}
	
	private void loadAlbums() {
		Log.d(TAG, "loadAlbums()");
		
		if (getActivity().isFinishing() || getListAdapter().getItems() != null) {
			return;
		}
		
		MusicDirectory musicDir = null;
		
		try {
			musicDir = Preferences.getInstance().getCurrentOrAddDefaultMusicDir(owner);
		} catch (Exception exception) {
			Log.e(TAG, null, exception);
			
			ToastUtils.show(getActivity(), R.string.error_while_creating_directory_for_music);
			return;
		}
		
		AlbumScannerResult albumScannerResult = null;
		
		try {
			albumScannerResult = MusicLibraryScanner.getInstance().scan(musicDir).albumScannerResult;
		} catch (VkGatewayException exception) {
			Log.e(TAG, null, exception);
			
			ToastUtils.show(getActivity(), R.string.error_while_communicating_with_vkontake);
			return;
		}
		
		List<AlbumListItem> albums = new ArrayList<AlbumListItem>(
				albumScannerResult.remoteEntities.size() + 
				albumScannerResult.notSyncedLocalEntities.size()
		);
		
		albums.add(new AlbumListItem(ALL_TRACKS_ALBUM_ID, getString(R.string.all_tracks)));
		
		for (Album remoteAlbum : albumScannerResult.remoteEntities) {
			albums.add(new AlbumListItem(remoteAlbum.id, remoteAlbum.title));
		}
		
		for (NotSyncedLocalAlbum notSyncedLocalAlbum : albumScannerResult.notSyncedLocalEntities) {
			albums.add(new AlbumListItem(0, notSyncedLocalAlbum.getTitle()));
		}
		
		if (albums.size() > 1) {
			Collections.sort(albums.subList(1, albums.size() - 1), new Comparator<AlbumListItem>() {

				@Override
				public int compare(AlbumListItem album1, AlbumListItem album2) {
					return album1.title.compareToIgnoreCase(album2.title);
				}
				
			});
		}
		
		getListAdapter().setItems(albums);
		getListAdapter().notifyDataSetChanged();
	}
}
