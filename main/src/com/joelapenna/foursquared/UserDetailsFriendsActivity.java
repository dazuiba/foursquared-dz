/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.app.LoadableListActivity;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.widget.FriendListAdapter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

/**
 * Shows a list of friends for the user id passed as an intent extra.
 * 
 * @date March 9, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class UserDetailsFriendsActivity extends LoadableListActivity {
    static final String TAG = "UserDetailsFriendsActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    public static final String EXTRA_USER_ID = Foursquared.PACKAGE_NAME
        + ".UserDetailsFriendsActivity.EXTRA_USER_ID";
    public static final String EXTRA_USER_NAME = Foursquared.PACKAGE_NAME
        + ".UserDetailsFriendsActivity.EXTRA_USER_NAME";

    public static final String EXTRA_SHOW_ADD_FRIEND_OPTIONS = Foursquared.PACKAGE_NAME
        + ".UserDetailsFriendsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS";

    private StateHolder mStateHolder;
    private FriendListAdapter mListAdapter;

    
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
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivityForTaskFriends(this);
        } else {
            if (getIntent().hasExtra(EXTRA_USER_ID) && getIntent().hasExtra(EXTRA_USER_NAME)) {
                mStateHolder = new StateHolder(
                        getIntent().getStringExtra(EXTRA_USER_ID),
                        getIntent().getStringExtra(EXTRA_USER_NAME));
            } else {
                Log.e(TAG, TAG + " requires a userid and username in its intent extras.");
                finish();
                return;
            }
            
            mStateHolder.startTaskFriends(this);
        }
        
        ensureUi();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        if (isFinishing()) {
            mStateHolder.cancelTasks();
            mListAdapter.removeObserver();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTaskFriends(null);
        return mStateHolder;
    }

    private void ensureUi() {
        mListAdapter = new FriendListAdapter(this, 
            ((Foursquared) getApplication()).getRemoteResourceManager());
        mListAdapter.setGroup(mStateHolder.getFriends());
        
        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setSmoothScrollbarEnabled(true);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                User user = (User) parent.getAdapter().getItem(position);
                Intent intent = new Intent(UserDetailsFriendsActivity.this, UserDetailsActivity.class);
                intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, user);
                intent.putExtra(UserDetailsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS, true);
                startActivity(intent);
            }
        });
        
        if (mStateHolder.getIsRunningFriendsTask()) {
            setLoadingView();
        } else if (mStateHolder.getFetchedFriendsOnce() && mStateHolder.getFriends().size() == 0) {
            setEmptyView();
        }
        
        setTitle(getString(R.string.user_details_friends_activity_title, mStateHolder.getUsername()));
    }

    private void onFriendsTaskComplete(Group<User> group, Exception ex) {
        mListAdapter.removeObserver();
        mListAdapter = new FriendListAdapter(this, 
            ((Foursquared) getApplication()).getRemoteResourceManager());
        if (group != null) {
            mStateHolder.setFriends(group);
            mListAdapter.setGroup(mStateHolder.getFriends());
            getListView().setAdapter(mListAdapter);
        }
        else {
            mStateHolder.setFriends(new Group<User>());
            mListAdapter.setGroup(mStateHolder.getFriends());
            getListView().setAdapter(mListAdapter);
            
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        mStateHolder.setIsRunningFriendsTask(false);
        mStateHolder.setFetchedFriendsOnce(true);
        
        // TODO: We can probably tighten this up by just calling ensureUI() again.
        if (mStateHolder.getFriends().size() == 0) {
            setEmptyView();
        }
    }
    
    /**
     * Gets friends of the current user we're working for.
     */
    private static class FriendsTask extends AsyncTask<String, Void, Group<User>> {

        private UserDetailsFriendsActivity mActivity;
        private Exception mReason;

        public FriendsTask(UserDetailsFriendsActivity activity) {
            mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.setLoadingView();
        }

        @Override
        protected Group<User> doInBackground(String... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();
                return foursquare.friends(
                    params[0], LocationUtils.createFoursquareLocation(foursquared.getLastKnownLocation()));
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Group<User> users) {
            if (mActivity != null) {
                mActivity.onFriendsTaskComplete(users, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onFriendsTaskComplete(null, mReason);
            }
        }
        
        public void setActivity(UserDetailsFriendsActivity activity) {
            mActivity = activity;
        }
    }
    
    
    private static class StateHolder {
        
        private String mUserId;
        private String mUsername;
        private Group<User> mFriends;
        
        private FriendsTask mTaskFriends;
        private boolean mIsRunningFriendsTask;
        private boolean mFetchedFriendsOnce;
        
        
        public StateHolder(String userId, String username) {
            mUserId = userId;
            mUsername = username;
            mIsRunningFriendsTask = false;
            mFetchedFriendsOnce = false;
            mFriends = new Group<User>();
        }
 
        public String getUsername() {
            return mUsername;
        }
        
        public Group<User> getFriends() {
            return mFriends;
        }
        
        public void setFriends(Group<User> friends) {
            mFriends = friends;
        }
        
        public void startTaskFriends(UserDetailsFriendsActivity activity) {
            mIsRunningFriendsTask = true;
            mTaskFriends = new FriendsTask(activity);
            mTaskFriends.execute(mUserId);
        }

        public void setActivityForTaskFriends(UserDetailsFriendsActivity activity) {
            if (mTaskFriends != null) {
                mTaskFriends.setActivity(activity);
            }
        }

        public void setIsRunningFriendsTask(boolean isRunning) {
            mIsRunningFriendsTask = isRunning;
        }

        public boolean getIsRunningFriendsTask() {
            return mIsRunningFriendsTask;
        }
        
        public void setFetchedFriendsOnce(boolean fetchedOnce) {
            mFetchedFriendsOnce = fetchedOnce;
        }
        
        public boolean getFetchedFriendsOnce() {
            return mFetchedFriendsOnce;
        }
        
        public void cancelTasks() {
            if (mTaskFriends != null) {
                mTaskFriends.setActivity(null);
                mTaskFriends.cancel(true);
            }
        }
    }
}
