package com.malykhin.gateway.vk;

import java.net.URLEncoder;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebView;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Auth {
	
	public interface OnSuccessListener {
		public void onSuccess();
	}
	
	public interface OnErrorListener {
		public void onError(String description, String failingUrl);
	}
	
	public interface OnCancelListener {
		public void onCancel();
	}

	public interface OnStartLoadingListener {
		public void onStartLoading();
	}
	
	public interface OnFinishLoadingListener {
		public void onFinishLoading();
	}
	
	public static class AccessScope {
		private List<Integer> bits = new LinkedList<Integer>();
		private List<String> items = new LinkedList<String>();
		
		public AccessScope audio() {
			bits.add(8);
			items.add("audio");
			return this;
		}
		
		public AccessScope friends() {
			bits.add(2);
			items.add("friends");
			return this;
		}
		
		public AccessScope offline() {
			items.add("offline");
			return this;
		}
		
		public AccessScope groups() {
			items.add("groups");
			return this;
		}
		
		public List<String> getItems() {
			return items;
		}
		
		public boolean has(int bitMask) {
			
			for (Integer bit : bits) {
				if ((bitMask & bit) == 0) {
					return false;
				}
			}
			
			return true;
		}
	}
	
	private class WebViewClient extends android.webkit.WebViewClient {
		
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}
		
		@Override
		public void onReceivedError(WebView view, int errorCode, String description, 
			String failingUrl
		) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			
			if (onErrorListener != null) {
				onErrorListener.onError(description, failingUrl);
			}
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);

			if (onStartLoadingListener != null) {
				onStartLoadingListener.onStartLoading();
			}
			
			if (url.contains("error")) {
				
				if (onCancelListener != null) {
					onCancelListener.onCancel();
				}
				
			} else if (url.contains("access_token")) {
				Uri uri = Uri.parse(url);
				uri = Uri.parse(uri.getHost() + "?" + uri.getFragment());
				
				accessToken = uri.getQueryParameter("access_token");
				userId = Long.parseLong(uri.getQueryParameter("user_id"));
				
				int expiresIn = Integer.parseInt(uri.getQueryParameter("expires_in"));
				
				if (expiresIn > 0) {
					expireDate = new Date(System.currentTimeMillis() + expiresIn * 1000);
				}
				
				if (onSuccessListener != null) {
					onSuccessListener.onSuccess();
				}
			}
		}
		
		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			
			if (onFinishLoadingListener != null) {
				onFinishLoadingListener.onFinishLoading();
			}
		}

	}
	
	private static final String TAG = Auth.class.getSimpleName();
	private static final Auth instance = new Auth();

	private OnSuccessListener onSuccessListener;
	private OnErrorListener onErrorListener;
	private OnCancelListener onCancelListener;
	private OnStartLoadingListener onStartLoadingListener;
	private OnFinishLoadingListener onFinishLoadingListener;
	private String accessToken;
	private Long userId;
	private Date expireDate;
	
	static Auth getInstance() {
		return instance;
	}

	/**
	 * 
	 * @return Null if no access token
	 */
	String getAccessToken() {
		return accessToken;
	}
	
	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	void setAccessToken(String token) {
		
		if (token == null) {
			throw new IllegalArgumentException("Invalid token: " + token);
		}
		
		accessToken = token;
	}
	
	/**
	 * 
	 * @return ID of logged user or null if no logged user
	 */
	Long getUserId() {
		return userId;
	}
	
	/**
	 * 
	 * @throws IllegalArgumentException
	 */
	void setUserId(Long userId) {
		
		if (userId == null || userId == 0) {
			throw new IllegalArgumentException("Invalid user ID: " + userId);
		}
		
		this.userId = userId;
	}
	
	/**
	 * 
	 * @return Date when access token will expire. Null if token is infinite
	 */
	Date getExpireDate() {
		return expireDate;
	}
	
	void removeListeners() {
		onSuccessListener = null;
		onErrorListener = null;
		onCancelListener = null;
		onStartLoadingListener = null;
		onFinishLoadingListener = null;
	}
	
	/**
	 * 
	 * @param webView
	 * @param appId Vkontakte application ID
	 * @param accessScope
	 * @param onSuccessListener Can be null
	 * @param onErrorListener Can be null
	 * @param onCancelListener Can be null
	 * @param onStartLoadingListener Can be null
	 * @param onFinishLoadingListener Can be null
	 */
	void auth(
			WebView webView, 
			long appId, 
			AccessScope accessScope, 
			OnSuccessListener onSuccessListener, 
			OnErrorListener onErrorListener, 
			OnCancelListener onCancelListener,
			OnStartLoadingListener onStartLoadingListener,
			OnFinishLoadingListener onFinishLoadingListener
	) {
		this.onSuccessListener = onSuccessListener;
		this.onErrorListener = onErrorListener;
		this.onCancelListener = onCancelListener;
		this.onStartLoadingListener = onStartLoadingListener;
		this.onFinishLoadingListener = onFinishLoadingListener;
		
		webView.getSettings().setJavaScriptEnabled(true);
		webView.setWebViewClient(new WebViewClient());

		webView.loadUrl("https://oauth.vk.com/authorize?client_id=" + appId + "&scope=" + 
			TextUtils.join(",", accessScope.getItems()) + "&redirect_uri=" + 
			URLEncoder.encode("http://oauth.vk.com/blank.html") + 
			"&display=touch&response_type=token"
		);
	}

	private Auth() {}

}
