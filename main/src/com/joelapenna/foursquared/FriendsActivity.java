/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.Checkin;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquared.app.LoadableListActivityWithViewAndHeader;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.CheckinTimestampSort;
import com.joelapenna.foursquared.util.Comparators;
import com.joelapenna.foursquared.util.MenuUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.UiUtil;
import com.joelapenna.foursquared.widget.CheckinListAdapter;
import com.joelapenna.foursquared.widget.SegmentedButton;
import com.joelapenna.foursquared.widget.SegmentedButton.OnClickListenerSegmentedButton;
import com.joelapenna.foursquared.widget.SeparatedListAdapter;

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
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *         -Added dummy location observer, new menu icon logic, 
 *          links to new user activity (3/10/2010).
 *         -Sorting checkins by distance/time. (3/18/2010).
 *         -Added option to sort by server response, or by distance. (6/10/2010).
 *         -Reformatted/refactored. (9/22/2010).
 */
public class FriendsActivity extends LoadableListActivityWithViewAndHeader {
    static final String TAG = "FriendsActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final int CITY_RADIUS_IN_METERS = 20 * 1000; // 20km
    private static final long SLEEP_TIME_IF_NO_LOCATION = 3000L;

    private static final int MENU_GROUP_SEARCH = 0;
    private static final int MENU_REFRESH      = 1;
    private static final int MENU_SHOUT        = 2;
    private static final int MENU_MORE         = 3;
    
    private static final int MENU_MORE_MAP             = 20;
    private static final int MENU_MORE_LEADERBOARD     = 21;
    
    private static final int SORT_METHOD_RECENT = 0;
    private static final int SORT_METHOD_NEARBY = 1;
    
    
    private StateHolder mStateHolder;
    private SearchLocationObserver mSearchLocationObserver = new SearchLocationObserver();
    private LinkedHashMap<Integer, String> mMenuMoreSubitems;
    private SeparatedListAdapter mListAdapter;
    private ViewGroup mLayoutEmpty;

    
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

        if (getLastNonConfigurationInstance() != null) {
            mStateHolder = (StateHolder) getLastNonConfigurationInstance();
            mStateHolder.setActivity(this);
        } else {
        	mStateHolder = new StateHolder();
        	mStateHolder.setSortMethod(SORT_METHOD_RECENT);
        }

        ensureUi();
        
        Foursquared foursquared = (Foursquared)getApplication();
        if (foursquared.isReady()) {
            if (!mStateHolder.getRanOnce()) {
                mStateHolder.startTask(this);
            }
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
            mListAdapter.removeObserver();
            unregisterReceiver(mLoggedOutReceiver);
            mStateHolder.cancel();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(MENU_GROUP_SEARCH, MENU_REFRESH, Menu.NONE, R.string.refresh)
                .setIcon(R.drawable.ic_menu_refresh);
        menu.add(Menu.NONE, MENU_SHOUT, Menu.NONE, R.string.shout_action_label)
                .setIcon(R.drawable.ic_menu_shout);

        SubMenu menuMore = menu.addSubMenu(Menu.NONE, MENU_MORE, Menu.NONE, "More");
        menuMore.setIcon(android.R.drawable.ic_menu_more);
        for (Map.Entry<Integer, String> it : mMenuMoreSubitems.entrySet()) {
            menuMore.add(it.getValue());
        }
        
        MenuUtils.addPreferencesToMenu(this, menu);
 
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                mStateHolder.startTask(this);
                return true;
            case MENU_SHOUT:
                Intent intent = new Intent(this, CheckinOrShoutGatherInfoActivity.class);
                intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_IS_SHOUT, true);
                startActivity(intent);
                return true;
            case MENU_MORE:
                // Submenu items generate id zero, but we check on item title below.
                return true;
            default:
                if (item.getTitle().equals("Map")) {
                	Checkin[] checkins = (Checkin[])mStateHolder.getCheckins().toArray(
                			new Checkin[mStateHolder.getCheckins().size()]);
                	Intent intentMap = new Intent(FriendsActivity.this, FriendsMapActivity.class);
                	intentMap.putExtra(FriendsMapActivity.EXTRA_CHECKIN_PARCELS, checkins);
                	startActivity(intentMap);
                    return true;
                } else if (item.getTitle().equals(mMenuMoreSubitems.get(MENU_MORE_LEADERBOARD))) {
                    startActivity(new Intent(FriendsActivity.this, StatsActivity.class));
                    return true;
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
    	mStateHolder.setActivity(null);
        return mStateHolder;
    }
    
    @Override
    public int getNoSearchResultsStringId() {
        return R.string.no_friend_checkins;
    }

    private void ensureUi() {
       
        SegmentedButton buttons = getHeaderButton();
        buttons.clearButtons();
        buttons.addButtons(
                getString(R.string.friendsactivity_btn_recent),
                getString(R.string.friendsactivity_btn_nearby));
        if (mStateHolder.getSortMethod() == SORT_METHOD_RECENT) {
            buttons.setPushedButtonIndex(0);
        } else {
            buttons.setPushedButtonIndex(1);
        }
        
        buttons.setOnClickListener(new OnClickListenerSegmentedButton() {
            @Override
            public void onClick(int index) {
                if (index == 0) {
                    mStateHolder.setSortMethod(SORT_METHOD_RECENT);
                } else {
                    mStateHolder.setSortMethod(SORT_METHOD_NEARBY);
                }
                
                ensureUiListView();
            }
        });
        
        mMenuMoreSubitems = new LinkedHashMap<Integer, String>();
        mMenuMoreSubitems.put(MENU_MORE_MAP, getResources().getString(
                R.string.friendsactivity_menu_map));
        mMenuMoreSubitems.put(MENU_MORE_LEADERBOARD, getResources().getString(
                R.string.friendsactivity_menu_leaderboard));
        
        ensureUiListView();
    }
    
    private void ensureUiListView() {
        mListAdapter = new SeparatedListAdapter(this);
        if (mStateHolder.getSortMethod() == SORT_METHOD_RECENT) {
            sortCheckinsRecent(mStateHolder.getCheckins(), mListAdapter);
        } else {
            sortCheckinsDistance(mStateHolder.getCheckins(), mListAdapter);
        }
        
        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setDividerHeight(0);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Checkin checkin = (Checkin) parent.getAdapter().getItem(position);
                if (checkin.getUser() != null) {
                    Intent intent = new Intent(FriendsActivity.this, UserDetailsActivity.class);
                    intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, checkin.getUser());
                    intent.putExtra(UserDetailsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS, true);
                    startActivity(intent);
                }
            }
        });

        // Prepare our no-results view. Something odd is going on with the layout parameters though.
        // If we don't explicitly set the layout to be fill/fill after inflating, the layout jumps
        // to a wrap/wrap layout. Furthermore, sdk 3 crashes with the original layout using two
        // buttons in a horizontal LinearLayout.
        LayoutInflater inflater = LayoutInflater.from(this);
        if (UiUtil.sdkVersion() > 3) {
            mLayoutEmpty = (ScrollView)inflater.inflate(
                    R.layout.friends_activity_empty, null);
            
            Button btnAddFriends = (Button)mLayoutEmpty.findViewById(
                    R.id.friendsActivityEmptyBtnAddFriends);
            btnAddFriends.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(FriendsActivity.this, AddFriendsActivity.class);
                    startActivity(intent);
                }
            });
            
            Button btnFriendRequests = (Button)mLayoutEmpty.findViewById(
                    R.id.friendsActivityEmptyBtnFriendRequests);
            btnFriendRequests.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(FriendsActivity.this, FriendRequestsActivity.class);
                    startActivity(intent);
                }
            });
        } else {
            // Inflation on 1.5 is causing a lot of issues, dropping full layout.
            mLayoutEmpty = (ScrollView)inflater.inflate(
                    R.layout.friends_activity_empty_sdk3, null);
        }
        
        mLayoutEmpty.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        
        if (mListAdapter.getCount() == 0) {
            setEmptyView(mLayoutEmpty);
        }
        
        if (mStateHolder.getIsRunningTask()) {
            setProgressBarIndeterminateVisibility(true);
            if (!mStateHolder.getRanOnce()) {
                setLoadingView();
            }
        } else {
            setProgressBarIndeterminateVisibility(false);
        }
    }

    private void sortCheckinsRecent(Group<Checkin> checkins, SeparatedListAdapter listAdapter) {

        // Sort all by timestamp first.
        Collections.sort(checkins, Comparators.getCheckinRecencyComparator());
        
        // We'll group in different section adapters based on some time thresholds.
        Group<Checkin> recent = new Group<Checkin>();
        Group<Checkin> today = new Group<Checkin>();
        Group<Checkin> yesterday = new Group<Checkin>();
        Group<Checkin> older = new Group<Checkin>();
        Group<Checkin> other = new Group<Checkin>();
        CheckinTimestampSort timestamps = new CheckinTimestampSort();
        for (Checkin it : checkins) {

            // If we can't parse the distance value, it's possible that we
            // did not have a geolocation for the device at the time the
            // search was run. In this case just assume this friend is nearby
            // to sort them in the time buckets.
            int meters = 0;
            try {
                meters = Integer.parseInt(it.getDistance());
            } catch (NumberFormatException ex) {
                if (DEBUG) Log.d(TAG, "Couldn't parse distance for checkin during friend search.");
                meters = 0;
            }
  
            if (meters > CITY_RADIUS_IN_METERS) {
                other.add(it);
            } else {
                try { 
                    Date date = new Date(it.getCreated());
                    if (date.after(timestamps.getBoundaryRecent())) {
                        recent.add(it);
                    } else if (date.after(timestamps.getBoundaryToday())) {
                        today.add(it); 
                    } else if (date.after(timestamps.getBoundaryYesterday())) {
                        yesterday.add(it);
                    } else {
                        older.add(it);
                    }
                } catch (Exception ex) {
                    older.add(it);
                }
            }
        }
        
        if (recent.size() > 0) {
            CheckinListAdapter adapter = new CheckinListAdapter(this, 
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(recent);
            listAdapter.addSection(getResources().getString(
                    R.string.friendsactivity_title_sort_recent), adapter);
        }
        if (today.size() > 0) {
            CheckinListAdapter adapter = new CheckinListAdapter(this, 
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(today);
            listAdapter.addSection(getResources().getString(
                    R.string.friendsactivity_title_sort_today), adapter);
        }
        if (yesterday.size() > 0) {
            CheckinListAdapter adapter = new CheckinListAdapter(this, 
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(yesterday);
            listAdapter.addSection(getResources().getString(
                    R.string.friendsactivity_title_sort_yesterday), adapter);
        }
        if (older.size() > 0) {
            CheckinListAdapter adapter = new CheckinListAdapter(this, 
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(older);
            listAdapter.addSection(getResources().getString(
                    R.string.friendsactivity_title_sort_older), adapter);
        }
        if (other.size() > 0) {
            CheckinListAdapter adapter = new CheckinListAdapter(this, 
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(other);
            listAdapter.addSection(getResources().getString(
                    R.string.friendsactivity_title_sort_other_city), adapter);
        }
    }
    
    private void sortCheckinsDistance(Group<Checkin> checkins, SeparatedListAdapter listAdapter) {
        Collections.sort(checkins, Comparators.getCheckinDistanceComparator());
        
        Group<Checkin> nearby = new Group<Checkin>();
        CheckinListAdapter adapter = new CheckinListAdapter(this, 
                ((Foursquared) getApplication()).getRemoteResourceManager());
        for (Checkin it : checkins) {
            int meters = 0;
            try {
                meters = Integer.parseInt(it.getDistance());
            } catch (NumberFormatException ex) {
                if (DEBUG) Log.d(TAG, "Couldn't parse distance for checkin during friend search.");
                meters = 0;
            }
  
            if (meters < CITY_RADIUS_IN_METERS) {
                nearby.add(it);
            }
        }
        
        if (nearby.size() > 0) {
	        adapter.setGroup(nearby);
	        listAdapter.addSection(getResources().getString(
	                R.string.friendsactivity_title_sort_distance), adapter);
        }
    }
    
    private void onTaskStart() {
        setProgressBarIndeterminateVisibility(true);
        setLoadingView();
    }
    
    private void onTaskComplete(Group<Checkin> checkins, Exception ex) {
    	mStateHolder.setRanOnce(true);
    	mStateHolder.setIsRunningTask(false);
        setProgressBarIndeterminateVisibility(false);
    	
    	// Clear list for new batch.
        mListAdapter.removeObserver();
        mListAdapter.clear();
        mListAdapter = new SeparatedListAdapter(this);
         
        // User can sort by default (which is by checkin time), or just by distance.
        if (checkins != null) {
            mStateHolder.setCheckins(checkins);
        	if (mStateHolder.getSortMethod() == SORT_METHOD_RECENT) {
                sortCheckinsRecent(checkins, mListAdapter);
            } else {
                sortCheckinsDistance(checkins, mListAdapter);
            }
        } else if (ex != null) {
            mStateHolder.setCheckins(new Group<Checkin>());
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        if (mStateHolder.getCheckins().size() == 0) {
        	setEmptyView(mLayoutEmpty);
        }
        
        getListView().setAdapter(mListAdapter);
    }

    private static class TaskCheckins extends AsyncTask<Void, Void, Group<Checkin>> {

    	private Foursquared mFoursquared;
    	private FriendsActivity mActivity;
        private Exception mException;

        public TaskCheckins(FriendsActivity activity) {
        	mFoursquared = ((Foursquared) activity.getApplication());
        	mActivity = activity;
        }
        
        public void setActivity(FriendsActivity activity) {
        	mActivity = activity;
        }
        
        @Override
        public Group<Checkin> doInBackground(Void... params) {
        	Group<Checkin> checkins = null;
        	try {
        		checkins = checkins();
            } catch (Exception ex) {
            	mException = ex;
            }

            return checkins;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.onTaskStart();
        }

        @Override
        public void onPostExecute(Group<Checkin> checkins) {
        	if (mActivity != null) {
        		mActivity.onTaskComplete(checkins, mException);
        	}
        }

        private Group<Checkin> checkins() throws FoursquareException, IOException {
            
            // If we're the startup tab, it's likely that we won't have a geo location
            // immediately. For now we can use this ugly method of sleeping for N
            // seconds to at least let network location get a lock. We're only trying
            // to discern between same-city, so we can even use LocationManager's
            // getLastKnownLocation() method because we don't care if we're even a few
            // miles off. The api endpoint doesn't require location, so still go ahead
        	// even if we can't find a location.
            Location loc = mFoursquared.getLastKnownLocation();
            if (loc == null) {
                try { Thread.sleep(SLEEP_TIME_IF_NO_LOCATION); } catch (InterruptedException ex) {}
                loc = mFoursquared.getLastKnownLocation();
            }
            
            Group<Checkin> checkins = mFoursquared.getFoursquare().checkins(LocationUtils
                    .createFoursquareLocation(loc));
            
            Collections.sort(checkins, Comparators.getCheckinRecencyComparator());
            
            return checkins;
        }
    }
    
    private static class StateHolder {
        private Group<Checkin> mCheckins;
        private int mSortMethod;
        private boolean mRanOnce;
        private boolean mIsRunningTask;
        private TaskCheckins mTaskCheckins;
        
        public StateHolder() {
        	mRanOnce = false;
        	mIsRunningTask = false; 
        	mCheckins = new Group<Checkin>();
        }
        
        public int getSortMethod() {
        	return mSortMethod;
        }
        
        public void setSortMethod(int sortMethod) {
        	mSortMethod = sortMethod;
        }
        
        public Group<Checkin> getCheckins() {
        	return mCheckins;
        }
        
        public void setCheckins(Group<Checkin> checkins) {
        	mCheckins = checkins;
        }
        
        public boolean getRanOnce() {
        	return mRanOnce;
        }
        
        public void setRanOnce(boolean ranOnce) {
        	mRanOnce = ranOnce;
        }
        
        public boolean getIsRunningTask() {
        	return mIsRunningTask;
        }
        
        public void setIsRunningTask(boolean isRunning) {
        	mIsRunningTask = isRunning;
        }
        
        public void setActivity(FriendsActivity activity) {
        	if (mIsRunningTask) {
        		mTaskCheckins.setActivity(activity);
        	}
        }
        
        public void startTask(FriendsActivity activity) {
        	if (!mIsRunningTask) {
        		mTaskCheckins = new TaskCheckins(activity);
        		mTaskCheckins.execute();
        		mIsRunningTask = true;
        	}
        }
        
        public void cancel() {
        	if (mIsRunningTask) {
        		mTaskCheckins.cancel(true);
        		mIsRunningTask = false;
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
