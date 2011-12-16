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
 * Shows a tips of a user, but not the logged-in user. This is pretty much a copy-paste
 * of TipsActivity, but there are enough small differences to put it in its own activity.
 * The direction of this activity is unknown too, so separating i here.
 * 
 * @date September 23, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class UserDetailsTipsActivity extends LoadableListActivityWithViewAndHeader {
    static final String TAG = "UserDetailsTipsActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String INTENT_EXTRA_USER_ID = Foursquared.PACKAGE_NAME
            + ".UserDetailsTipsActivity.INTENT_EXTRA_USER_ID";
    public static final String INTENT_EXTRA_USER_NAME = Foursquared.PACKAGE_NAME
            + ".UserDetailsTipsActivity.INTENT_EXTRA_USER_NAME";
    
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
            if (getIntent().hasExtra(INTENT_EXTRA_USER_ID) && getIntent().hasExtra(INTENT_EXTRA_USER_NAME)) {
                mStateHolder = new StateHolder(
                        getIntent().getStringExtra(INTENT_EXTRA_USER_ID),
                        getIntent().getStringExtra(INTENT_EXTRA_USER_NAME));
                mStateHolder.setRecentOnly(true);
            } else {
                Log.e(TAG, TAG + " requires user ID and name in intent extras.");
                finish();
                return;
            }
        }

        ensureUi();

        // Friend tips is shown first by default so auto-fetch it if necessary.
        if (!mStateHolder.getRanOnceTipsRecent()) {
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
            ((Foursquared) getApplication()).getRemoteResourceManager(), R.layout.tip_venue_list_item);
        if (mStateHolder.getRecentOnly()) {
            mListAdapter.setGroup(mStateHolder.getTipsRecent());
            if (mStateHolder.getTipsRecent().size() == 0) {
                if (mStateHolder.getRanOnceTipsRecent()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        } else {
            mListAdapter.setGroup(mStateHolder.getTipsPopular());
            if (mStateHolder.getTipsPopular().size() == 0) {
                if (mStateHolder.getRanOnceTipsPopular()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        }
        
        SegmentedButton buttons = getHeaderButton();
        buttons.clearButtons();
        buttons.addButtons(
                getString(R.string.user_details_tips_activity_btn_recent),
                getString(R.string.user_details_tips_activity_btn_popular));
        if (mStateHolder.mRecentOnly) {
            buttons.setPushedButtonIndex(0);
        } else {
            buttons.setPushedButtonIndex(1);
        }

        buttons.setOnClickListener(new OnClickListenerSegmentedButton() {
            @Override
            public void onClick(int index) {
                if (index == 0) {
                    mStateHolder.setRecentOnly(true);
                    mListAdapter.setGroup(mStateHolder.getTipsRecent());
                    if (mStateHolder.getTipsRecent().size() < 1) {
                        if (mStateHolder.getRanOnceTipsRecent()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTaskTips(UserDetailsTipsActivity.this, true);
                        }
                    }
                } else {
                    mStateHolder.setRecentOnly(false);
                    mListAdapter.setGroup(mStateHolder.getTipsPopular());
                    if (mStateHolder.getTipsPopular().size() < 1) {
                        if (mStateHolder.getRanOnceTipsPopular()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTaskTips(UserDetailsTipsActivity.this, false);
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
                Intent intent = new Intent(UserDetailsTipsActivity.this, TipActivity.class);
                intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, tip);
                startActivityForResult(intent, ACTIVITY_TIP);
            }
        });

        if (mStateHolder.getIsRunningTaskTipsRecent() || 
            mStateHolder.getIsRunningTaskTipsPopular()) {
            setProgressBarIndeterminateVisibility(true);
        } else {
            setProgressBarIndeterminateVisibility(false);
        }

        setTitle(getString(R.string.user_details_tips_activity_title, mStateHolder.getUsername()));
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
                mStateHolder.startTaskTips(this, mStateHolder.getRecentOnly());
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
                Log.i(TAG, "onActivityResult(), return tip intent extra found, processing.");
                updateTip((Tip)data.getParcelableExtra(TipActivity.EXTRA_TIP_RETURNED));
            } else {
                Log.i(TAG, "onActivityResult(), no return tip intent extra found.");
            }
        }
    }
    
    private void updateTip(Tip tip) {
        mStateHolder.updateTip(tip);
        mListAdapter.notifyDataSetInvalidated();
    }
    
    private void onStartTaskTips() {
        if (mListAdapter != null) {
            if (mStateHolder.getRecentOnly()) {
                mStateHolder.setIsRunningTaskTipsRecent(true);
                mListAdapter.setGroup(mStateHolder.getTipsRecent());
            } else {
                mStateHolder.setIsRunningTaskTipsPopular(true);
                mListAdapter.setGroup(mStateHolder.getTipsPopular());
            }
            mListAdapter.notifyDataSetChanged();
        }

        setProgressBarIndeterminateVisibility(true);
        setLoadingView();
    }
    
    private void onTaskTipsComplete(Group<Tip> group, boolean recentOnly, Exception ex) {
        SegmentedButton buttons = getHeaderButton();
        
        boolean update = false;
        if (group != null) {
            if (recentOnly) {
                mStateHolder.setTipsRecent(group);
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getTipsRecent());
                    update = true;
                }
            } else {
                mStateHolder.setTipsPopular(group);
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getTipsPopular());
                    update = true;
                }
            }
        }
        else {
            if (recentOnly) {
                mStateHolder.setTipsRecent(new Group<Tip>());
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getTipsRecent());
                    update = true;
                }
            } else {
                mStateHolder.setTipsPopular(new Group<Tip>());
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getTipsPopular());
                    update = true;
                }
            }
            
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        if (recentOnly) {
            mStateHolder.setIsRunningTaskTipsRecent(false);
            mStateHolder.setRanOnceTipsRecent(true);
            if (mStateHolder.getTipsRecent().size() == 0 && 
                    buttons.getSelectedButtonIndex() == 0) {
                setEmptyView(mLayoutEmpty);
            }
        } else {
            mStateHolder.setIsRunningTaskTipsPopular(false);
            mStateHolder.setRanOnceTipsPopular(true);
            if (mStateHolder.getTipsPopular().size() == 0 &&
                    buttons.getSelectedButtonIndex() == 1) {
                setEmptyView(mLayoutEmpty);
            }
        }
        
        if (update) {
            mListAdapter.notifyDataSetChanged();
            getListView().setSelection(0);
        }
        
        if (!mStateHolder.getIsRunningTaskTipsRecent() &&
            !mStateHolder.getIsRunningTaskTipsPopular()) {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    
    private static class TaskTips extends AsyncTask<Void, Void, Group<Tip>> {

        private String mUserId;
        private UserDetailsTipsActivity mActivity;
        private boolean mRecentOnly;
        private Exception mReason;

        public TaskTips(UserDetailsTipsActivity activity, String userId, boolean recentOnly) {
            mActivity = activity;
            mUserId = userId;
            mRecentOnly = recentOnly;
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
                        mUserId,
                        "nearby",
                        mRecentOnly ? "recent" : "popular",
                        30);
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Group<Tip> tips) {
            if (mActivity != null) {
                mActivity.onTaskTipsComplete(tips, mRecentOnly, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskTipsComplete(null, mRecentOnly, mReason);
            }
        }
        
        public void setActivity(UserDetailsTipsActivity activity) {
            mActivity = activity;
        }
    }
    
    
    private static class StateHolder {
        
        private String mUserId;
        private String mUsername;
        private Group<Tip> mTipsRecent;
        private Group<Tip> mTipsPopular;
        private TaskTips mTaskTipsRecent;
        private TaskTips mTaskTipsPopular;
        private boolean mIsRunningTaskTipsRecent;
        private boolean mIsRunningTaskTipsPopular;
        private boolean mRecentOnly;
        private boolean mRanOnceTipsRecent;
        private boolean mRanOnceTipsPopular;
        
        
        public StateHolder(String userId, String username) {
            mUserId = userId;
            mUsername = username;
            mIsRunningTaskTipsRecent = false;
            mIsRunningTaskTipsPopular = false;
            mRanOnceTipsRecent = false;
            mRanOnceTipsPopular = false;
            mTipsRecent = new Group<Tip>();
            mTipsPopular = new Group<Tip>();
            mRecentOnly = true;
        }
        
        public String getUsername() {
            return mUsername;
        }
        
        public Group<Tip> getTipsRecent() {
            return mTipsRecent;
        }
        
        public void setTipsRecent(Group<Tip> tipsRecent) {
            mTipsRecent = tipsRecent;
        }
        
        public Group<Tip> getTipsPopular() {
            return mTipsPopular;
        }
        
        public void setTipsPopular(Group<Tip> tipsPopular) {
            mTipsPopular = tipsPopular;
        }
        
        public void startTaskTips(UserDetailsTipsActivity activity,
                                  boolean recentOnly) {
            if (recentOnly) {
                if (mIsRunningTaskTipsRecent) {
                    return;
                }
                mIsRunningTaskTipsRecent = true;
                mTaskTipsRecent = new TaskTips(activity, mUserId, recentOnly);
                mTaskTipsRecent.execute();
            } else {
                if (mIsRunningTaskTipsPopular) {
                    return;
                }
                mIsRunningTaskTipsPopular = true;
                mTaskTipsPopular = new TaskTips(activity, mUserId, recentOnly);
                mTaskTipsPopular.execute();
            }
        }

        public void setActivity(UserDetailsTipsActivity activity) {
            if (mTaskTipsRecent != null) {
                mTaskTipsRecent.setActivity(activity);
            }
            if (mTaskTipsPopular != null) {
                mTaskTipsPopular.setActivity(activity);
            }
        }

        public boolean getIsRunningTaskTipsRecent() {
            return mIsRunningTaskTipsRecent;
        }
        
        public void setIsRunningTaskTipsRecent(boolean isRunning) {
            mIsRunningTaskTipsRecent = isRunning;
        }

        public boolean getIsRunningTaskTipsPopular() {
            return mIsRunningTaskTipsPopular;
        }
        
        public void setIsRunningTaskTipsPopular(boolean isRunning) {
            mIsRunningTaskTipsPopular = isRunning;
        }
        
        public void cancelTasks() {
            if (mTaskTipsRecent != null) {
                mTaskTipsRecent.setActivity(null);
                mTaskTipsRecent.cancel(true);
            }
            if (mTaskTipsPopular != null) {
                mTaskTipsPopular.setActivity(null);
                mTaskTipsPopular.cancel(true);
            }
        }
        
        public boolean getRecentOnly() {
            return mRecentOnly;
        }
        
        public void setRecentOnly(boolean recentOnly) {
            mRecentOnly = recentOnly;
        }
        
        public boolean getRanOnceTipsRecent() {
            return mRanOnceTipsRecent;
        }
        
        public void setRanOnceTipsRecent(boolean ranOnce) {
            mRanOnceTipsRecent = ranOnce;
        }
        
        public boolean getRanOnceTipsPopular() {
            return mRanOnceTipsPopular;
        }
        
        public void setRanOnceTipsPopular(boolean ranOnce) {
            mRanOnceTipsPopular = ranOnce;
        }
        
        public void updateTip(Tip tip) {
            updateTipFromArray(tip, mTipsRecent);
            updateTipFromArray(tip, mTipsPopular);
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

    private class SearchLocationObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
        }
    }
}
