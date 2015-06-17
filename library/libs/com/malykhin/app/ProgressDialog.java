package com.malykhin.app;

import android.app.Dialog;
import android.os.Bundle;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class ProgressDialog extends DialogFragment {
	
	private String message;

	public ProgressDialog() {
		super();
	}
	
	public ProgressDialog(String message) {
		super();
		this.message = message;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		android.app.ProgressDialog dialog = new android.app.ProgressDialog(getActivity());
		dialog.setMessage(message);
		setStyle(STYLE_NO_TITLE, 0);
		setCancelable(false);
		
		return dialog;
	}

}