package com.malykhin.vkmusicsync.donate;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.crittercism.app.Crittercism;
import com.fortumo.android.Fortumo;
import com.fortumo.android.PaymentActivity;
import com.fortumo.android.PaymentRequest;
import com.fortumo.android.PaymentRequestBuilder;
import com.fortumo.android.PaymentResponse;
import com.malykhin.util.Log;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class MainActivity extends PaymentActivity {
	
	private static class StartDonationTask extends AsyncTask<Void, Void, Boolean>{

		private static final String TAG = StartDonationTask.class.getSimpleName();
		private MainActivity activity;
		
		public StartDonationTask(MainActivity activity) {
			this.activity = activity;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			activity.showDialog(PROGRESS_DLG_ID, null);
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			return Donation.getInstance().start(activity);
		}
		
		@Override
		protected void onPostExecute(Boolean success) {
			super.onPostExecute(success);
			
			try {
				activity.dismissDialog(PROGRESS_DLG_ID);
			} catch (IllegalArgumentException exception) {
			}
			
			if (!success) {
				Toast.makeText(activity, R.string.failed_to_prepare_payment, Toast.LENGTH_LONG)
					.show();
				return;
			}
			
			PaymentRequest request = new PaymentRequestBuilder()
				.setConsumable(false)
				.setDisplayString(activity.getString(R.string.donation_confirm))
				.setService("7ae8ce3e14d2fa470a64c4881e6dff2e", "27eed77b611beeaa8402705d42f5a720")
				.setProductName(activity.getString(R.string.donation))
				.build();
			
			activity.makePayment(request);
		}
		
	}
	
	private static final String TAG = MainActivity.class.getSimpleName();
	private static final int PROGRESS_DLG_ID = 1;
	
	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
		
		if (id == PROGRESS_DLG_ID) {
			ProgressDialog dlg = new ProgressDialog(this);
			dlg.setCancelable(false);
			dlg.setIndeterminate(true);
			dlg.setMessage(getString(R.string.loading));
			
			return dlg;
		}
		
		return super.onCreateDialog(id, args);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.setEnabled(BuildConfig.DEBUG);
		Log.d(TAG, "onCreate()");
		
		Crittercism.init(getApplicationContext(), "4f78c627b093157a340004c9"); 
		
		setContentView(R.layout.main_activity);
		Fortumo.enablePaymentBroadcast(this, Manifest.permission.PAYMENT_BROADCAST_PERMISSION);
		
		findViewById(R.id.donate_button).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				donate();
			}
			
		});
	}
	
	@Override
	protected void onPaymentFailed(PaymentResponse response) {
		super.onPaymentFailed(response);
		
		Log.d(TAG, "onPaymentFailed()");
		
		Toast.makeText(this, R.string.payment_failed, Toast.LENGTH_LONG).show();
	}
	
	@Override
	protected void onPaymentPending(PaymentResponse response) {
		super.onPaymentPending(response);
		
		Log.d(TAG, "onPaymentPending()");
		
		Toast.makeText(this, R.string.waiting_for_payment_result, Toast.LENGTH_LONG).show();
	}
	
	@Override
	protected void onPaymentSuccess(PaymentResponse response) {
		super.onPaymentSuccess(response);
		
		Log.d(TAG, "onPaymentSuccess()");
		
		Toast.makeText(this, R.string.payment_successful, Toast.LENGTH_LONG).show();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Log.d(TAG, "onResume()");

		boolean isInternetEnabled = isInternetEnabled();
		
		if (!isInternetEnabled) {
			Toast.makeText(this, R.string.please_enable_internet, Toast.LENGTH_LONG).show();
		}
		
		findViewById(R.id.donate_button).setEnabled(isInternetEnabled);
	}
	
	private void donate() {
		new StartDonationTask(this).execute();
	}
	
	private boolean isInternetEnabled() {
		ConnectivityManager connectionManager = 
				(ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectionManager.getActiveNetworkInfo();
		
		return networkInfo != null && networkInfo.isConnected();
	}
}
