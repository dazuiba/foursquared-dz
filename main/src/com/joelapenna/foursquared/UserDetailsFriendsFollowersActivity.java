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
import com.joelapenna.foursquared.util.UserUtils;
import com.joelapenna.foursquared.widget.FriendActionableListAdapter;
import com.joelapenna.foursquared.widget.SegmentedButton;
import com.joelapenna.foursquared.widget.SegmentedButton.OnClickListenerSegmentedButton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the logged-in user's followers and friends. If the user has no followers, then 
 * they should just be shown UserDetailsFriendsActivity directly.
 * 
 * @date September 25, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class UserDetailsFriendsFollowersActivity extends LoadableListActivityWithViewAndHeader {
    static final String TAG = "UserDetailsFriendsFollowersActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    public static final String EXTRA_USER_NAME = Foursquared.PACKAGE_NAME
        + ".UserDetailsFriendsFollowersActivity.EXTRA_USER_NAME";
    
    private StateHolder mStateHolder;
    private FriendActionableListAdapter mListAdapter;
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
            if (getIntent().hasExtra(EXTRA_USER_NAME)) {
                mStateHolder = new StateHolder(getIntent().getStringExtra(EXTRA_USER_NAME));
                mStateHolder.setFollowersOnly(true);
            } else {
                Log.e(TAG, TAG + " requires user name in intent extras.");
                finish();
                return;
            }
        }

        ensureUi();

        // Friend tips is shown first by default so auto-fetch it if necessary.
        if (!mStateHolder.getRanOnceFollowers()) {
            mStateHolder.startTask(this, true);
        }
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
        
        mLayoutEmpty = (ScrollView)inflater.inflate(R.layout.user_details_friends_activity_empty, null);     
        mLayoutEmpty.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        
        mListAdapter = new FriendActionableListAdapter(
            this, mButtonRowClickHandler, ((Foursquared) getApplication()).getRemoteResourceManager());
        
        if (mStateHolder.getFollowersOnly()) {
            mListAdapter.setGroup(mStateHolder.getFollowers());
            if (mStateHolder.getFollowers().size() == 0) {
                if (mStateHolder.getRanOnceFollowers()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        } else {
            mListAdapter.setGroup(mStateHolder.getFriends());
            if (mStateHolder.getFriends().size() == 0) {
                if (mStateHolder.getRanOnceFriends()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        }
        
        SegmentedButton buttons = getHeaderButton();
        buttons.clearButtons();
        buttons.addButtons(
                getString(R.string.user_details_friends_followers_activity_followers),
                getString(R.string.user_details_friends_followers_activity_friends));
        if (mStateHolder.mFollowersOnly) {
            buttons.setPushedButtonIndex(0);
        } else {
            buttons.setPushedButtonIndex(1);
        }

        buttons.setOnClickListener(new OnClickListenerSegmentedButton() {
            @Override
            public void onClick(int index) {
                if (index == 0) {
                    mStateHolder.setFollowersOnly(true);
                    mListAdapter.setGroup(mStateHolder.getFollowers());
                    if (mStateHolder.getFollowers().size() < 1) {
                        if (mStateHolder.getRanOnceFollowers()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTask(UserDetailsFriendsFollowersActivity.this, true);
                        }
                    }
                } else {
                    mStateHolder.setFollowersOnly(false);
                    mListAdapter.setGroup(mStateHolder.getFriends());
                    if (mStateHolder.getFriends().size() < 1) {
                        if (mStateHolder.getRanOnceFriends()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTask(UserDetailsFriendsFollowersActivity.this, false);
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
        listView.setItemsCanFocus(false);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                User user = (User) parent.getAdapter().getItem(position);
                Intent intent = new Intent(UserDetailsFriendsFollowersActivity.this, UserDetailsActivity.class);
                intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, user);
                startActivity(intent);
            }
        });

        if (mStateHolder.getIsRunningTaskFollowers() || 
            mStateHolder.getIsRunningTaskFriends()) {
            setProgressBarIndeterminateVisibility(true);
        } else {
            setProgressBarIndeterminateVisibility(false);
        }
        
        setTitle(getString(R.string.user_details_friends_followers_activity_title,
                mStateHolder.getUsername()));
    }
    
    private FriendActionableListAdapter.ButtonRowClickHandler mButtonRowClickHandler = 
        new FriendActionableListAdapter.ButtonRowClickHandler() {

        @Override
        public void onBtnClickBtn1(User user) {
            if (mStateHolder.getFollowersOnly()) {
                updateFollowerStatus(user, true);
            }
        }
        
        @Override
        public void onBtnClickBtn2(User user) {
            if (mStateHolder.getFollowersOnly()) {
                updateFollowerStatus(user, false);
            }
        }
    };
    
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
                mStateHolder.startTask(this, mStateHolder.getFollowersOnly());
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
    
    /*
     * Leaving this out for now as it may be very costly for users with very large
     * friend networks.
    private void prepareResultIntent() {
        Group<User> followers = mStateHolder.getFollowers();
        Group<User> friends = mStateHolder.getFollowers();
        
        User[] followersArr = (User[])followers.toArray(new User[followers.size()]);
        User[] friendsArr = (User[])friends.toArray(new User[friends.size()]);

        Intent intent = new Intent();
        intent.putExtra(EXTRA_FOLLOWERS_RETURNED, followersArr);
        intent.putExtra(EXTRA_FRIENDS_RETURNED, friendsArr);
        setResult(CODE, intent);
    }
    */
    
    private void updateFollowerStatus(User user, boolean approve) {
        mStateHolder.startTaskUpdateFollower(this, user, approve);
        if (mStateHolder.getFollowersOnly()) {
            mListAdapter.notifyDataSetChanged();
            if (mStateHolder.getFollowers().size() == 0) {
                setEmptyView(mLayoutEmpty);
            }
        }
    }
    
    private void onStartTaskUsers() {
        if (mListAdapter != null) {
            if (mStateHolder.getFollowersOnly()) {
                mStateHolder.setIsRunningTaskFollowers(true);
                mListAdapter.setGroup(mStateHolder.getFollowers());
            } else {
                mStateHolder.setIsRunningTaskFriends(true);
                mListAdapter.setGroup(mStateHolder.getFriends());
            }
            mListAdapter.notifyDataSetChanged();
        }

        setProgressBarIndeterminateVisibility(true);
        setLoadingView();
    }
    
    private void onStartUpdateFollower() {
        setProgressBarIndeterminateVisibility(true);
    }
    
    private void onTaskUsersComplete(Group<User> group, boolean friendsOnly, Exception ex) {
        SegmentedButton buttons = getHeaderButton();
        
        boolean update = false;
        if (group != null) {
            if (friendsOnly) {
                mStateHolder.setFollowers(group);
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getFollowers());
                    update = true;
                }
            } else {
                mStateHolder.setFriends(group);
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getFriends());
                    update = true;
                }
            }
        }
        else {
            if (friendsOnly) {
                mStateHolder.setFollowers(new Group<User>());
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getFollowers());
                    update = true;
                }
            } else {
                mStateHolder.setFriends(new Group<User>());
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getFriends());
                    update = true;
                }
            }
            
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        if (friendsOnly) {
            mStateHolder.setIsRunningTaskFollowers(false);
            mStateHolder.setRanOnceFollowers(true);
            if (mStateHolder.getFollowers().size() == 0 && 
                    buttons.getSelectedButtonIndex() == 0) {
                setEmptyView(mLayoutEmpty);
            }
        } else {
            mStateHolder.setIsRunningTaskFriends(false);
            mStateHolder.setRanOnceFriends(true);
            if (mStateHolder.getFriends().size() == 0 &&
                    buttons.getSelectedButtonIndex() == 1) {
                setEmptyView(mLayoutEmpty);
            }
        }
        
        if (update) {
            mListAdapter.notifyDataSetChanged();
            getListView().setSelection(0);
        }
        
        if (!mStateHolder.areAnyTasksRunning()) {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    private void onTaskUpdateFollowerComplete(TaskUpdateFollower task, User user, 
            boolean approve, Exception ex) {
        
        if (user != null) {
            if (UserUtils.isFriend(user)) {
                if (mStateHolder.addFriend(user)) {
                    mListAdapter.notifyDataSetChanged();
                }
            }
        }
        
        mStateHolder.removeTaskUpdateFollower(task);
        
        if (!mStateHolder.areAnyTasksRunning()) {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    private static class TaskUsers extends AsyncTask<Void, Void, Group<User>> {

        private UserDetailsFriendsFollowersActivity mActivity;
        private boolean mFollowersOnly;
        private Exception mReason;

        public TaskUsers(UserDetailsFriendsFollowersActivity activity, boolean followersOnly) {
            mActivity = activity;
            mFollowersOnly = followersOnly;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.onStartTaskUsers();
        }

        @Override
        protected Group<User> doInBackground(Void... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();

                Location loc = foursquared.getLastKnownLocation();
                
                if (mFollowersOnly) {
                    return foursquare.friendRequests();
                } else {
                    return foursquare.friends(null, LocationUtils.createFoursquareLocation(loc));
                }
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Group<User> users) {
            if (mActivity != null) {
                mActivity.onTaskUsersComplete(users, mFollowersOnly, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskUsersComplete(null, mFollowersOnly, mReason);
            }
        }
        
        public void setActivity(UserDetailsFriendsFollowersActivity activity) {
            mActivity = activity;
        }
    }
    
    private static class TaskUpdateFollower extends AsyncTask<Void, Void, User> {

        private UserDetailsFriendsFollowersActivity mActivity;
        private String mUserId;
        private boolean mApprove;
        private Exception mReason;
        private boolean mDone;

        public TaskUpdateFollower(UserDetailsFriendsFollowersActivity activity, 
                                  String userId,
                                  boolean approve) {
            mActivity = activity;
            mUserId = userId;
            mApprove = approve;
            mDone = false;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.onStartUpdateFollower();
        }

        @Override
        protected User doInBackground(Void... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();

                if (mApprove) {
                    return foursquare.friendApprove(mUserId);
                } else {
                    return foursquare.friendDeny(mUserId);
                }

            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(User user) {
            if (mActivity != null) {
                mActivity.onTaskUpdateFollowerComplete(this, user, mApprove, mReason);
            }
            mDone = true;
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskUpdateFollowerComplete(this, null, mApprove, mReason);
            }
            mDone = true;
        }
        
        public void setActivity(UserDetailsFriendsFollowersActivity activity) {
            mActivity = activity;
        }

        public boolean getIsDone() {
            return mDone;
        }
    }
    
    private static class StateHolder {
        private String mUsername;
        private Group<User> mFollowers;
        private Group<User> mFriends;
        
        private TaskUsers mTaskFollowers;
        private TaskUsers mTaskFriends;
        private boolean mIsRunningTaskFollowers;
        private boolean mIsRunningTaskFriends;
        private boolean mFollowersOnly;
        private boolean mRanOnceFollowers;
        private boolean mRanOnceFriends;
        
        private List<TaskUpdateFollower> mTasksUpdateFollowers;
        
        
        public StateHolder(String username) {
            mUsername = username;
            mIsRunningTaskFollowers = false;
            mIsRunningTaskFriends = false;
            mRanOnceFriends = false;
            mRanOnceFollowers = false;
            mFollowers = new Group<User>();
            mFriends = new Group<User>();
            mFollowersOnly = true;
            mTasksUpdateFollowers = new ArrayList<TaskUpdateFollower>();
        }
        
        public String getUsername() {
            return mUsername;
        }
        
        public Group<User> getFollowers() {
            return mFollowers;
        }
        
        public void setFollowers(Group<User> followers) {
            mFollowers = followers;
        }
        
        public Group<User> getFriends() {
            return mFriends;
        }
        
        public void setFriends(Group<User> friends) {
            mFriends = friends;
        }
        
        public void startTask(UserDetailsFriendsFollowersActivity activity,
                              boolean followersOnly) {
            if (followersOnly) {
                if (mIsRunningTaskFollowers) {
                    return;
                }
                mIsRunningTaskFollowers = true;
                mTaskFollowers = new TaskUsers(activity, followersOnly);
                mTaskFollowers.execute();
            } else {
                if (mIsRunningTaskFriends) {
                    return;
                }
                mIsRunningTaskFriends = true;
                mTaskFriends = new TaskUsers(activity, followersOnly);
                mTaskFriends.execute();
            }
        }
        
        public void startTaskUpdateFollower(UserDetailsFriendsFollowersActivity activity,
                                            User user,
                                            boolean approve) {
            for (User it : mFollowers) {
                if (it.getId().equals(user.getId())) {
                    mFollowers.remove(it);
                    break;
                }
            }
            
            TaskUpdateFollower task = new TaskUpdateFollower(activity, user.getId(), approve);
            task.execute();
            mTasksUpdateFollowers.add(task);
        }

        public void setActivity(UserDetailsFriendsFollowersActivity activity) {
            if (mTaskFollowers != null) {
                mTaskFollowers.setActivity(activity);
            }
            if (mTaskFriends != null) {
                mTaskFriends.setActivity(activity);
            }
            for (TaskUpdateFollower it : mTasksUpdateFollowers) {
                it.setActivity(activity);
            }
        }

        public boolean getIsRunningTaskFollowers() {
            return mIsRunningTaskFollowers;
        }
        
        public void setIsRunningTaskFollowers(boolean isRunning) {
            mIsRunningTaskFollowers = isRunning;
        }

        public boolean getIsRunningTaskFriends() {
            return mIsRunningTaskFriends;
        }
        
        public void setIsRunningTaskFriends(boolean isRunning) {
            mIsRunningTaskFriends = isRunning;
        }
        
        public void cancelTasks() {
            if (mTaskFollowers != null) {
                mTaskFollowers.setActivity(null);
                mTaskFollowers.cancel(true);
            }
            if (mTaskFriends != null) {
                mTaskFriends.setActivity(null);
                mTaskFriends.cancel(true);
            }
            for (TaskUpdateFollower it : mTasksUpdateFollowers) {
                it.setActivity(null);
                it.cancel(true);
            }
        }
        
        public boolean getFollowersOnly() {
            return mFollowersOnly;
        }
        
        public void setFollowersOnly(boolean followersOnly) {
            mFollowersOnly = followersOnly;
        }
        
        public boolean getRanOnceFollowers() {
            return mRanOnceFollowers;
        }
        
        public void setRanOnceFollowers(boolean ranOnce) {
            mRanOnceFollowers = ranOnce;
        }
        
        public boolean getRanOnceFriends() {
            return mRanOnceFriends;
        }
        
        public void setRanOnceFriends(boolean ranOnce) {
            mRanOnceFriends = ranOnce;
        }
        
        public boolean areAnyTasksRunning() {
            return mIsRunningTaskFollowers || mIsRunningTaskFriends
                || mTasksUpdateFollowers.size() > 0;
        }
        
        public boolean addFriend(User user) {
            for (User it : mFriends) {
                if (it.getId().equals(user.getId())) {
                    return false;
                }
            }
            
            mFriends.add(user);
            return true;
        }
        
        public void removeTaskUpdateFollower(TaskUpdateFollower task) {
            mTasksUpdateFollowers.remove(task);
            
            // Try to cleanup anyone we missed, this could happen for a brief period
            // during rotation.
            for (int i = mTasksUpdateFollowers.size()-1; i > -1; i--) {
                if (mTasksUpdateFollowers.get(i).getIsDone()) {
                    mTasksUpdateFollowers.remove(i);
                }
            }
        }
    }
}
