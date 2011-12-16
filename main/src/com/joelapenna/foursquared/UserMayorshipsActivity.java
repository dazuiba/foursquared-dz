/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.app.LoadableListActivity;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.widget.SeparatedListAdapter;
import com.joelapenna.foursquared.widget.VenueListAdapter;

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

import java.util.ArrayList;

/**
 * Shows a list of venues that the specified user is mayor of.
 * We can fetch these ourselves given a userId, or work from
 * a venue array parcel.
 * 
 * @date March 15, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class UserMayorshipsActivity extends LoadableListActivity {
    static final String TAG = "UserMayorshipsActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String EXTRA_USER_ID = Foursquared.PACKAGE_NAME
        + ".UserMayorshipsActivity.EXTRA_USER_ID";
    public static final String EXTRA_USER_NAME = Foursquared.PACKAGE_NAME
        + ".UserMayorshipsActivity.EXTRA_USER_NAME";

    public static final String EXTRA_VENUE_LIST_PARCEL = Foursquared.PACKAGE_NAME
        + ".UserMayorshipsActivity.EXTRA_VENUE_LIST_PARCEL";

    private StateHolder mStateHolder;
    private SeparatedListAdapter mListAdapter;

    
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
            mStateHolder.setActivityForTaskVenues(this);
        } else {

            if (getIntent().hasExtra(EXTRA_USER_ID) && getIntent().hasExtra(EXTRA_USER_NAME)) {
                mStateHolder = new StateHolder(
                        getIntent().getStringExtra(EXTRA_USER_ID),
                        getIntent().getStringExtra(EXTRA_USER_NAME));
            } else {
                Log.e(TAG, "UserMayorships requires a userid in its intent extras.");
                finish();
                return;
            }
            
            if (getIntent().getExtras().containsKey(EXTRA_VENUE_LIST_PARCEL)) {
                // Can't jump from ArrayList to Group, argh.
                ArrayList<Venue> venues = getIntent().getExtras().getParcelableArrayList(
                        EXTRA_VENUE_LIST_PARCEL);
                Group<Venue> group = new Group<Venue>();
                for (Venue it : venues) {
                    group.add(it);
                }
                mStateHolder.setVenues(group);
                
            } else {
                mStateHolder.startTaskVenues(this);
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
            unregisterReceiver(mLoggedOutReceiver);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTaskVenues(null);
        return mStateHolder;
    }

    private void ensureUi() {
        mListAdapter = new SeparatedListAdapter(this);
        VenueListAdapter adapter = new VenueListAdapter(this, 
            ((Foursquared) getApplication()).getRemoteResourceManager());
        if (mStateHolder.getVenues().size() > 0) {
            adapter.setGroup(mStateHolder.getVenues());
            mListAdapter.addSection(
                getResources().getString(R.string.user_mayorships_activity_adapter_title), 
                adapter);
        }
        
        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setSmoothScrollbarEnabled(true);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Venue venue = (Venue)mListAdapter.getItem(position);
                
                Intent intent = new Intent(UserMayorshipsActivity.this, VenueActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_PARTIAL, venue);
                startActivity(intent);
            }
        });
        
        if (mStateHolder.getIsRunningVenuesTask()) {
            setLoadingView();
        } else if (mStateHolder.getFetchedVenuesOnce() && mStateHolder.getVenues().size() == 0) {
            setEmptyView();
        }

        setTitle(getString(R.string.user_mayorships_activity_title, mStateHolder.getUsername()));
    }

    private void onVenuesTaskComplete(User user, Exception ex) {
        mListAdapter.removeObserver();
        mListAdapter = new SeparatedListAdapter(this);
        
        if (user != null) {
            mStateHolder.setVenues(user.getMayorships());
        }
        else {
            mStateHolder.setVenues(new Group<Venue>());
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        if (mStateHolder.getVenues().size() > 0) {
            VenueListAdapter adapter = new VenueListAdapter(this, 
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(mStateHolder.getVenues());
            mListAdapter.addSection(
                    getResources().getString(R.string.user_mayorships_activity_adapter_title), 
                    adapter);
        }
        getListView().setAdapter(mListAdapter);
        
        mStateHolder.setIsRunningVenuesTask(false);
        mStateHolder.setFetchedVenuesOnce(true);
        
        // TODO: We can probably tighten this up by just calling ensureUI() again.
        if (mStateHolder.getVenues().size() == 0) {
            setEmptyView();
        }
    }
    
    @Override
    public int getNoSearchResultsStringId() {
        return R.string.user_mayorships_activity_no_info;
    }
    
    /**
     * Gets venues that the current user is mayor of.
     */
    private static class VenuesTask extends AsyncTask<String, Void, User> {

        private UserMayorshipsActivity mActivity;
        private Exception mReason;

        public VenuesTask(UserMayorshipsActivity activity) {
            mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.setLoadingView();
        }

        @Override
        protected User doInBackground(String... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();
                return foursquare.user(params[0], true, false, false,
                        LocationUtils.createFoursquareLocation(foursquared.getLastKnownLocation()));
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(User user) {
            if (mActivity != null) {
                mActivity.onVenuesTaskComplete(user, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onVenuesTaskComplete(null, mReason);
            }
        }
        
        public void setActivity(UserMayorshipsActivity activity) {
            mActivity = activity;
        }
    }
    
    
    private static class StateHolder {
        
        private String mUserId;
        private String mUsername;
        private Group<Venue> mVenues;
        
        private VenuesTask mTaskVenues;
        private boolean mIsRunningVenuesTask;
        private boolean mFetchedVenuesOnce;
        
        
        public StateHolder(String userId, String username) {
            mUserId = userId;
            mUsername = username;
            mIsRunningVenuesTask = false;
            mFetchedVenuesOnce = false;
            mVenues = new Group<Venue>();
        }
        
        public String getUsername() {
            return mUsername;
        }
        
        public Group<Venue> getVenues() {
            return mVenues;
        }
        
        public void setVenues(Group<Venue> venues) {
            mVenues = venues;
        }
        
        public void startTaskVenues(UserMayorshipsActivity activity) {
            mIsRunningVenuesTask = true;
            mTaskVenues = new VenuesTask(activity);
            mTaskVenues.execute(mUserId);
        }

        public void setActivityForTaskVenues(UserMayorshipsActivity activity) {
            if (mTaskVenues != null) {
                mTaskVenues.setActivity(activity);
            }
        }

        public void setIsRunningVenuesTask(boolean isRunning) {
            mIsRunningVenuesTask = isRunning;
        }

        public boolean getIsRunningVenuesTask() {
            return mIsRunningVenuesTask;
        }
        
        public void setFetchedVenuesOnce(boolean fetchedOnce) {
            mFetchedVenuesOnce = fetchedOnce;
        }
        
        public boolean getFetchedVenuesOnce() {
            return mFetchedVenuesOnce;
        }
        
        public void cancelTasks() {
            if (mTaskVenues != null) {
                mTaskVenues.setActivity(null);
                mTaskVenues.cancel(true);
            }
        }
    }
}
