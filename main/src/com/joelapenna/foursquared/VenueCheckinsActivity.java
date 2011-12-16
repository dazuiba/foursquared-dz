/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.types.Checkin;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.app.LoadableListActivity;
import com.joelapenna.foursquared.util.UserUtils;
import com.joelapenna.foursquared.widget.CheckinListAdapter;
import com.joelapenna.foursquared.widget.SeparatedListAdapter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *   -refactored for display of straight checkins list (September 16, 2010).
 *   
 */
public class VenueCheckinsActivity extends LoadableListActivity {
    public static final String TAG = "VenueCheckinsActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueCheckinsActivity.INTENT_EXTRA_VENUE";

    private SeparatedListAdapter mListAdapter;
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
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
        } else {
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
                mStateHolder = new StateHolder(
            	    (Venue)getIntent().getExtras().getParcelable(INTENT_EXTRA_VENUE),
            	    ((Foursquared) getApplication()).getUserId());
            } else {
                Log.e(TAG, "VenueCheckinsActivity requires a venue parcel its intent extras.");
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
            mListAdapter.removeObserver();
            unregisterReceiver(mLoggedOutReceiver);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return mStateHolder;
    }

    private void ensureUi() {
    	
    	mListAdapter = new SeparatedListAdapter(this);
    	
    	if (mStateHolder.getCheckinsYou().size() > 0) {
    	    String title = getResources().getString(R.string.venue_activity_people_count_you);
    	    
            CheckinListAdapter adapter = new CheckinListAdapter(this,
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(mStateHolder.getCheckinsYou());
            mListAdapter.addSection(title, adapter);
    	}
    	if (mStateHolder.getCheckinsFriends().size() > 0) {
    	    String title = getResources().getString(
                mStateHolder.getCheckinsOthers().size() == 1 ?
                    R.string.venue_activity_checkins_count_friends_single :
                    R.string.venue_activity_checkins_count_friends_plural, 
                    mStateHolder.getCheckinsFriends().size());
    	    
            CheckinListAdapter adapter = new CheckinListAdapter(this,
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(mStateHolder.getCheckinsFriends());
            mListAdapter.addSection(title, adapter);
        } 
    	if (mStateHolder.getCheckinsOthers().size() > 0) {
    	    boolean others = mStateHolder.getCheckinsYou().size() + 
    	        mStateHolder.getCheckinsFriends().size() > 0;
    	    
    	    String title = getResources().getString(
    	        mStateHolder.getCheckinsOthers().size() == 1 ?
    	            (others ? R.string.venue_activity_checkins_count_others_single :
    	                R.string.venue_activity_checkins_count_others_alone_single) :
                    (others ? R.string.venue_activity_checkins_count_others_plural : 
                        R.string.venue_activity_checkins_count_others_alone_plural), 
                    mStateHolder.getCheckinsOthers().size());
    	    
            CheckinListAdapter adapter = new CheckinListAdapter(this,
                    ((Foursquared) getApplication()).getRemoteResourceManager());
            adapter.setGroup(mStateHolder.getCheckinsOthers());
            mListAdapter.addSection(title, adapter);
        }
        
        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setSmoothScrollbarEnabled(true);
        listView.setDividerHeight(0);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Checkin checkin = (Checkin) parent.getAdapter().getItem(position);
                Intent intent = new Intent(VenueCheckinsActivity.this, UserDetailsActivity.class);
                intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, checkin.getUser());
                intent.putExtra(UserDetailsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS, true);
                startActivity(intent);
            }
        });
        
        setTitle(getString(R.string.venue_checkins_activity_title, mStateHolder.getVenueName()));
    }
    
    private static class StateHolder {
        
        private String mVenueName;
        private Group<Checkin> mYou;
        private Group<Checkin> mFriends;
        private Group<Checkin> mOthers;
        
        public StateHolder(Venue venue, String loggedInUserId) {
            mVenueName = venue.getName();
            mYou = new Group<Checkin>();
            mFriends = new Group<Checkin>();
            mOthers = new Group<Checkin>();
            
            mYou.clear();
            mFriends.clear();
            mOthers.clear();
            for (Checkin it : venue.getCheckins()) {
                User user = it.getUser();
                if (UserUtils.isFriend(user)) {
                    mFriends.add(it);
                } else if (loggedInUserId.equals(user.getId())) {
                    mYou.add(it);
                } else {
                    mOthers.add(it);
                }
            }
        }
        
        public String getVenueName() {
            return mVenueName;
        }
 
        public Group<Checkin> getCheckinsYou() {
            return mYou;
        }

        public Group<Checkin> getCheckinsFriends() {
            return mFriends;
        }
        
        public Group<Checkin> getCheckinsOthers() {
            return mOthers;
        }
    }
}
