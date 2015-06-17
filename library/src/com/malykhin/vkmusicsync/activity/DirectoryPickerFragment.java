package com.malykhin.vkmusicsync.activity;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;

import org.apache.commons.io.comparator.NameFileComparator;

import android.app.Activity;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.Environment;
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
import com.malykhin.io.CreateDirectoryException;
import com.malykhin.io.NotWritableDirectoryException;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Application;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.model.MusicDirectory;
import com.malykhin.vkmusicsync.model.MusicOwner;
import com.malykhin.vkmusicsync.util.Analytics;
import com.malykhin.vkmusicsync.util.ToastUtils;

/**
 * TODO dont allow to change dir while sync service is running
 * 
 * @author Andrey Malykhin
 *
 */
public class DirectoryPickerFragment extends SherlockListFragment {
	
	private class ChangeMusicDirTask extends PausableAsyncTask<File, Void, Integer>{

		private int progressBarRequestId;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			progressBarRequestId = ProgressDialogUtils.show(getFragmentManager(), 
					getResources().getString(R.string.loading), DirectoryPickerFragment.this);
		}
		
		@Override
		protected Integer doInBackground(File... params) {
			Log.d(TAG, "ChangeMusicDirTask.doInBackground()");
			
			File newMusicDir = params[0];
			Integer errorMsgId = null;
			
			try {
				boolean isDirChanged = Preferences.getInstance().changeMusicDir(musicDir, 
						newMusicDir);
				
				if (isDirChanged) {
					Analytics.logMusicDirChange();
				}
				
			} catch (IOException exception) {
				Log.e(TAG, null, exception);
				
				if (exception instanceof CreateDirectoryException) {
					errorMsgId = R.string.error_while_creating_directory_for_music;
				} else if (exception instanceof NotWritableDirectoryException) {
					errorMsgId = R.string.choosed_directory_is_not_writable_please_choose_another;
				} else {
					errorMsgId = R.string.error_while_trying_to_change_music_dir;
				}
			}
			
			doPause();
			
			return errorMsgId;
		}
		
		@Override
		protected void onPostExecute(Integer errorMsgId) {
			super.onPostExecute(errorMsgId);
			
			ProgressDialogUtils.hide(getFragmentManager(), progressBarRequestId, 
					DirectoryPickerFragment.this);
			
			if (errorMsgId != null) {
				getActivity().setResult(Activity.RESULT_CANCELED);
				ToastUtils.show(getActivity(), errorMsgId);
				return;
			}
			
			getActivity().setResult(Activity.RESULT_OK);
			getActivity().finish();
		}
	}
	
	private class DirectoryListAdapter extends BaseAdapter {

		private File[] items;
		
		public void setItems(File[] items) {
			this.items = items;
		}
		
		@Override
		public int getCount() {
			return items == null ? 0 : items.length;
		}

		/**
		 * @return Null if items were not set
		 */
		@Override
		public File getItem(int position) {
			return items == null ? null : items[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View directoryView = null;
			
			if (convertView == null) {
				directoryView = View.inflate(getActivity(), R.layout.directory_picker_item, null);
			} else {
				directoryView = convertView;
			}
			
			File dir = getItem(position);
			
			TextView dirNameText = (TextView) directoryView.findViewById(R.id.directory_name_text);
			dirNameText.setText(position == 0 ? ".." : dir.getName());
			
			return directoryView;
		}
		
	}
	
	public static final String TAG = DirectoryPickerActivity.class.getSimpleName();
	
	private DirectoryListAdapter directoryListAdapter = new DirectoryListAdapter();
	private File currentDir;
	private MusicDirectory musicDir;
	private ChangeMusicDirTask changeMusicDirTask;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "onActivityCreated()");
		
		setListAdapter(directoryListAdapter);

		((Button) getView().findViewById(R.id.ok_button)).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				changeMusicDirectory(currentDir);
			}
			
		});
		
		((Button) getView().findViewById(R.id.cancel_button)).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				cancel();
			}
			
		});
		
		selectDirectory(currentDir);
		PausableAsyncTask.notifyAboutCreatedActivity(changeMusicDirTask, this, getActivity());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "onCreate()");
		
		setRetainInstance(true);
		
		long ownerId = ((DirectoryPickerActivity) getActivity()).getIntent().getLongExtra(
				DirectoryPickerActivity.START_INTENT_EXTRA_OWNER_ID, 0);
		boolean isOwnerGroup = ((DirectoryPickerActivity) getActivity()).getIntent().getBooleanExtra(
				DirectoryPickerActivity.START_INTENT_EXTRA_OWNER_GROUP, false);
		MusicOwner owner = new MusicOwner();
		owner.setId(ownerId)
			.setGroup(isOwnerGroup);
		
		try {
			musicDir = Preferences.getInstance().getCurrentOrAddDefaultMusicDir(owner);
		} catch (Exception exception) {
			Log.e(TAG, null, exception);
			
			ToastUtils.show(Application.getContext(), 
					R.string.error_while_creating_directory_for_music);
		}
		
		if (musicDir == null) {
			currentDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
		} else {
			currentDir = musicDir.getDirectory();
		}
	}
	
	@Override
	public DirectoryListAdapter getListAdapter() {
		return (DirectoryListAdapter) super.getListAdapter();
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		selectDirectory(getListAdapter().getItem(position));
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, 
			Bundle savedInstanceState) 
	{
		return inflater.inflate(R.layout.directory_picker_fragment, container);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		PausableAsyncTask.notifyAboutDestroyedFragment(changeMusicDirTask, this);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		PausableAsyncTask.notifyAboutDetachedFragment(changeMusicDirTask, this);
	}

	private void selectDirectory(File dir) {
		Log.d(TAG, "selectDirectory()");
		
		if (dir == null) {
			return;
		}
		
		currentDir = dir;
		loadDirectoryList(dir);
		((TextView) getView().findViewById(R.id.directory_path_text)).setText(dir.getAbsolutePath());
		setSelection(0);
	}
	
	private void loadDirectoryList(File parentDir) {
		Log.d(TAG, "loadDirectoryList()");
		
		if (parentDir == null) {
			return;
		}
		
		LinkedList<File> dirs = new LinkedList<File>();
		File[] files = parentDir.listFiles();
		
		if (files != null) {
			
			for (File file : files) {
				if (!file.isDirectory()) {
					continue;
				}
				
				dirs.add(file);
			}
			
			Collections.sort(dirs, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
		}
		
		File parentOfParentDir = parentDir.getParentFile();
		
		if (parentOfParentDir != null) {
			dirs.addFirst(parentOfParentDir);
		}
		
		getListAdapter().setItems(dirs.toArray(new File[dirs.size()]));
		getListAdapter().notifyDataSetChanged();
	}
	
	private void changeMusicDirectory(File newDirectory) {
		Log.d(TAG, "changeMusicDirectory()");
		
		if (getActivity() == null) {
			return;
		}
		
		if (changeMusicDirTask == null || changeMusicDirTask.getStatus() == Status.FINISHED) {
			changeMusicDirTask = new ChangeMusicDirTask();
		} 
		
		if (changeMusicDirTask.getStatus() == Status.PENDING) {
			changeMusicDirTask.execute(newDirectory);
		}
	}
	
	private void cancel() {
		getActivity().setResult(Activity.RESULT_CANCELED);
		getActivity().finish();
	}
}
