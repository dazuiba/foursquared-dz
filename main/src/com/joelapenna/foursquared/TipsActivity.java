/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquared.app.LoadableListActivityWithViewAndHeader;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.MenuUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.widget.SegmentedButton;
import com.joelapenna.foursquared.widget.SegmentedButton.OnClickListenerSegmentedButton;
import com.joelapenna.foursquared.widget.TipsListAdapter;

import android.app.Activity;
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

import java.util.Observable;
import java.util.Observer;

/**
 * Shows a list of nearby tips. User can sort tips by friends-only.
 * 
 * @date August 31, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class TipsActivity extends LoadableListActivityWithViewAndHeader {
    static final String TAG = "TipsActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    private static final int ACTIVITY_TIP = 500;
    
    private StateHolder mStateHolder;
    private TipsListAdapter mListAdapter;
    private SearchLocationObserver mSearchLocationObserver = new SearchLocationObserver();
    private View mLayoutEmpty;
    
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
            mStateHolder.setFriendsOnly(true);
        }

        ensureUi();

        // Friend tips is shown first by default so auto-fetch it if necessary.
        if (!mStateHolder.getRanOnceTipsFriends()) {
            mStateHolder.startTaskTips(this, true);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();

        ((Foursquared) getApplication()).requestLocationUpdates(mSearchLocationObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        
        ((Foursquared) getApplication()).removeLocationUpdates(mSearchLocationObserver);
        
        if (isFinishing()) {
            mStateHolder.cancelTasks();
            mListAdapter.removeObserver();
            unregisterReceiver(mLoggedOutReceiver);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivity(null);
        return mStateHolder;
    }

    private void ensureUi() {
        LayoutInflater inflater = LayoutInflater.from(this);
        
        mLayoutEmpty = inflater.inflate(R.layout.tips_activity_empty, null);     
        mLayoutEmpty.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        
        mListAdapter = new TipsListAdapter(this, 
            ((Foursquared) getApplication()).getRemoteResourceManager(), R.layout.tip_list_item);
        if (mStateHolder.getFriendsOnly()) {
            mListAdapter.setGroup(mStateHolder.getTipsFriends());
            if (mStateHolder.getTipsFriends().size() == 0) {
                if (mStateHolder.getRanOnceTipsFriends()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        } else {
            mListAdapter.setGroup(mStateHolder.getTipsEveryone());
            if (mStateHolder.getTipsEveryone().size() == 0) {
                if (mStateHolder.getRanOnceTipsEveryone()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        }
        
        SegmentedButton buttons = getHeaderButton();
        buttons.clearButtons();
        buttons.addButtons(
                getString(R.string.tips_activity_btn_friends_only),
                getString(R.string.tips_activity_btn_everyone));
        if (mStateHolder.mFriendsOnly) {
            buttons.setPushedButtonIndex(0);
        } else {
            buttons.setPushedButtonIndex(1);
        }

        buttons.setOnClickListener(new OnClickListenerSegmentedButton() {
            @Override
            public void onClick(int index) {
                if (index == 0) {
                    mStateHolder.setFriendsOnly(true);
                    mListAdapter.setGroup(mStateHolder.getTipsFriends());
                    if (mStateHolder.getTipsFriends().size() < 1) {
                        if (mStateHolder.getRanOnceTipsFriends()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTaskTips(TipsActivity.this, true);
                        }
                    }
                } else {
                    mStateHolder.setFriendsOnly(false);
                    mListAdapter.setGroup(mStateHolder.getTipsEveryone());
                    if (mStateHolder.getTipsEveryone().size() < 1) {
                        if (mStateHolder.getRanOnceTipsEveryone()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTaskTips(TipsActivity.this, false);
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
                Tip tip = (Tip) parent.getAdapter().getItem(position);
                Intent intent = new Intent(TipsActivity.this, TipActivity.class);
                intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, tip);
                startActivityForResult(intent, ACTIVITY_TIP);
            }
        });

        if (mStateHolder.getIsRunningTaskTipsFriends() || 
            mStateHolder.getIsRunningTaskTipsEveryone()) {
            setProgressBarIndeterminateVisibility(true);
        } else {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.refresh)
            .setIcon(R.drawable.ic_menu_refresh);
        MenuUtils.addPreferencesToMenu(this, menu);
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                mStateHolder.startTaskTips(this, mStateHolder.getFriendsOnly());
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	// We don't care about the returned to-do (if any) since we're not bound
    	// to a venue in this activity for update. We just update the status member
    	// of the target tip.
        if (requestCode == ACTIVITY_TIP && resultCode == Activity.RESULT_OK) {
        	if (data.hasExtra(TipActivity.EXTRA_TIP_RETURNED)) {
        		Log.d(TAG, "onActivityResult(), return tip intent extra found, processing.");
        		updateTip((Tip)data.getParcelableExtra(TipActivity.EXTRA_TIP_RETURNED));
        	} else {
        		Log.d(TAG, "onActivityResult(), no return tip intent extra found.");
        	}
        }
    }
    
    private void updateTip(Tip tip) {
        mStateHolder.updateTip(tip);
        mListAdapter.notifyDataSetInvalidated();
    }
    
    private void onStartTaskTips() {
        if (mListAdapter != null) {
            if (mStateHolder.getFriendsOnly()) {
                mStateHolder.setIsRunningTaskTipsFriends(true);
                mListAdapter.setGroup(mStateHolder.getTipsFriends());
            } else {
                mStateHolder.setIsRunningTaskTipsEveryone(true);
                mListAdapter.setGroup(mStateHolder.getTipsEveryone());
            }
            mListAdapter.notifyDataSetChanged();
        }

        setProgressBarIndeterminateVisibility(true);
        setLoadingView();
    }
    
    private void onTaskTipsComplete(Group<Tip> group, boolean friendsOnly, Exception ex) {
        SegmentedButton buttons = getHeaderButton();
        
        boolean update = false;
        if (group != null) {
            if (friendsOnly) {
                mStateHolder.setTipsFriends(group);
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getTipsFriends());
                    update = true;
                }
            } else {
                mStateHolder.setTipsEveryone(group);
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getTipsEveryone());
                    update = true;
                }
            }
        }
        else {
            if (friendsOnly) {
                mStateHolder.setTipsFriends(new Group<Tip>());
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getTipsFriends());
                    update = true;
                }
            } else {
                mStateHolder.setTipsEveryone(new Group<Tip>());
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getTipsEveryone());
                    update = true;
                }
            }
            
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        if (friendsOnly) {
            mStateHolder.setIsRunningTaskTipsFriends(false);
            mStateHolder.setRanOnceTipsFriends(true);
            if (mStateHolder.getTipsFriends().size() == 0 && 
                    buttons.getSelectedButtonIndex() == 0) {
                setEmptyView(mLayoutEmpty);
            }
        } else {
            mStateHolder.setIsRunningTaskTipsEveryone(false);
            mStateHolder.setRanOnceTipsEveryone(true);
            if (mStateHolder.getTipsEveryone().size() == 0 &&
                    buttons.getSelectedButtonIndex() == 1) {
                setEmptyView(mLayoutEmpty);
            }
        }
        
        if (update) {
            mListAdapter.notifyDataSetChanged();
            getListView().setSelection(0);
        }
        
        if (!mStateHolder.getIsRunningTaskTipsFriends() &&
            !mStateHolder.getIsRunningTaskTipsEveryone()) {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    /**
     * Gets friends of the current user we're working for.
     */
    private static class TaskTips extends AsyncTask<Void, Void, Group<Tip>> {

        private TipsActivity mActivity;
        private boolean mFriendsOnly;
        private Exception mReason;

        public TaskTips(TipsActivity activity, boolean friendsOnly) {
            mActivity = activity;
            mFriendsOnly = friendsOnly;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.onStartTaskTips();
        }

        @Override
        protected Group<Tip> doInBackground(Void... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();

                Location loc = foursquared.getLastKnownLocation();
                if (loc == null) {
                    try { Thread.sleep(3000); } catch (InterruptedException ex) {}
                    loc = foursquared.getLastKnownLocation();
                    if (loc == null) {
                        throw new FoursquareException("Your location could not be determined!");
                    }
                } 
                
                return foursquare.tips(
                        LocationUtils.createFoursquareLocation(loc), 
                        null,
                        mFriendsOnly ? "friends" : "nearby",
                        null,
                        30);
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Group<Tip> tips) {
            if (mActivity != null) {
                mActivity.onTaskTipsComplete(tips, mFriendsOnly, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskTipsComplete(null, mFriendsOnly, mReason);
            }
        }
        
        public void setActivity(TipsActivity activity) {
            mActivity = activity;
        }
    }
    
    
    private static class StateHolder {
        
        /** Tips by friends. */
        private Group<Tip> mTipsFriends;
        
        /** Tips by everyone. */
        private Group<Tip> mTipsEveryone;
        
        private TaskTips mTaskTipsFriends;
        private TaskTips mTaskTipsEveryone;
        private boolean mIsRunningTaskTipsFriends;
        private boolean mIsRunningTaskTipsEveryone;
        
        private boolean mFriendsOnly;

        private boolean mRanOnceTipsFriends;
        private boolean mRanOnceTipsEveryone;
        
        
        public StateHolder() {
            mIsRunningTaskTipsFriends = false;
            mIsRunningTaskTipsEveryone = false;
            mRanOnceTipsFriends = false;
            mRanOnceTipsEveryone = false;
            mTipsFriends = new Group<Tip>();
            mTipsEveryone = new Group<Tip>();
            mFriendsOnly = true;
        }
        
        public Group<Tip> getTipsFriends() {
            return mTipsFriends;
        }
        
        public void setTipsFriends(Group<Tip> tipsFriends) {
            mTipsFriends = tipsFriends;
        }
        
        public Group<Tip> getTipsEveryone() {
            return mTipsEveryone;
        }
        
        public void setTipsEveryone(Group<Tip> tipsEveryone) {
            mTipsEveryone = tipsEveryone;
        }
        
        public void startTaskTips(TipsActivity activity,
                                  boolean friendsOnly) {
            if (friendsOnly) {
                if (mIsRunningTaskTipsFriends) {
                    return;
                }
                mIsRunningTaskTipsFriends = true;
                mTaskTipsFriends = new TaskTips(activity, friendsOnly);
                mTaskTipsFriends.execute();
            } else {
                if (mIsRunningTaskTipsEveryone) {
                    return;
                }
                mIsRunningTaskTipsEveryone = true;
                mTaskTipsEveryone = new TaskTips(activity, friendsOnly);
                mTaskTipsEveryone.execute();
            }
        }

        public void setActivity(TipsActivity activity) {
            if (mTaskTipsFriends != null) {
                mTaskTipsFriends.setActivity(activity);
            }
            if (mTaskTipsEveryone != null) {
                mTaskTipsEveryone.setActivity(activity);
            }
        }

        public boolean getIsRunningTaskTipsFriends() {
            return mIsRunningTaskTipsFriends;
        }
        
        public void setIsRunningTaskTipsFriends(boolean isRunning) {
            mIsRunningTaskTipsFriends = isRunning;
        }

        public boolean getIsRunningTaskTipsEveryone() {
            return mIsRunningTaskTipsEveryone;
        }
        
        public void setIsRunningTaskTipsEveryone(boolean isRunning) {
            mIsRunningTaskTipsEveryone = isRunning;
        }
        
        public void cancelTasks() {
            if (mTaskTipsFriends != null) {
                mTaskTipsFriends.setActivity(null);
                mTaskTipsFriends.cancel(true);
            }
            if (mTaskTipsEveryone != null) {
                mTaskTipsEveryone.setActivity(null);
                mTaskTipsEveryone.cancel(true);
            }
        }
        
        public boolean getFriendsOnly() {
            return mFriendsOnly;
        }
        
        public void setFriendsOnly(boolean friendsOnly) {
            mFriendsOnly = friendsOnly;
        }
        
        public boolean getRanOnceTipsFriends() {
            return mRanOnceTipsFriends;
        }
        
        public void setRanOnceTipsFriends(boolean ranOnce) {
            mRanOnceTipsFriends = ranOnce;
        }
        
        public boolean getRanOnceTipsEveryone() {
            return mRanOnceTipsEveryone;
        }
        
        public void setRanOnceTipsEveryone(boolean ranOnce) {
            mRanOnceTipsEveryone = ranOnce;
        }
        
        public void updateTip(Tip tip) {
            updateTipFromArray(tip, mTipsFriends);
            updateTipFromArray(tip, mTipsEveryone);
        }
        
        private void updateTipFromArray(Tip tip, Group<Tip> target) {
            for (Tip it : target) {
                if (it.getId().equals(tip.getId())) {
                	it.setStatus(tip.getStatus());
                    break;
                }
            }
        }
    }
    
    /** 
     * This is really just a dummy observer to get the GPS running
     * since this is the new splash page. After getting a fix, we
     * might want to stop registering this observer thereafter so
     * it doesn't annoy the user too much.
     */
    private class SearchLocationObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
        }
    }
}
