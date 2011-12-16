/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.Settings;
import com.joelapenna.foursquare.types.User;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Lets the user set pings on/off for a given friend.
 * 
 * @date September 25, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 * 
 */
public class UserDetailsPingsActivity extends Activity {
    private static final String TAG = "UserDetailsPingsActivity";
    private static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String EXTRA_USER_PARCEL = Foursquared.PACKAGE_NAME
        + ".UserDetailsPingsActivity.EXTRA_USER_PARCEL";
    
    public static final String EXTRA_USER_RETURNED = Foursquared.PACKAGE_NAME
        + ".UserDetailsPingsActivity.EXTRA_USER_RETURNED";
    
    private StateHolder mStateHolder;
    

    private BroadcastReceiver mLoggedOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive: " + intent);
            finish();
        }
    };
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.user_details_pings_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));
        
        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivity(this);
            setPreparedResultIntent();
        } else {
            mStateHolder = new StateHolder();
            if (getIntent().getExtras() != null) {
                if (getIntent().hasExtra(EXTRA_USER_PARCEL)) {
                    User user = getIntent().getExtras().getParcelable(EXTRA_USER_PARCEL);
                    mStateHolder.setUser(user);
                } else {
                    Log.e(TAG, TAG + " requires a user pareclable in its intent extras.");
                    finish();
                    return;
                }
            } else {
                Log.e(TAG, "TipActivity requires a tip pareclable in its intent extras.");
                finish();
                return;
            }
        }

        ensureUi();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivity(null);
        return mStateHolder;
    }

    private void ensureUi() {
        User user = mStateHolder.getUser();
        
        TextView tv = (TextView)findViewById(R.id.userDetailsPingsActivityDescription);
        Button btn = (Button)findViewById(R.id.userDetailsPingsActivityButton);
        
        if (user.getSettings().getGetPings()) {
            tv.setText(getString(R.string.user_details_pings_activity_description_on,
                    user.getFirstname()));
            btn.setText(getString(R.string.user_details_pings_activity_pings_off,
                    user.getFirstname()));
            btn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.setEnabled(false);
                    setProgressBarIndeterminateVisibility(true);
                    mStateHolder.startPingsTask(UserDetailsPingsActivity.this, false);
                }
            });
        } else {
            tv.setText(getString(R.string.user_details_pings_activity_description_off,
                    user.getFirstname()));
            btn.setText(getString(R.string.user_details_pings_activity_pings_on,
                    user.getFirstname()));
            btn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.setEnabled(false);
                    setProgressBarIndeterminateVisibility(true);
                    mStateHolder.startPingsTask(UserDetailsPingsActivity.this, true);
                }
            });
        }

        if (mStateHolder.getIsRunningTaskPings()) {
            btn.setEnabled(false);
            setProgressBarIndeterminateVisibility(true);
        } else {
            btn.setEnabled(true);
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    private void setPreparedResultIntent() {
        if (mStateHolder.getPreparedResult() != null) {
            setResult(Activity.RESULT_OK, mStateHolder.getPreparedResult());
        }
    }
    
    private void prepareResultIntent() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_USER_RETURNED, mStateHolder.getUser());
        mStateHolder.setPreparedResult(intent);
        setPreparedResultIntent();
    }
    
    private void onTaskPingsComplete(Settings settings, String userId, boolean on, Exception ex) {
        mStateHolder.setIsRunningTaskPings(false);
        
        // The api is returning pings = false for all cases, so manually overwrite,
        // assume a non-null settings object is success.
        if (settings != null) {
            settings.setGetPings(on);
            mStateHolder.setSettingsResult(settings);
            prepareResultIntent();
       
        } else {
            Toast.makeText(this, ex.toString(), Toast.LENGTH_LONG).show();
        }
        
        ensureUi();
    }
    
    private static class TaskPings extends AsyncTask<Void, Void, Settings> {
        private UserDetailsPingsActivity mActivity;
        private String mUserId;
        private boolean mOn;
        private Exception mReason;
        
        public TaskPings(UserDetailsPingsActivity activity, String userId, boolean on) {
            mActivity = activity;
            mUserId = userId;
            mOn = on;
        }

        public void setActivity(UserDetailsPingsActivity activity) {
            mActivity = activity;
        }

        @Override
        protected void onPreExecute() {
            mActivity.ensureUi();
        }
        
        @Override
        protected Settings doInBackground(Void... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();
                
                return foursquare.setpings(mUserId, mOn);
                
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "TipTask: Exception performing tip task.", e);
                mReason = e;
            }
            
            return null;
        }

        @Override
        protected void onPostExecute(Settings settings) {
            if (mActivity != null) {
                mActivity.onTaskPingsComplete(settings, mUserId, mOn, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskPingsComplete(null, mUserId, mOn, new FoursquareException("Tip task cancelled."));
            }
        }
    }
    
    private static class StateHolder {
        private User mUser;
        private boolean mIsRunningTask;
        private Intent mPreparedResult;
        private TaskPings mTaskPings;
        
        
        public StateHolder() {
            mPreparedResult = null;
            mIsRunningTask = false;
        }
        
        public User getUser() {
            return mUser;
        }
        
        public void setUser(User user) {
            mUser = user;
        }
        
        public void setSettingsResult(Settings settings) {
            mUser.getSettings().setGetPings(settings.getGetPings());
        }
        
        public void startPingsTask(UserDetailsPingsActivity activity, boolean on) {
            if (!mIsRunningTask) {
                mIsRunningTask = true;
                mTaskPings = new TaskPings(activity, mUser.getId(), on);
                mTaskPings.execute();
            }
        }

        public void setActivity(UserDetailsPingsActivity activity) {
            if (mTaskPings != null) {
                mTaskPings.setActivity(activity);
            }
        }
        
        public void setIsRunningTaskPings(boolean isRunning) {
            mIsRunningTask = isRunning;
        }
        
        public boolean getIsRunningTaskPings() {
            return mIsRunningTask;
        }
        
        public Intent getPreparedResult() {
            return mPreparedResult;
        }
        
        public void setPreparedResult(Intent intent) {
            mPreparedResult = intent;
        }
    }
}
