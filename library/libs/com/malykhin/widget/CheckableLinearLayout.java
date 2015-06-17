package com.malykhin.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.LinearLayout;

/**
 * 
 * @author http://tokudu.com/2010/android-checkable-linear-layout
 *
 */
public class CheckableLinearLayout extends LinearLayout implements Checkable {
	private Checkable checkbox;

	public CheckableLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		
		int childCount = getChildCount();
		
		for (int i = 0; i < childCount; ++i) {
			View v = getChildAt(i);

			if (v instanceof Checkable) {
				checkbox = (Checkable) v;
				break;
			}
		}
	}

	@Override
	public boolean isChecked() {
		return checkbox != null ? checkbox.isChecked() : false;
	}

	@Override
	public void setChecked(boolean checked) {
		if (checkbox != null) {
			checkbox.setChecked(checked);
		}
	}

	@Override
	public void toggle() {
		if (checkbox != null) {
			checkbox.toggle();
		}
	}
}

/**
 * 
 * From {@link http://www.marvinlabs.com/2010/10/custom-listview-ability-check-items/}
 *
 */
//public class CheckableLinearLayout extends LinearLayout implements Checkable {
//
//	private boolean isChecked;
//	private List<Checkable> checkableViews;
//
//	public CheckableLinearLayout(Context context, AttributeSet attrs) {
//		super(context, attrs);
//		initialise(attrs);
//	}
//
//	public CheckableLinearLayout(Context context, int checkableId) {
//		super(context);
//		initialise(null);
//	}
//
//	/*
//	 * @see android.widget.Checkable#isChecked()
//	 */
//	public boolean isChecked() {
//		return isChecked;
//	}
//
//	/*
//	 * @see android.widget.Checkable#setChecked(boolean)
//	 */
//	public void setChecked(boolean isChecked) {
//		this.isChecked = isChecked;
//		for (Checkable c : checkableViews) {
//			c.setChecked(isChecked);
//		}
//	}
//
//	/*
//	 * @see android.widget.Checkable#toggle()
//	 */
//	public void toggle() {
//		this.isChecked = !this.isChecked;
//		for (Checkable c : checkableViews) {
//			c.toggle();
//		}
//	}
//
//	@Override
//	protected void onFinishInflate() {
//		super.onFinishInflate();
//
//		final int childCount = this.getChildCount();
//		for (int i = 0; i < childCount; ++i) {
//			findCheckableChildren(this.getChildAt(i));
//		}
//	}
//
//	/**
//	 * Read the custom XML attributes
//	 */
//	private void initialise(AttributeSet attrs) {
//		this.isChecked = false;
//		this.checkableViews = new ArrayList<Checkable>(5);
//	}
//
//	/**
//	 * Add to our checkable list all the children of the view that implement the
//	 * interface Checkable
//	 */
//	private void findCheckableChildren(View v) {
//		if (v instanceof Checkable) {
//			this.checkableViews.add((Checkable) v);
//		}
//
//		if (v instanceof ViewGroup) {
//			final ViewGroup vg = (ViewGroup) v;
//			final int childCount = vg.getChildCount();
//			for (int i = 0; i < childCount; ++i) {
//				findCheckableChildren(vg.getChildAt(i));
//			}
//		}
//	}
//
//}
