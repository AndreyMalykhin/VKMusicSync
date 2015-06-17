package com.malykhin.vkmusicsync.donate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.fortumo.android.Fortumo;
import com.malykhin.util.Log;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class PaymentStatusReceiver extends BroadcastReceiver {

	private static final String TAG = "PaymentStatusReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle extras = intent.getExtras();
		int billingStatus = extras.getInt("billing_status");
		
		Log.d(TAG, "onReceive(); extras=" + extras);
		
		if (billingStatus == Fortumo.MESSAGE_STATUS_BILLED) {
			context.startService(new Intent(context, DonationFinisherService.class));
		}
	}
}
