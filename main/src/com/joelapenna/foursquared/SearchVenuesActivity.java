/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.error.LocationException;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.providers.VenueQuerySuggestionsProvider;
import com.joelapenna.foursquared.util.Comparators;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.TabsUtil;
import com.joelapenna.foursquared.util.UiUtil;
import com.joelapenna.foursquared.widget.SeparatedListAdapter;
import com.joelapenna.foursquared.widget.VenueListAdapter;

import android.app.Activity;
import android.app.SearchManager;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.SearchRecentSuggestions;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Collections;
import java.util.Observable;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class SearchVenuesActivity extends TabActivity {
    static final String TAG = "SearchVenuesActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    public static final String QUERY_NEARBY = null;

    public static SearchResultsObservable searchResultsObservable;

    private static final int MENU_SEARCH = 0;
    private static final int MENU_REFRESH = 1;
    private static final int MENU_NEARBY = 2;
    private static final int MENU_ADD_VENUE = 3;
    private static final int MENU_GROUP_SEARCH = 0;

    private SearchTask mSearchTask;
    private SearchHolder mSearchHolder = new SearchHolder();
    private ListView mListView;
    private LinearLayout mEmpty;
    private TextView mEmptyText;
    private ProgressBar mEmptyProgress;
    private TabHost mTabHost;
    private SeparatedListAdapter mListAdapter;

    private boolean mIsShortcutPicker;

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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.search_venues_activity);
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        searchResultsObservable = new SearchResultsObservable();

        initTabHost();
        initListViewAdapter();

        // Watch to see if we've been called as a shortcut intent.
        mIsShortcutPicker = Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction());
        
        if (getLastNonConfigurationInstance() != null) {
            if (DEBUG) Log.d(TAG, "Restoring state.");
            SearchHolder holder = (SearchHolder) getLastNonConfigurationInstance();
            if (holder.results != null) {
                mSearchHolder.query = holder.query;
                setSearchResults(holder.results);
                putSearchResultsInAdapter(holder.results);
            }
        } else {
            onNewIntent(getIntent());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((Foursquared) getApplication()).requestLocationUpdates(true);
        if (mSearchHolder.results == null && mSearchTask == null) {
            mSearchTask = (SearchTask) new SearchTask().execute();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        ((Foursquared) getApplication()).removeLocationUpdates();
        
        if (isFinishing()) {
            mListAdapter.removeObserver();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSearchTask != null) {
            mSearchTask.cancel(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // Always show these.
        menu.add(MENU_GROUP_SEARCH, MENU_SEARCH, Menu.NONE, R.string.search_label) //
                .setIcon(R.drawable.ic_menu_search) //
                .setAlphabeticShortcut(SearchManager.MENU_KEY);
        menu.add(MENU_GROUP_SEARCH, MENU_NEARBY, Menu.NONE, R.string.nearby_label) //
                .setIcon(R.drawable.ic_menu_places);
        menu.add(MENU_GROUP_SEARCH, MENU_REFRESH, Menu.NONE, R.string.refresh) //
                .setIcon(R.drawable.ic_menu_refresh);
        menu.add(MENU_GROUP_SEARCH, MENU_ADD_VENUE, Menu.NONE, R.string.nearby_menu_add_venue) //
                .setIcon(R.drawable.ic_menu_add);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SEARCH:
                onSearchRequested();
                return true;
            case MENU_NEARBY:
                executeSearchTask(null);
                return true;
            case MENU_REFRESH:
                executeSearchTask(mSearchHolder.query);
                return true;
            case MENU_ADD_VENUE:
                Intent intent = new Intent(SearchVenuesActivity.this, AddVenueActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        
        if (intent != null) {
            String action = intent.getAction();
            String query = intent.getStringExtra(SearchManager.QUERY);
            
            Log.i(TAG, "New Intent: action[" + action + "].");
            
            if (!TextUtils.isEmpty(action)) {
                if (action.equals(Intent.ACTION_CREATE_SHORTCUT)) {
                    Log.i(TAG, "   action = create shortcut, user can click one of the current venues.");
                } else if (action.equals(Intent.ACTION_VIEW)) {
                    if (!TextUtils.isEmpty(query)) {
                        Log.i(TAG, "   action = view, query term provided, prepopulating search.");
                        startSearch(query, false, null, false);
                    } else {
                        Log.i(TAG, "   action = view, but no query term provided, doing nothing.");
                    }
                } else if (action.equals(Intent.ACTION_SEARCH) && !TextUtils.isEmpty(query)) {
                    Log.i(TAG, "   action = search, query term provided, executing search immediately.");
                    SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                            VenueQuerySuggestionsProvider.AUTHORITY, VenueQuerySuggestionsProvider.MODE);
                    suggestions.saveRecentQuery(query, null);
                    executeSearchTask(query);
                }
            }
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mSearchHolder;
    }

    public void putSearchResultsInAdapter(Group<Group<Venue>> searchResults) {
        mListAdapter.removeObserver();
        mListAdapter = new SeparatedListAdapter(this);
        int groupCount = searchResults.size();
        for (int groupsIndex = 0; groupsIndex < groupCount; groupsIndex++) {
            Group<Venue> group = searchResults.get(groupsIndex);
            if (group.size() > 0) {
                VenueListAdapter groupAdapter = new VenueListAdapter(this,
                        ((Foursquared) getApplication()).getRemoteResourceManager());
                groupAdapter.setGroup(group);
                if (DEBUG) Log.d(TAG, "Adding Section: " + group.getType());
                mListAdapter.addSection(group.getType(), groupAdapter);
            }
        }
        mListView.setAdapter(mListAdapter);
    }

    public void setSearchResults(Group<Group<Venue>> searchResults) {
        if (DEBUG) Log.d(TAG, "Setting search results.");
        mSearchHolder.results = searchResults;
        searchResultsObservable.notifyObservers();
    }

    void executeSearchTask(String query) {
        if (DEBUG) Log.d(TAG, "sendQuery()");
        mSearchHolder.query = query;
        // not going through set* because we don't want to notify search result
        // observers.
        mSearchHolder.results = null;

        // If a task is already running, don't start a new one.
        if (mSearchTask != null && mSearchTask.getStatus() != AsyncTask.Status.FINISHED) {
            if (DEBUG) Log.d(TAG, "Query already running attempting to cancel: " + mSearchTask);
            if (!mSearchTask.cancel(true) && !mSearchTask.isCancelled()) {
                if (DEBUG) Log.d(TAG, "Unable to cancel search? Notifying the user.");
                Toast.makeText(this, getString(R.string.search_already_in_progress_toast),
                        Toast.LENGTH_SHORT);
                return;
            }
        }
        mSearchTask = (SearchTask) new SearchTask().execute();
    }

    void startItemActivity(Venue venue) {
        Intent intent = new Intent(SearchVenuesActivity.this, VenueActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_PARTIAL, venue);
        startActivity(intent);
    }

    private void ensureSearchResults() {
        if (mListAdapter.getCount() > 0) {
            mEmpty.setVisibility(LinearLayout.GONE);
            mListView.setVisibility(ViewGroup.VISIBLE);
        } else {
            mEmpty.setVisibility(LinearLayout.VISIBLE);
            mEmptyProgress.setVisibility(ViewGroup.GONE);
            mEmptyText.setText(R.string.no_search_results);
            mListView.setVisibility(ViewGroup.GONE);
        }
    }

    private void ensureTitle(boolean finished) {
        if (finished) {
            if (mSearchHolder.query == QUERY_NEARBY) {
                setTitle(getString(R.string.title_search_finished_noquery));
            } else {
                setTitle(getString(R.string.title_search_finished, mSearchHolder.query));
            }
        } else {
            if (mSearchHolder.query == QUERY_NEARBY) {
                setTitle(getString(R.string.title_search_inprogress_noquery));
            } else {
                setTitle(getString(R.string.title_search_inprogress, mSearchHolder.query));
            }
        }
    }

    private void initListViewAdapter() {
        if (mListView != null) {
            throw new IllegalStateException("Trying to initialize already initialized ListView");
        }
        mEmpty = (LinearLayout) findViewById(R.id.empty);
        mEmptyText = (TextView) findViewById(R.id.emptyText);
        mEmptyProgress = (ProgressBar) findViewById(R.id.emptyProgress);

        mListView = (ListView) findViewById(R.id.list);
        mListAdapter = new SeparatedListAdapter(this);

        mListView.setAdapter(mListAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override 
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Venue venue = (Venue) parent.getAdapter().getItem(position);
                if (mIsShortcutPicker) {
                    setupShortcut(venue);
                    finish();
                } else {
                    startItemActivity(venue);
                }
                finish();
            }
        });
    }

    protected void setupShortcut(Venue venue) {
        // First, set up the shortcut intent. For this example, we simply create
        // an intent that will bring us directly back to this activity. A more 
        // typical implementation would use a data Uri in order to display a more 
        // specific result, or a custom action in order to launch a specific operation.

        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClassName(this, VenueActivity.class.getName());
        shortcutIntent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_ID, venue.getId());

        // Then, set up the container intent (the response to the caller)
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, venue.getName());
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this,
                R.drawable.venue_shortcut_icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher
        setResult(RESULT_OK, intent);
    }

    private void initTabHost() {
        if (mTabHost != null) {
            throw new IllegalStateException("Trying to intialize already initializd TabHost");
        }

        mTabHost = getTabHost(); 
        
        TabsUtil.addTab(mTabHost, getString(R.string.tab_search_nav_venues), 
                R.drawable.tab_search_nav_venues_selector, 0, R.id.listviewLayout);
        TabsUtil.addTab(mTabHost, getString(R.string.tab_search_nav_map), 
                R.drawable.tab_search_nav_map_selector, 
                1, new Intent(this, SearchVenuesMapActivity.class));
        
        mTabHost.setCurrentTab(0);
        
        // Fix layout for 1.5.
        if (UiUtil.sdkVersion() < 4) {
            FrameLayout flTabContent = (FrameLayout)findViewById(android.R.id.tabcontent);
            flTabContent.setPadding(0, 0, 0, 0);
        }
    }

    private class SearchTask extends AsyncTask<Void, Void, Group<Group<Venue>>> {

        private Exception mReason = null;

        @Override
        public void onPreExecute() {
            if (DEBUG) Log.d(TAG, "SearchTask: onPreExecute()");
            setProgressBarIndeterminateVisibility(true);
            ensureTitle(false);
        }

        @Override
        public Group<Group<Venue>> doInBackground(Void... params) {
            try {
                return search();
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        public void onPostExecute(Group<Group<Venue>> groups) {
            try {
                if (groups == null) {
                    NotificationsUtil.ToastReasonForFailure(SearchVenuesActivity.this, mReason);
                } else {
                    setSearchResults(groups);
                    putSearchResultsInAdapter(groups);
                }

            } finally {
                setProgressBarIndeterminateVisibility(false);
                ensureTitle(true);
                ensureSearchResults();
            }
        }

        public Group<Group<Venue>> search() throws FoursquareException, LocationException,
                IOException {
            Foursquare foursquare = ((Foursquared) getApplication()).getFoursquare();
            Location location = ((Foursquared) getApplication()).getLastKnownLocationOrThrow();

            Group<Group<Venue>> groups = foursquare.venues(LocationUtils
                    .createFoursquareLocation(location), mSearchHolder.query, 30);
            for (int i = 0; i < groups.size(); i++) {
                Collections.sort(groups.get(i), Comparators.getVenueDistanceComparator());
            }
            return groups;
        }
    }

    private static class SearchHolder {
        Group<Group<Venue>> results;
        String query;
    }

    class SearchResultsObservable extends Observable {

        @Override
        public void notifyObservers(Object data) {
            setChanged();
            super.notifyObservers(data);
        }

        public Group<Group<Venue>> getSearchResults() {
            return mSearchHolder.results;
        }

        public String getQuery() {
            return mSearchHolder.query;
        }
    };
}
