package com.malykhin.vkmusicsync.activity;

import android.app.Activity;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;

import com.actionbarsherlock.app.SherlockFragment;
import com.malykhin.app.PausableAsyncTask;
import com.malykhin.app.ProgressDialogUtils;
import com.malykhin.gateway.vk.Auth.AccessScope;
import com.malykhin.gateway.vk.Auth.OnCancelListener;
import com.malykhin.gateway.vk.Auth.OnErrorListener;
import com.malykhin.gateway.vk.Auth.OnFinishLoadingListener;
import com.malykhin.gateway.vk.Auth.OnStartLoadingListener;
import com.malykhin.gateway.vk.Auth.OnSuccessListener;
import com.malykhin.gateway.vk.User;
import com.malykhin.gateway.vk.VkGateway;
import com.malykhin.util.Log;
import com.malykhin.vkmusicsync.Preferences;
import com.malykhin.vkmusicsync.VkGatewayHelper;
import com.malykhin.vkmusicsync.library.R;
import com.malykhin.vkmusicsync.util.ToastUtils;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class AuthFragment extends SherlockFragment {

	private class SaveAccountTask extends PausableAsyncTask<Void, Void, Integer> {

		private final String TAG = SaveAccountTask.class.getSimpleName();
		private int progressBarRequestId;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressBarRequestId = ProgressDialogUtils.show(getFragmentManager(), 
					getString(R.string.loading), AuthFragment.this);
		}
		
		@Override
		protected Integer doInBackground(Void... params) {
			Log.d(TAG, "doInBackground()");
			
			User user = null;
			Integer errorMsgId = null;
			
			try {
				user = vkGateway.getUserById(vkGateway.getLoggedUserId());
			} catch (Exception exception) {
				Log.e(TAG, null, exception);
				
				errorMsgId = R.string.error_while_communicating_with_vkontake;
			}
			
			if (errorMsgId == null) {
				Editor preferenceEditor = Preferences.getInstance().getSharedPreferences().edit();
				preferenceEditor.putLong(Preferences.USER_ID, user.id)
					.putString(Preferences.ACCOUNT, user.getFullName())
					.putString(Preferences.ACCESS_TOKEN, vkGateway.getAccessToken());
				
				if (!preferenceEditor.commit()) {
					errorMsgId = R.string.error_while_saving_settings;
				}
			}
			
			doPause();
			
			return errorMsgId;
		}
		
		@Override
		protected void onPostExecute(Integer errorMsgId) {
			super.onPostExecute(errorMsgId);
			
			Log.d(TAG, "onPostExecute()");
			
			ProgressDialogUtils.hide(getFragmentManager(), progressBarRequestId, 
					AuthFragment.this);
			
			if (errorMsgId != null) {
				getActivity().setResult(Activity.RESULT_CANCELED);
				ToastUtils.show(getActivity(), errorMsgId);
				return;
			}
			
			getActivity().setResult(Activity.RESULT_OK);
			getActivity().finish();
		}
	}
	
	private class AuthEventHandler implements OnSuccessListener, OnErrorListener, OnCancelListener,
		OnStartLoadingListener, OnFinishLoadingListener 
	{
		private final String TAG = AuthEventHandler.class.getSimpleName();
		private int progressBarRequestId;
		
		@Override
		public void onSuccess() {
			Log.d(TAG, "onSuccess()");
			
			saveAccount();
			webView.setVisibility(View.GONE);
		}
	
		@Override
		public void onError(String description, String failedUrl) {
			Log.d(TAG, "onError()");
			
			ProgressDialogUtils.hide(getFragmentManager(), progressBarRequestId, 
					AuthFragment.this);
			getActivity().setResult(Activity.RESULT_CANCELED);
		}

		@Override
		public void onCancel() {
			Log.d(TAG, "onCancel()");
			
			getActivity().setResult(Activity.RESULT_CANCELED);
			getActivity().finish();
		}

		@Override
		public void onFinishLoading() {
			Log.d(TAG, "onFinishLoading()");
			
			ProgressDialogUtils.hide(getFragmentManager(), progressBarRequestId, 
					AuthFragment.this);
		}

		@Override
		public void onStartLoading() {
			Log.d(TAG, "onStartLoading()");
			
			progressBarRequestId = ProgressDialogUtils.show(getFragmentManager(), 
					getString(R.string.loading), AuthFragment.this);
		}
	}
	
	private static final String TAG = AuthFragment.class.getSimpleName();

	private WebView webView;
	private SaveAccountTask saveAccountTask;
	private VkGateway vkGateway;
	private AuthEventHandler authEventHandler = new AuthEventHandler();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "onCreate()");
		
		setRetainInstance(true);
		vkGateway = VkGatewayHelper.getGateway();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Log.d(TAG, "onActivityCreated()");
		
		PausableAsyncTask.notifyAboutCreatedActivity(saveAccountTask, this, getActivity());
		
		CookieSyncManager.createInstance(getActivity());
		CookieManager.getInstance().removeAllCookie();
		CookieSyncManager.getInstance().sync();
		
		webView = (WebView) getView().findViewById(R.id.auth_web_page);
		webView.getSettings().setSaveFormData(false);
		webView.getSettings().setSavePassword(false);
		
		vkGateway.auth(
				webView, 
				Preferences.VK_APP_ID, 
				new AccessScope().audio().friends().offline().groups(), 
				authEventHandler, 
				authEventHandler, 
				authEventHandler, 
				authEventHandler, 
				authEventHandler
		);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, 
			Bundle savedInstanceState) 
	{
		return inflater.inflate(R.layout.auth_fragment, container);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		Log.d(TAG, "onDestroy()");
		
		vkGateway.removeListeners();
		PausableAsyncTask.notifyAboutDestroyedFragment(saveAccountTask, this);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		
		Log.d(TAG, "onDetach()");
		
		PausableAsyncTask.notifyAboutDetachedFragment(saveAccountTask, this);
	}

	private void saveAccount() {
		Log.d(TAG, "saveAccount()");
		
		if (getActivity() == null || getActivity().isFinishing()) {
			return;
		}

		if (saveAccountTask == null 
			|| saveAccountTask.getStatus() == android.os.AsyncTask.Status.FINISHED) 
		{
			saveAccountTask = new SaveAccountTask();
		} 
		
		if (saveAccountTask.getStatus() == android.os.AsyncTask.Status.PENDING) {
			saveAccountTask.execute();
		}
	}
}
