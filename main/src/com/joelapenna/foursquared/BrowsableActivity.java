/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriMatcher;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.net.URLDecoder;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class BrowsableActivity extends Activity {
    private static final String TAG = "BrowsableActivity";
    private static final boolean DEBUG = FoursquaredSettings.DEBUG;

    private static final int URI_PATH_CHECKIN = 1;
    private static final int URI_PATH_CHECKINS = 2;
    private static final int URI_PATH_SEARCH = 3;
    private static final int URI_PATH_SHOUT = 4;
    private static final int URI_PATH_USER = 5;
    private static final int URI_PATH_VENUE = 6;
    
    public static String PARAM_SHOUT_TEXT = "shout";
    public static String PARAM_SEARCH_QUERY = "q";
    public static String PARAM_SEARCH_IMMEDIATE= "immediate";
    public static String PARAM_USER_ID= "uid";

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {

        sUriMatcher.addURI("m.foursquare.com", "checkin", URI_PATH_CHECKIN);
        sUriMatcher.addURI("m.foursquare.com", "checkins", URI_PATH_CHECKINS);
        sUriMatcher.addURI("m.foursquare.com", "search", URI_PATH_SEARCH);
        sUriMatcher.addURI("m.foursquare.com", "shout", URI_PATH_SHOUT);
        sUriMatcher.addURI("m.foursquare.com", "user", URI_PATH_USER);
        sUriMatcher.addURI("m.foursquare.com", "venue/#", URI_PATH_VENUE);
    }

    private BroadcastReceiver mLoggedOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive: " + intent);
            finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate()");
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        Uri uri = getIntent().getData();
        if (DEBUG) Log.d(TAG, "Intent Data: " + uri);

        Intent intent;

        switch (sUriMatcher.match(uri)) {
            case URI_PATH_CHECKIN:
                if (DEBUG) Log.d(TAG, "Matched: URI_PATH_CHECKIN");
                intent = new Intent(this, VenueActivity.class);
                intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_ID, uri.getQueryParameter("vid"));
                startActivity(intent);
                break;
            case URI_PATH_CHECKINS:
                if (DEBUG) Log.d(TAG, "Matched: URI_PATH_CHECKINS");
                intent = new Intent(this, FriendsActivity.class);
                startActivity(intent);
                break;
            case URI_PATH_SEARCH:
                if (DEBUG) Log.d(TAG, "Matched: URI_PATH_SEARCH");
                intent = new Intent(this, SearchVenuesActivity.class);
                if (!TextUtils.isEmpty(uri.getQueryParameter(PARAM_SEARCH_QUERY))) {
                    intent.putExtra(SearchManager.QUERY, URLDecoder.decode(uri.getQueryParameter(PARAM_SEARCH_QUERY)));
                    if (uri.getQueryParameter(PARAM_SEARCH_IMMEDIATE) != null && 
                        uri.getQueryParameter(PARAM_SEARCH_IMMEDIATE).equals("1")) {
                        intent.setAction(Intent.ACTION_SEARCH); // interpret action as search immediately.
                    } else {
                        intent.setAction(Intent.ACTION_VIEW); // interpret as prepopulate search field only.
                    }
                }
                startActivity(intent);
                break;
            case URI_PATH_SHOUT:
                if (DEBUG) Log.d(TAG, "Matched: URI_PATH_SHOUT");
                intent = new Intent(this, CheckinOrShoutGatherInfoActivity.class);
                intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_IS_SHOUT, true);
                if (!TextUtils.isEmpty(uri.getQueryParameter(PARAM_SHOUT_TEXT))) {
                    intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_TEXT_PREPOPULATE, 
                            URLDecoder.decode(uri.getQueryParameter(PARAM_SHOUT_TEXT)));
                }
                startActivity(intent);
                break;
            case URI_PATH_USER:
                if (DEBUG) Log.d(TAG, "Matched: URI_PATH_USER");
                intent = new Intent(this, UserDetailsActivity.class);
                if (!TextUtils.isEmpty(uri.getQueryParameter(PARAM_USER_ID))) {
                    intent.putExtra(UserDetailsActivity.EXTRA_USER_ID, 
                            uri.getQueryParameter(PARAM_USER_ID));
                }
                startActivity(intent);
                break;
            case URI_PATH_VENUE:
                if (DEBUG) Log.d(TAG, "Matched: URI_PATH_VENUE");
                intent = new Intent(this, VenueActivity.class);
                intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_ID, uri.getLastPathSegment());
                startActivity(intent);
                break;
            default:
                if (DEBUG) Log.d(TAG, "Matched: None");
        }
        finish();
    }
}
