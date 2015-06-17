package com.malykhin.app;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class DialogFragment extends android.support.v4.app.DialogFragment {

	public DialogFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	
	/**
     * Same as {@link #show(FragmentManager, String)}, but uses
     * {@link FragmentTransaction#commitAllowingStateLoss()}.
     */
	public void showAllowingStateLoss(FragmentManager manager, String tag) {
		setShowsDialog(true);
		manager.beginTransaction()
			.add(this, tag)
			.commitAllowingStateLoss();
	}
	
	/**
     * Same as {@link #dismiss()}, but uses 
     * {@link FragmentTransaction#commitAllowingStateLoss()}.
     */
    public void dismissAllowingStateLoss() {
    	
    	if (getDialog() != null) {
    		getDialog().dismiss();
        }

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.remove(this).commitAllowingStateLoss();
    }
	
	@Override
	public void onDestroyView() {
		
		if (getDialog() != null && getRetainInstance()) {
			getDialog().setOnDismissListener(null);
		}
		
		super.onDestroyView();
	}
}
