package com.malykhin.vkmusicsync.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.malykhin.app.PausableAsyncTask;
import com.malykhin.app.ProgressDialogUtils;
import com.malykhin.gateway.vk.Group;
import com.malykhin.gateway.vk.User;
import com.malykhin.util.CommonUtils;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Application;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.VkGatewayHelper;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.util.ToastUtils;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class FriendsAndGroupsFragment extends SherlockListFragment {
	
	private static class LoadFriendsAndGroupsTask 
		extends PausableAsyncTask<Void, Void, ArrayList<FriendsAndGroupsListItem>> 
	{
		private final String TAG = LoadFriendsAndGroupsTask.class.getSimpleName();
		private int progressBarRequestId;

		public LoadFriendsAndGroupsTask(Activity activity, Fragment fragment) {
			super(activity, fragment);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressBarRequestId = ProgressDialogUtils.show(fragment.getFragmentManager(), 
					fragment.getString(R.string.loading), fragment);
		}
		
		@Override
		protected ArrayList<FriendsAndGroupsListItem> doInBackground(Void... params) {
			Log.d(TAG, "doInBackground()");
			
			User[] friends = new User[]{};
			ArrayList<Group> groups = new ArrayList<Group>();
			final long loggedUserId = Preferences.getInstance().getUserId();
			
			if (CommonUtils.isInternetEnabled(Application.getContext())) {
				
				try {
					friends = VkGatewayHelper.getGateway().getUserFriends(loggedUserId);
					groups = VkGatewayHelper.getGateway().getGroups(loggedUserId);
				} catch (Exception exception) {
					Log.e(TAG, null, exception);
					
					ToastUtils.show(activity, R.string.error_while_communicating_with_vkontake);
				}
			}
			
			ArrayList<FriendsAndGroupsListItem> friendsAndGroups = 
					new ArrayList<FriendsAndGroupsListItem>(friends.length + groups.size());
			
			for (User friend : friends) {
				friendsAndGroups.add(new FriendsAndGroupsListItem(friend.id, false, 
						friend.getFullName()));
			}
			
			for (Group group : groups) {
				friendsAndGroups.add(new FriendsAndGroupsListItem(group.id, true, group.name));
			}
			
			Collections.sort(friendsAndGroups, new Comparator<FriendsAndGroupsListItem>() {

				@Override
				public int compare(FriendsAndGroupsListItem owner1, FriendsAndGroupsListItem owner2) {
					return owner1.name.compareToIgnoreCase(owner2.name);
				}
				
			});
					
			ArrayList<FriendsAndGroupsListItem> finalFriendsAndGroups = 
					new ArrayList<FriendsAndGroupsListItem>(friendsAndGroups.size() + 1);
			FriendsAndGroupsListItem loggedUser = new FriendsAndGroupsListItem(loggedUserId, false, 
					fragment.getString(R.string.me));
			finalFriendsAndGroups.add(loggedUser);
			finalFriendsAndGroups.addAll(friendsAndGroups);
			
			doPause();
			
			return finalFriendsAndGroups;
		}
		
		protected void onPostExecute(ArrayList<FriendsAndGroupsListItem> friendsAndGroups) {
			ProgressDialogUtils.hide(fragment.getFragmentManager(), progressBarRequestId, 
					fragment);
			
			FriendsAndGroupsFragment fragment = (FriendsAndGroupsFragment) this.fragment;
			fragment.getListAdapter().setItems(friendsAndGroups);
			fragment.getListAdapter().notifyDataSetChanged();
		};
		
	}
	
	private static class FriendsAndGroupsListItem {
		public final long id;
		public final boolean isGroup;
		public final String name;
		
		public FriendsAndGroupsListItem(long id, boolean isGroup, String name) {
			this.id = id;
			this.isGroup = isGroup;
			this.name = name;
		}
	}
	
	private class FriendsAndGroupsListAdapter extends BaseAdapter {

		private ArrayList<FriendsAndGroupsListItem> items;
		private OnClickListener musicDirButtonClickListener = new OnClickListener() {
			
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(getActivity(), DirectoryPickerActivity.class);
				long ownerId = ((FriendsAndGroupsListItem) view.getTag()).id;
				boolean isOwnerGroup = ((FriendsAndGroupsListItem) view.getTag()).isGroup;
				intent.putExtra(DirectoryPickerActivity.START_INTENT_EXTRA_OWNER_ID, ownerId);
				intent.putExtra(DirectoryPickerActivity.START_INTENT_EXTRA_OWNER_GROUP, 
						isOwnerGroup);
				startActivity(intent);
			}
			
		};
		
		@Override
		public int getCount() {
			
			if (items == null) {
				return 0;
			}
			
			return items.size();
		}

		public void setItems(ArrayList<FriendsAndGroupsListItem> items) {
			this.items = items;
		}
		
		/**
		 * @return Null if items were not set
		 */
		@Override
		public FriendsAndGroupsListItem getItem(int position) {
			
			if (items == null) {
				return null;
			}
			
			return items.get(position);
		}

		@Override
		public long getItemId(int position) {
			return getItem(position).id;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View itemView = null;
			
			if (convertView == null) {
				itemView = View.inflate(getActivity(), R.layout.friends_and_groups_list_item, null);
			} else {
				itemView = convertView;
			}
			
			FriendsAndGroupsListItem owner = getItem(position);
			
			TextView nameText = (TextView) itemView.findViewById(R.id.name_text);
			nameText.setText(owner.name);
			
			Button musicDirButton = (Button) itemView.findViewById(R.id.music_dir_button);
			musicDirButton.setTag(owner);
			musicDirButton.setOnClickListener(musicDirButtonClickListener);
			
			return itemView;
		}

		/**
		 * 
		 * @return Null if items were not set
		 */
		public ArrayList<FriendsAndGroupsListItem> getItems() {
			return items;
		}
		
	}
	
	public static final String TAG = FriendsAndGroupsFragment.class.getSimpleName();
	private LoadFriendsAndGroupsTask loadFriendsAndGroupsTask;
	private FriendsAndGroupsListAdapter friendsAndGroupsListAdapter = 
			new FriendsAndGroupsListAdapter();
	
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
		
		setListAdapter(friendsAndGroupsListAdapter);
		PausableAsyncTask.notifyAboutCreatedActivity(loadFriendsAndGroupsTask, this, getActivity());
		loadFriendsAndGroups();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, 
			Bundle savedInstanceState) 
	{
		return inflater.inflate(R.layout.friends_and_groups_fragment, container);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		selectOwner(getListAdapter().getItem(position));
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		PausableAsyncTask.notifyAboutDetachedFragment(loadFriendsAndGroupsTask, this);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		Log.d(TAG, "onDestroy()");
		
		PausableAsyncTask.notifyAboutDestroyedFragment(loadFriendsAndGroupsTask, this);
	}

	@Override
	public FriendsAndGroupsListAdapter getListAdapter() {
		return (FriendsAndGroupsListAdapter) super.getListAdapter();
	}
	
	private void loadFriendsAndGroups() {
		Log.d(TAG, "loadUsers()");
		
		if (getActivity() == null 
			|| getActivity().isFinishing() 
			|| getListAdapter().getItems() != null
		) {
			return;
		}
		
		if (loadFriendsAndGroupsTask == null 
			|| loadFriendsAndGroupsTask.getStatus() == Status.FINISHED) 
		{
			loadFriendsAndGroupsTask = new LoadFriendsAndGroupsTask(getActivity(), this);
		}

		if (loadFriendsAndGroupsTask.getStatus() == Status.PENDING) {
			loadFriendsAndGroupsTask.execute();
		}
	}
	
	private void selectOwner(FriendsAndGroupsListItem owner) {
		Log.d(TAG, "selectOwner()");
		
		if (owner == null || getActivity() == null) {
			return;
		}
		
		Intent intent = new Intent();
		intent.putExtra(FriendsAndGroupsActivity.RESULT_INTENT_EXTRA_OWNER_ID, owner.id);
		intent.putExtra(FriendsAndGroupsActivity.RESULT_INTENT_EXTRA_OWNER_GROUP, owner.isGroup);
		intent.putExtra(FriendsAndGroupsActivity.RESULT_INTENT_EXTRA_OWNER_NAME, owner.name);
		getActivity().setResult(Activity.RESULT_OK, intent);
		getActivity().finish();
	}
}
