package com.malykhin.vkmusicsync.gateway;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
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
public class DonationGateway {
	
	private static final String TAG = DonationGateway.class.getSimpleName();
	private static final DonationGateway instance = new DonationGateway();
	
	public static DonationGateway getInstance() {
		return instance;
	}

	/**
	 * 
	 * @throws IOException
	 * @throws ParseException
	 * @throws JSONException
	 */
	public boolean isLoggedUserDonated(Context context) throws IOException, ParseException, 
		JSONException 
	{
		Account[] userAccounts = AccountManager.get(context).getAccountsByType("com.google");
		List<BasicNameValuePair> requestParams = new ArrayList<BasicNameValuePair>(2);
		
		for (Account userAccount : userAccounts) {
			requestParams.add(new BasicNameValuePair("accounts[]", userAccount.name));
		}
		
		HttpGet request = new HttpGet("http://vkmusicsync.webege.com/check_donation.php?" + 
				URLEncodedUtils.format(requestParams, "utf-8"));
		AndroidHttpClient httpClient = AndroidHttpClient.newInstance("Android");
		
		try {
			HttpEntity entity = httpClient.execute(request).getEntity();
			JSONObject response = 
					(JSONObject) new JSONTokener(EntityUtils.toString(entity)).nextValue();
			entity.consumeContent();
			
			Log.d(TAG, "isLoggedUserDonated(); response=" + response);
			
			if (!response.getBoolean("result")) {
				return false;
			}
			
		} finally {
			httpClient.close();
		}
		
		return true;
	}
	
	private DonationGateway() {
	}
}
