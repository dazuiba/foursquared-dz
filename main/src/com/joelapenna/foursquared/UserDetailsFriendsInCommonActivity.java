/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.app.LoadableListActivityWithViewAndHeader;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.widget.FriendListAdapter;
import com.joelapenna.foursquared.widget.SegmentedButton;
import com.joelapenna.foursquared.widget.SegmentedButton.OnClickListenerSegmentedButton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;

/**
 * To be used when a user has some friends in common. We show two lists, by default
 * the first list is 'friends in common'. The second list is all friends. This is
 * expected to be used with a fully-fetched user object, so the friends in common
 * group should already be fetched. The full 'all friends' list is fetched separately
 * within this activity.
 * 
 * If the user has no friends in common, then just use UserDetailsFriendsActivity 
 * directly.
 * 
 * @date September 23, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class UserDetailsFriendsInCommonActivity extends LoadableListActivityWithViewAndHeader {
    static final String TAG = "UserDetailsFriendsInCommonActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String EXTRA_USER_PARCEL = Foursquared.PACKAGE_NAME
        + ".UserDetailsFriendsInCommonActivity.EXTRA_USER_PARCEL";
    
    private StateHolder mStateHolder;
    private FriendListAdapter mListAdapter;
    private ScrollView mLayoutEmpty;
    
    private static final int MENU_REFRESH = 0;


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
            mStateHolder.setActivity(this);
        } else {
            mStateHolder = new StateHolder();
            if (getIntent().hasExtra(EXTRA_USER_PARCEL)) {
                 mStateHolder.setUser((User)getIntent().getParcelableExtra(EXTRA_USER_PARCEL));
                 if (mStateHolder.getUser().getFriendsInCommon() == null || 
                     mStateHolder.getUser().getFriendsInCommon().size() == 0) {
                     Log.e(TAG, TAG + " requires user parcel have friends in common size > 0.");
                     finish();
                     return;
                 }
            } else {
                Log.e(TAG, TAG + " requires user parcel in intent extras.");
                finish();
                return;
            }
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
        LayoutInflater inflater = LayoutInflater.from(this);
        
        mLayoutEmpty = (ScrollView)inflater.inflate(R.layout.user_details_friends_activity_empty, 
                null);     
        mLayoutEmpty.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        
        mListAdapter = new FriendListAdapter(this, 
            ((Foursquared) getApplication()).getRemoteResourceManager());
        if (mStateHolder.getFriendsInCommonOnly()) {
            mListAdapter.setGroup(mStateHolder.getUser().getFriendsInCommon());
        } else {
            mListAdapter.setGroup(mStateHolder.getAllFriends());
            if (mStateHolder.getAllFriends().size() == 0) {
                if (mStateHolder.getRanOnce()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        }
        
        SegmentedButton buttons = getHeaderButton();
        buttons.clearButtons();
        buttons.addButtons(
                getString(R.string.user_details_friends_in_common_common_friends),
                getString(R.string.user_details_friends_in_common_all_friends));
        if (mStateHolder.getFriendsInCommonOnly()) {
            buttons.setPushedButtonIndex(0);
        } else {
            buttons.setPushedButtonIndex(1);
        }

        buttons.setOnClickListener(new OnClickListenerSegmentedButton() {
            @Override
            public void onClick(int index) {
                if (index == 0) {
                    mStateHolder.setFriendsInCommonOnly(true);
                    mListAdapter.setGroup(mStateHolder.getUser().getFriendsInCommon());
                } else {
                    mStateHolder.setFriendsInCommonOnly(false);
                    mListAdapter.setGroup(mStateHolder.getAllFriends());
                    if (mStateHolder.getAllFriends().size() < 1) {
                        if (mStateHolder.getRanOnce()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTaskAllFriends(UserDetailsFriendsInCommonActivity.this);
                        }
                    }
                }
                
                mListAdapter.notifyDataSetChanged();
                getListView().setSelection(0);
            }
        });

        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setSmoothScrollbarEnabled(false);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                User user = (User) parent.getAdapter().getItem(position);
                Intent intent = new Intent(UserDetailsFriendsInCommonActivity.this, UserDetailsActivity.class);
                intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, user);
                intent.putExtra(UserDetailsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS, true);
                startActivity(intent);
            }
        });

        if (mStateHolder.getIsRunningTaskAllFriends()) {
            setProgressBarIndeterminateVisibility(true);
        } else {
            setProgressBarIndeterminateVisibility(false);
        }

        setTitle(getString(R.string.user_details_friends_in_common_title, mStateHolder.getUser().getFirstname()));
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.refresh)
            .setIcon(R.drawable.ic_menu_refresh);
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                if (!mStateHolder.getFriendsInCommonOnly()) {
                    mStateHolder.startTaskAllFriends(this);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
    
    private void onStartTaskAllFriends() {
        mStateHolder.setIsRunningTaskAllFriends(true);
        setProgressBarIndeterminateVisibility(true);
        setLoadingView();
    }
    
    private void onTaskAllFriendsComplete(Group<User> allFriends, Exception ex) {
        setProgressBarIndeterminateVisibility(false);
        mStateHolder.setRanOnce(true);
        mStateHolder.setIsRunningTaskAllFriends(false);

        if (allFriends != null) {
            mStateHolder.setAllFriends(allFriends);
        } else {
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        SegmentedButton buttons = getHeaderButton();
        if (buttons.getSelectedButtonIndex() == 1) {
            mListAdapter.setGroup(mStateHolder.getAllFriends());
            if (mStateHolder.getAllFriends().size() == 0) {
                if (mStateHolder.getRanOnce()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        }
    }
    
    /**
     * Gets friends of the current user we're working for.
     */
    private static class TaskAllFriends extends AsyncTask<Void, Void, Group<User>> {

        private UserDetailsFriendsInCommonActivity mActivity;
        private String mUserId;
        private Exception mReason;

        public TaskAllFriends(UserDetailsFriendsInCommonActivity activity, String userId) {
            mActivity = activity;
            mUserId = userId;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.onStartTaskAllFriends();
        }

        @Override
        protected Group<User> doInBackground(Void... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();
                return foursquare.friends(
                    mUserId, LocationUtils.createFoursquareLocation(foursquared.getLastKnownLocation()));
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Group<User> allFriends) {
            if (mActivity != null) {
                mActivity.onTaskAllFriendsComplete(allFriends, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskAllFriendsComplete(null, mReason);
            }
        }
        
        public void setActivity(UserDetailsFriendsInCommonActivity activity) {
            mActivity = activity;
        }
    }
    
    
    private static class StateHolder {
        private User mUser;
        private Group<User> mAllFriends;
        
        private TaskAllFriends mTaskAllFriends;
        private boolean mIsRunningTaskAllFriends;
        private boolean mRanOnceTaskAllFriends;
        private boolean mFriendsInCommonOnly;
        
        
        public StateHolder() {
            mAllFriends = new Group<User>();
            mIsRunningTaskAllFriends = false;
            mRanOnceTaskAllFriends = false;
            mFriendsInCommonOnly = true;
        }
        
        public User getUser() {
            return mUser;
        }
        
        public void setUser(User user) {
            mUser = user;
        }
        
        public Group<User> getAllFriends() {
            return mAllFriends;
        }
        
        public void setAllFriends(Group<User> allFriends) {
            mAllFriends = allFriends;
        }
        
        public void startTaskAllFriends(UserDetailsFriendsInCommonActivity activity) {
            if (!mIsRunningTaskAllFriends) {
                mIsRunningTaskAllFriends = true;
                mTaskAllFriends = new TaskAllFriends(activity, mUser.getId());
                mTaskAllFriends.execute();
            }
        }

        public void setActivity(UserDetailsFriendsInCommonActivity activity) {
            if (mTaskAllFriends != null) {
                mTaskAllFriends.setActivity(activity);
            }
        }

        public boolean getIsRunningTaskAllFriends() {
            return mIsRunningTaskAllFriends;
        }
        
        public void setIsRunningTaskAllFriends(boolean isRunning) {
            mIsRunningTaskAllFriends = isRunning;
        }
        
        public void cancelTasks() {
            if (mTaskAllFriends != null) {
                mTaskAllFriends.setActivity(null);
                mTaskAllFriends.cancel(true);
            }
        }
        
        public boolean getRanOnce() {
            return mRanOnceTaskAllFriends;
        }
        
        public void setRanOnce(boolean ranOnce) {
            mRanOnceTaskAllFriends = ranOnce;
        }
        
        public boolean getFriendsInCommonOnly() {
            return mFriendsInCommonOnly;
        }
        
        public void setFriendsInCommonOnly(boolean friendsInCommonOnly) {
            mFriendsInCommonOnly = friendsInCommonOnly;
        }
    }
}
