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
import android.widget.Toast;

import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.StringFormatters;

/**
 * Lets the user add a tip to a venue.
 * 
 * @date September 16, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class AddTipActivity extends Activity {
	
    private static final String TAG = "AddTipActivity";

    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".AddTipActivity.INTENT_EXTRA_VENUE";
    
    public static final String EXTRA_TIP_RETURNED = Foursquared.PACKAGE_NAME
        + ".AddTipActivity.EXTRA_TIP_RETURNED";

    private StateHolder mStateHolder;
    private ProgressDialog mDlgProgress;
    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_tip_activity);
        
        StateHolder holder = (StateHolder) getLastNonConfigurationInstance();
        if (holder != null) {
        	mStateHolder = holder;
        	mStateHolder.setActivityForTasks(this);
        } else {
        	mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
    	    	mStateHolder.setVenue((Venue)getIntent().getParcelableExtra(INTENT_EXTRA_VENUE));
            } else {
    	    	Log.e(TAG, "AddTipActivity must be given a venue parcel as intent extras.");
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
    	TextView tvVenueName = (TextView)findViewById(R.id.addTipActivityVenueName);
    	tvVenueName.setText(mStateHolder.getVenue().getName());
    	
    	TextView tvVenueAddress = (TextView)findViewById(R.id.addTipActivityVenueAddress);
    	tvVenueAddress.setText(StringFormatters.getVenueLocationCrossStreetOrCity(
    			mStateHolder.getVenue()));
    	
        Button btn = (Button) findViewById(R.id.addTipActivityButton);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	EditText et = (EditText)findViewById(R.id.addTipActivityText);
            	String text = et.getText().toString();
            	if (!TextUtils.isEmpty(text)) {
            		mStateHolder.startTaskAddTip(AddTipActivity.this, mStateHolder.getVenue().getId(), text);
            	} else {
            		Toast.makeText(AddTipActivity.this, text, Toast.LENGTH_SHORT).show();
            	}
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
    				Log.e(TAG, "User cancelled add tip.");
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
    
    private static class TaskAddTip extends AsyncTask<Void, Void, Tip> {

        private AddTipActivity mActivity;
        private String mVenueId;
        private String mTipText;
        private Exception mReason;

        public TaskAddTip(AddTipActivity activity, String venueId, String tipText) {
        	mActivity = activity;
        	mVenueId = venueId;
        	mTipText = tipText;
        }
        
        @Override
        protected void onPreExecute() {
        	mActivity.startProgressBar();
        }

        @Override
        protected Tip doInBackground(Void... params) {
            try {
            	// The returned tip won't have the user object or venue attached to it
            	// as part of the response. The venue is the parent venue and the user
            	// is the logged-in user.
            	Foursquared foursquared = (Foursquared)mActivity.getApplication();
                Tip tip = foursquared.getFoursquare().addTip(
                		mVenueId, mTipText, "tip", 
                		LocationUtils.createFoursquareLocation(foursquared.getLastKnownLocation()));
                
                // So fetch the full tip for convenience.
                Tip tipFull = foursquared.getFoursquare().tipDetail(tip.getId());
                
                return tipFull;
                
            } catch (Exception e) {
                Log.e(TAG, "Error adding tip.", e);
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Tip tip) {
        	mActivity.stopProgressBar();
        	mActivity.mStateHolder.setIsRunningTaskAddTip(false);
        	if (tip != null) {
        		Intent intent = new Intent();
        		intent.putExtra(EXTRA_TIP_RETURNED, tip);
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
        
        public void setActivity(AddTipActivity activity) {
        	mActivity = activity;
        }
    }
    
    private static final class StateHolder {
        private Venue mVenue;
        
        private TaskAddTip mTaskAddTip;
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
        
        public void startTaskAddTip(AddTipActivity activity, String venueId, String text) {
        	mIsRunningTaskAddTip = true;
        	mTaskAddTip = new TaskAddTip(activity, venueId, text);
        	mTaskAddTip.execute(); 
        }

        public void setActivityForTasks(AddTipActivity activity) {
        	if (mTaskAddTip != null) {
        		mTaskAddTip.setActivity(activity);
        	}
        }
        
        public void cancelTasks() {
        	if (mTaskAddTip != null) {
        		mTaskAddTip.cancel(true);
        	}
        }
    }
}
