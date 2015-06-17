package com.malykhin.vkmusicsync.donate;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class DonationFinisherService extends IntentService {

	private static final String TAG = DonationFinisherService.class.getSimpleName();
	private Handler handler = new Handler();
	
	public DonationFinisherService() {
		super(TAG);
	}
	
	public DonationFinisherService(String name) {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		
		if (!Donation.getInstance().finish(this)) {
			showErrorMsg();
		}
		
		stopSelf();
	}
	
	private void showErrorMsg() {
		
		handler.post(new Runnable() {
			
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), R.string.ad_disabling_failed, 
						Toast.LENGTH_LONG).show();
			}
			
		});
	}
}
