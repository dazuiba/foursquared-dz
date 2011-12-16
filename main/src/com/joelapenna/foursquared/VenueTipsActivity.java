/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.app.LoadableListActivity;
import com.joelapenna.foursquared.util.UserUtils;
import com.joelapenna.foursquared.util.VenueUtils;
import com.joelapenna.foursquared.widget.SeparatedListAdapter;
import com.joelapenna.foursquared.widget.TipsListAdapter;

import android.app.Activity;
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

import java.util.List;

/**
 * Shows tips left at a venue as a sectioned list adapter. Groups are split
 * into tips left by friends and tips left by everyone else.
 * 
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *   -modified to start TipActivity on tip click (2010-03-25)
 *   -added photos for tips (2010-03-25)
 *   -refactored for new VenueActivity design (2010-09-16)
 */
public class VenueTipsActivity extends LoadableListActivity {
    
	public static final String TAG = "VenueTipsActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueTipsActivity.INTENT_EXTRA_VENUE";
    public static final String INTENT_EXTRA_RETURN_VENUE = Foursquared.PACKAGE_NAME 
	        + ".VenueTipsActivity.INTENT_EXTRA_RETURN_VENUE";
    

    private static final int ACTIVITY_TIP = 500;
    
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
            setPreparedResultIntent();
        } else {
            mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
            	mStateHolder.setVenue((Venue)getIntent().getExtras().getParcelable(INTENT_EXTRA_VENUE));
            } else {
                Log.e(TAG, "VenueTipsActivity requires a venue parcel its intent extras.");
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
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mLoggedOutReceiver);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return mStateHolder;
    }

    private void ensureUi() {
    	
    	mListAdapter = new SeparatedListAdapter(this);
    	
    	if (mStateHolder.getTipsFriends().size() > 0) {
    	    TipsListAdapter adapter = new TipsListAdapter(this,
                    ((Foursquared) getApplication()).getRemoteResourceManager(), R.layout.tip_list_item);
    	    adapter.setDisplayTipVenueTitles(false);
    	    adapter.setGroup(mStateHolder.getTipsFriends());
            mListAdapter.addSection(getString(R.string.venue_tips_activity_section_friends, 
                    mStateHolder.getTipsFriends().size()), 
                    adapter);
    	}

    	TipsListAdapter adapter = new TipsListAdapter(this,
                ((Foursquared) getApplication()).getRemoteResourceManager(), R.layout.tip_list_item);
    	adapter.setDisplayTipVenueTitles(false);
    	adapter.setGroup(mStateHolder.getTipsAll());
        mListAdapter.addSection(getString(R.string.venue_tips_activity_section_all, 
                mStateHolder.getTipsAll().size()), 
                adapter);
        
        
        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setSmoothScrollbarEnabled(true);
        listView.setDividerHeight(0);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            	// The tip that was clicked won't have its venue member set, since we got
            	// here by viewing the parent venue. In this case, we request that the tip
            	// activity not let the user recursively start drilling down past here.
            	// Create a dummy venue which has only the name and address filled in.
            	Venue venue = new Venue();
            	venue.setName(mStateHolder.getVenue().getName());
            	venue.setAddress(mStateHolder.getVenue().getAddress());
            	venue.setCrossstreet(mStateHolder.getVenue().getCrossstreet());
            	
            	Tip tip = (Tip)parent.getAdapter().getItem(position);
            	tip.setVenue(venue);
            	
                Intent intent = new Intent(VenueTipsActivity.this, TipActivity.class);
                intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, tip);
                intent.putExtra(TipActivity.EXTRA_VENUE_CLICKABLE, false);
                startActivityForResult(intent, ACTIVITY_TIP);
            }
        });

        setTitle(getString(R.string.venue_tips_activity_title, mStateHolder.getVenue().getName()));
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == ACTIVITY_TIP && resultCode == Activity.RESULT_OK) {
    		if (data.hasExtra(TipActivity.EXTRA_TIP_RETURNED)) {
	    		Tip tip = (Tip)data.getParcelableExtra(TipActivity.EXTRA_TIP_RETURNED);
	    		Todo todo = data.hasExtra(TipActivity.EXTRA_TODO_RETURNED) ? 
	    				(Todo)data.getParcelableExtra(TipActivity.EXTRA_TODO_RETURNED) : null;
	    		updateTip(tip, todo);
    		}
        }
    }
    
    private void updateTip(Tip tip, Todo todo) {
    	mStateHolder.updateTip(tip, todo);
    	mListAdapter.notifyDataSetInvalidated();
    	prepareResultIntent();
    }
    
    private void prepareResultIntent() {
    	Intent intent = new Intent();
    	intent.putExtra(INTENT_EXTRA_RETURN_VENUE, mStateHolder.getVenue());
    	mStateHolder.setPreparedResult(intent);
    	setPreparedResultIntent();
    }
    
    private void setPreparedResultIntent() {
    	if (mStateHolder.getPreparedResult() != null) {
    		setResult(Activity.RESULT_OK, mStateHolder.getPreparedResult());
    	}
    }
    
    
    private static class StateHolder {
        
        private Venue mVenue;
        private Group<Tip> mTipsFriends;
        private Group<Tip> mTipsAll;
        private Intent mPreparedResult;
        
        public StateHolder() {
        	mPreparedResult = null;
        	mTipsFriends = new Group<Tip>();
        	mTipsAll = new Group<Tip>();
        }
 
        public Venue getVenue() {
            return mVenue;
        }
        
        public void setVenue(Venue venue) {
        	mVenue = venue;
        	mTipsFriends.clear();
        	mTipsAll.clear();
        	for (Tip tip : venue.getTips()) {
                if (UserUtils.isFriend(tip.getUser())) {
                    mTipsFriends.add(tip);
                } else {
                    mTipsAll.add(tip);
                }
            }
        }
        
        public Group<Tip> getTipsFriends() {
            return mTipsFriends;
        }
        
        public Group<Tip> getTipsAll() {
            return mTipsAll;
        }
        
        public Intent getPreparedResult() {
        	return mPreparedResult;
        }
        
        public void setPreparedResult(Intent intent) {
        	mPreparedResult = intent;
        }
        
        public void updateTip(Tip tip, Todo todo) {
            // Changes to a tip status can produce or remove a to-do for its 
            // parent venue.
            VenueUtils.handleTipChange(mVenue, tip, todo);
            
            // Also update the tip from wherever it appears in the separated
            // list adapter sections.
            updateTip(tip, mTipsFriends);
            updateTip(tip, mTipsAll);
        }
        
        private void updateTip(Tip tip, List<Tip> target) {
            for (Tip it : target) {
                if (it.getId().equals(tip.getId())) {
                    it.setStatus(tip.getStatus());
                    break;
                }
            }
        }
    }
}
