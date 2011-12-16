/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.StringFormatters;

/**
 * Lets the user add a todo for a venue.
 * 
 * @date September 16, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class AddTodoActivity extends Activity {
	
    private static final String TAG = "AddTodoActivity";

    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".AddTodoActivity.INTENT_EXTRA_VENUE";
    
    public static final String EXTRA_TODO_RETURNED = Foursquared.PACKAGE_NAME
        + ".AddTodoActivity.EXTRA_TODO_RETURNED";

    private StateHolder mStateHolder;
    private ProgressDialog mDlgProgress;
    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_todo_activity);
        
        StateHolder holder = (StateHolder) getLastNonConfigurationInstance();
        if (holder != null) {
        	mStateHolder = holder;
        	mStateHolder.setActivityForTasks(this);
        } else {
        	mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
    	    	mStateHolder.setVenue((Venue)getIntent().getParcelableExtra(INTENT_EXTRA_VENUE));
            } else {
    	    	Log.e(TAG, "AddTodoActivity must be given a venue parcel as intent extras.");
    	    	finish();
    	    	return;
    	    }
        }
        
        ensureUi();
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTasks(null);
        return mStateHolder;
    }
    
    private void ensureUi() {
    	TextView tvVenueName = (TextView)findViewById(R.id.addTodoActivityVenueName);
    	tvVenueName.setText(mStateHolder.getVenue().getName());
    	
    	TextView tvVenueAddress = (TextView)findViewById(R.id.addTodoActivityVenueAddress);
    	tvVenueAddress.setText(StringFormatters.getVenueLocationCrossStreetOrCity(
    			mStateHolder.getVenue()));
    	
        Button btn = (Button) findViewById(R.id.addTodoActivityButton);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	EditText et = (EditText)findViewById(R.id.addTodoActivityText);
            	String text = et.getText().toString();
            	mStateHolder.startTaskAddTodo(AddTodoActivity.this, mStateHolder.getVenue().getId(), text);
            }
        });
        
        if (mStateHolder.getIsRunningTaskVenue()) {
        	startProgressBar();
        }
    }
    
    private void startProgressBar() {
        if (mDlgProgress == null) {
            mDlgProgress = ProgressDialog.show(this, "",
                    getResources().getString(R.string.add_tip_todo_activity_progress_message));
            mDlgProgress.setCancelable(true);
            mDlgProgress.setOnCancelListener(new OnCancelListener() {
    			@Override
    			public void onCancel(DialogInterface dialog) {
    				Log.e(TAG, "User cancelled add todo.");
    				mStateHolder.cancelTasks();
    			}
            });
        }
        mDlgProgress.show();
        setProgressBarIndeterminateVisibility(true);
    }

    private void stopProgressBar() {
        if (mDlgProgress != null) {
            mDlgProgress.dismiss();
            mDlgProgress = null;
        }
        setProgressBarIndeterminateVisibility(false);
    }
    
    private static class TaskAddTodo extends AsyncTask<Void, Void, Todo> {

        private AddTodoActivity mActivity;
        private String mVenueId;
        private String mTipText;
        private Exception mReason;

        public TaskAddTodo(AddTodoActivity activity, String venueId, String tipText) {
        	mActivity = activity;
        	mVenueId = venueId;
        	mTipText = tipText;
        }
        
        @Override
        protected void onPreExecute() {
        	mActivity.startProgressBar();
        }

        @Override
        protected Todo doInBackground(Void... params) {
            try {
            	// If the user entered optional text, we need to use one endpoint,
            	// if not, we need to use mark/todo.
            	Foursquared foursquared = (Foursquared)mActivity.getApplication();
            	Todo todo = null;
            	if (!TextUtils.isEmpty(mTipText)) {
            		// The returned tip won't have the user object or venue attached to it
                	// as part of the response. The venue is the parent venue and the user
                	// is the logged-in user.
	                Tip tip = foursquared.getFoursquare().addTip(
	                		mVenueId, mTipText, "todo", 
	                		LocationUtils.createFoursquareLocation(foursquared.getLastKnownLocation()));
	                
	                // So fetch the full tip for convenience.
	                Tip tipFull = foursquared.getFoursquare().tipDetail(tip.getId());
	                
	                // The addtip API returns a tip instead of a todo, unlike the mark/todo endpoint,
	                // so we create a dummy todo object for now to wrap the tip.
	                String now = StringFormatters.createServerDateFormatV1();
	                todo = new Todo();
	                todo.setId("id_" + now);
	                todo.setCreated(now);
	                todo.setTip(tipFull);
	                
	                Log.i(TAG, "Added todo with wrapper ID: " + todo.getId());
            	} else {
            		// No text, so in this case we need to mark the venue itself as a todo.
            		todo = foursquared.getFoursquare().markTodoVenue(mVenueId);
            		
	                Log.i(TAG, "Added todo with ID: " + todo.getId());
            	}
                
                return todo;
                
            } catch (Exception e) {
                Log.e(TAG, "Error adding tip.", e);
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Todo todo) {
        	mActivity.stopProgressBar();
        	mActivity.mStateHolder.setIsRunningTaskAddTip(false);
        	if (todo != null) {
        		Intent intent = new Intent();
        		intent.putExtra(EXTRA_TODO_RETURNED, todo);
        		mActivity.setResult(Activity.RESULT_OK, intent);
        		mActivity.finish();
        	} else {
        		NotificationsUtil.ToastReasonForFailure(mActivity, mReason);
        		mActivity.setResult(Activity.RESULT_CANCELED);
        		mActivity.finish();
        	}
        }

        @Override
        protected void onCancelled() {
            mActivity.stopProgressBar();
    		mActivity.setResult(Activity.RESULT_CANCELED);
    		mActivity.finish();
        }
        
        public void setActivity(AddTodoActivity activity) {
        	mActivity = activity;
        }
    }
    
    private static final class StateHolder {
        private Venue mVenue;
        
        private TaskAddTodo mTaskAddTodo;
        private boolean mIsRunningTaskAddTip;
        
        
        public Venue getVenue() {
        	return mVenue;
        }
        
        public void setVenue(Venue venue) {
        	mVenue = venue;
        }
        
        public boolean getIsRunningTaskVenue() {
        	return mIsRunningTaskAddTip;
        }
        
        public void setIsRunningTaskAddTip(boolean isRunningTaskAddTip) {
        	mIsRunningTaskAddTip = isRunningTaskAddTip;
        }
        
        public void startTaskAddTodo(AddTodoActivity activity, String venueId, String text) {
        	mIsRunningTaskAddTip = true;
        	mTaskAddTodo = new TaskAddTodo(activity, venueId, text);
        	mTaskAddTodo.execute(); 
        }

        public void setActivityForTasks(AddTodoActivity activity) {
        	if (mTaskAddTodo != null) {
        		mTaskAddTodo.setActivity(activity);
        	}
        }
        
        public void cancelTasks() {
        	if (mTaskAddTodo != null) {
        		mTaskAddTodo.cancel(true);
        	}
        }
    }
}
