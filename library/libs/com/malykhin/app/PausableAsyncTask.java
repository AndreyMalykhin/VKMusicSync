package com.malykhin.app;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.ConditionVariable;
import android.support.v4.app.Fragment;

/**
 * {@link AsyncTask} that pauses itself while screen orientation changes.
 * 
 * @author Andrey Malykhin
 *
 * @param <Params>
 * @param <Progress>
 * @param <Result>
 */
abstract public class PausableAsyncTask<Params, Progress, Result> 
	extends AsyncTask<Params, Progress, Result> 
{
	protected Activity activity;
	protected Fragment fragment;
	private ConditionVariable resumeCondition = new ConditionVariable(true);
	
	/**
	 * 
	 * Resumes task. Must be called in {@link Fragment#onActivityCreated(android.os.Bundle)}. 
	 * Fragment must retain its instance via {@link Fragment#setRetainInstance(boolean)}.
	 * 
	 * @throws RuntimeException If fragment doesnt retains its instance
	 */
	public static void notifyAboutCreatedActivity(PausableAsyncTask<?, ?, ?> task, Fragment fragment, 
			Activity activity) 
	{
		if (task == null) {
			return;
		}

		if (!fragment.getRetainInstance()) {
			throw new RuntimeException(
					"Fragment must retain its instance. See Fragment.setRetainInstance()");
		}
		
		task.setActivity(activity);
		task.resume();
	}
	
	/**
	 * 
	 * Same as {@link #notifyAboutDestroyedFragment(PausableAsyncTask, boolean, boolean)}, but 
	 * cancelling task without interruption.
	 */
	public static void notifyAboutDestroyedFragment(PausableAsyncTask<?, ?, ?> task, 
			Fragment fragment) 
	{
		notifyAboutDestroyedFragment(task, fragment, true, false);
	}
	
	/**
	 * 
	 * Resumes task with with optional cancelling. Must be called in {@link Fragment#onDestroy()}.
	 * Fragment must retain its instance via {@link Fragment#setRetainInstance(boolean)}.
	 * 
	 * @throws RuntimeException If fragment doesnt retains its instance
	 */
	public static void notifyAboutDestroyedFragment(PausableAsyncTask<?, ?, ?> task, 
			Fragment fragment, boolean cancelTask, boolean cancelAllowingInterruption) 
	{
		if (task == null) {
			return;
		}
		
		if (!fragment.getRetainInstance()) {
			throw new RuntimeException(
					"Fragment must retain its instance. See Fragment.setRetainInstance()");
		}
		
		if (cancelTask) {
			task.cancel(cancelAllowingInterruption);
		}
		
		task.resume();
	}
	
	/**
	 * 
	 * Pauses task. Must be called in {@link Fragment#onDetach()}. Fragment must retain its instance 
	 * via {@link Fragment#setRetainInstance(boolean)}.
	 * 
	 * @throws RuntimeException If fragment doesnt retains its instance
	 */
	public static void notifyAboutDetachedFragment(PausableAsyncTask<?, ?, ?> task, 
			Fragment fragment) 
	{
		if (task == null) {
			return;
		}
		
		if (!fragment.getRetainInstance()) {
			throw new RuntimeException(
					"Fragment must retain its instance. See Fragment.setRetainInstance()");
		}
		
		task.pause();
	}
	
	public PausableAsyncTask(Activity activity, Fragment fragment) {
		super();
		this.activity = activity;
		this.fragment = fragment;
	}
	
	public PausableAsyncTask(Fragment fragment) {
		super();
		this.fragment = fragment;
	}
	
	public PausableAsyncTask(Activity activity) {
		super();
		this.activity = activity;
	}
	
	public PausableAsyncTask() {
		super();
	}
	
	public void setActivity(Activity activity) {
		this.activity = activity;
	}

	public void resume() {
		resumeCondition.open();
	}

	public void pause() {
		resumeCondition.close();
	}

	/**
	 * Pauses until {@link #resume()} is called. Must be called before return from 
	 * {@link #doInBackground(Object...)}, so it will be executed not on UI thread.
	 */
	protected final void doPause() {
		resumeCondition.block();
	}
}
