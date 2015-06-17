package com.malykhin.vkmusicsync.donate;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.net.http.AndroidHttpClient;

import com.malykhin.util.Log;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class Donation {
	
	private static final String TAG = Donation.class.getSimpleName();
	private static final Donation instance = new Donation();
	
	public static Donation getInstance() {
		return instance;
	}
	
	/**
	 * 
	 * @return False if failed
	 */
	public boolean start(Context context) {
		Log.d(TAG, "start()");

		return sendRequest(context, "start_donation.php");
	}
	
	/**
	 * 
	 * @return False if failed
	 */
	public boolean finish(Context context) {
		Log.d(TAG, "finish()");
		
		return sendRequest(context, "finish_donation.php");
	}
	
	private boolean sendRequest(Context context, String scriptFilename) {
		Account[] googleAccounts = AccountManager.get(context).getAccountsByType("com.google");
		List<BasicNameValuePair> requestParams = new ArrayList<BasicNameValuePair>(2);
		
		for (Account account : googleAccounts) {
			requestParams.add(new BasicNameValuePair("accounts[]", account.name));
		}
		
		HttpPost request = new HttpPost("http://vkmusicsync.webege.com/" + scriptFilename);
		
		try {
			request.setEntity(new UrlEncodedFormEntity(requestParams));
		} catch (UnsupportedEncodingException exception) {
			Log.e(TAG, null, exception);
			
			return false;
		}
		
		AndroidHttpClient httpClient = AndroidHttpClient.newInstance("Android");
		
		try {
			HttpEntity entity = httpClient.execute(request).getEntity();
			String stringResponse = EntityUtils.toString(entity);
			
			Log.d(TAG, "sendRequest(); response=" + stringResponse);
			
			JSONObject jsonResponse = 
					(JSONObject) new JSONTokener(stringResponse).nextValue();
			entity.consumeContent();
			
			if (!jsonResponse.getBoolean("result")) {
				return false;
			}
			
		} catch (Exception exception) {
			Log.e(TAG, null, exception);
			
			return false;
		} finally {
			httpClient.close();
		}
		
		return true;
	}
	
	private Donation(){}
}
