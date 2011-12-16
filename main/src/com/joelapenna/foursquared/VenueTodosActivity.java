/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

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

import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.app.LoadableListActivity;
import com.joelapenna.foursquared.util.VenueUtils;
import com.joelapenna.foursquared.widget.TodosListAdapter;

/**
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class VenueTodosActivity extends LoadableListActivity {
    
	public static final String TAG = "VenueTodosActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueTodosActivity.INTENT_EXTRA_VENUE";

    public static final String INTENT_EXTRA_RETURN_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueTodosActivity.INTENT_EXTRA_RETURN_VENUE";

    private static final int ACTIVITY_TIP = 500;
    
    private TodosListAdapter mListAdapter;
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
                Log.e(TAG, "VenueTodosActivity requires a venue parcel its intent extras.");
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
    	
    	Group<Todo> todos = mStateHolder.getVenue().getTodos();
        
    	mListAdapter = new TodosListAdapter(this, ((Foursquared) getApplication()).getRemoteResourceManager());
        mListAdapter.setGroup(todos);
        mListAdapter.setDisplayTodoVenueTitles(false);
        
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
            	
            	Todo todo = (Todo) parent.getAdapter().getItem(position);
            	if (todo.getTip() != null) {
                	todo.getTip().setVenue(venue);
                	
                    Intent intent = new Intent(VenueTodosActivity.this, TipActivity.class);
                    intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, todo.getTip());
                    intent.putExtra(TipActivity.EXTRA_VENUE_CLICKABLE, false);
                    startActivityForResult(intent, ACTIVITY_TIP);
            	}
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == ACTIVITY_TIP && resultCode == Activity.RESULT_OK) {
    		Tip tip = (Tip)data.getParcelableExtra(TipActivity.EXTRA_TIP_RETURNED);
    		Todo todo = data.hasExtra(TipActivity.EXTRA_TODO_RETURNED) ? 
    				(Todo)data.getParcelableExtra(TipActivity.EXTRA_TODO_RETURNED) : null;
			updateTip(tip, todo);
        }
    }
    
    private void updateTip(Tip tip, Todo todo) {
    	// Changes to a tip status can produce or remove a to-do from
    	// the venue, update it now.
    	VenueUtils.handleTipChange(mStateHolder.getVenue(), tip, todo);
    	
    	// If there are no more todos, there's nothing left to.. do.
    	prepareResultIntent();
    	if (mStateHolder.getVenue().getHasTodo()) {
        	mListAdapter.notifyDataSetInvalidated();
    	} else {
    		finish();
    	}
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
        private Intent mPreparedResult;
        
        public StateHolder() {
        	mPreparedResult = null;
        }
 
        public Venue getVenue() {
            return mVenue;
        }
        
        public void setVenue(Venue venue) {
        	mVenue = venue;
        }
        
        public Intent getPreparedResult() {
        	return mPreparedResult;
        }
        
        public void setPreparedResult(Intent intent) {
        	mPreparedResult = intent;
        }
    }
}
