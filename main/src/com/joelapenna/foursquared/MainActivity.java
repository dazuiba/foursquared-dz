/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquared.preferences.Preferences;
import com.joelapenna.foursquared.util.TabsUtil;
import com.joelapenna.foursquared.util.UiUtil;
import com.joelapenna.foursquared.util.UserUtils;

import android.app.Activity;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TabHost;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class MainActivity extends TabActivity {
    public static final String TAG = "MainActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    private TabHost mTabHost;

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
        
        if (DEBUG) Log.d(TAG, "onCreate()");
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        // Don't start the main activity if we don't have credentials
        if (!((Foursquared) getApplication()).isReady()) {
            if (DEBUG) Log.d(TAG, "Not ready for user.");
            redirectToLoginActivity();
        }

        if (DEBUG) Log.d(TAG, "Setting up main activity layout.");
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main_activity);
        initTabHost();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }

    private void initTabHost() {
        if (mTabHost != null) {
            throw new IllegalStateException("Trying to intialize already initializd TabHost");
        }

        mTabHost = getTabHost();
        
        // We may want to show the friends tab first, or the places tab first, depending on
        // the user preferences.
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
 
        // We can add more tabs here eventually, but if "Friends" isn't the startup tab, then
        // we are left with "Places" being the startup tab instead.
        String[] startupTabValues = getResources().getStringArray(R.array.startup_tabs_values);
        String startupTab = settings.getString(
                Preferences.PREFERENCE_STARTUP_TAB, startupTabValues[0]);
        Intent intent = new Intent(this, NearbyVenuesActivity.class);
        
        if (startupTab.equals(startupTabValues[0])) {
            TabsUtil.addTab(mTabHost, getString(R.string.tab_main_nav_friends), R.drawable.tab_main_nav_friends_selector, 
                    1, new Intent(this, FriendsActivity.class));
            TabsUtil.addTab(mTabHost, getString(R.string.tab_main_nav_nearby), R.drawable.tab_main_nav_nearby_selector, 
                    2, intent);
        } else {
            intent.putExtra(NearbyVenuesActivity.INTENT_EXTRA_STARTUP_GEOLOC_DELAY, 4000L);
            TabsUtil.addTab(mTabHost, getString(R.string.tab_main_nav_nearby), R.drawable.tab_main_nav_nearby_selector, 
                    1, intent);
            TabsUtil.addTab(mTabHost, getString(R.string.tab_main_nav_friends), R.drawable.tab_main_nav_friends_selector,
                    2, new Intent(this, FriendsActivity.class));
        }

        TabsUtil.addTab(mTabHost, getString(R.string.tab_main_nav_tips), R.drawable.tab_main_nav_tips_selector, 
                3, new Intent(this, TipsActivity.class));
        TabsUtil.addTab(mTabHost, getString(R.string.tab_main_nav_todos), R.drawable.tab_main_nav_todos_selector, 
                4, new Intent(this, TodosActivity.class));
        
        // 'Me' tab, just shows our own info. At this point we should have a
        // stored user id, and a user gender to control the image which is
        // displayed on the tab.
        String userId = ((Foursquared) getApplication()).getUserId();
        String userGender = ((Foursquared) getApplication()).getUserGender();
        
        Intent intentTabMe = new Intent(this, UserDetailsActivity.class);
        intentTabMe.putExtra(UserDetailsActivity.EXTRA_USER_ID, userId == null ? "unknown"
                : userId);
        TabsUtil.addTab(mTabHost, getString(R.string.tab_main_nav_me), 
                UserUtils.getDrawableForMeTabByGender(userGender), 5, intentTabMe);
        
        // Fix layout for 1.5.
        if (UiUtil.sdkVersion() < 4) {
            FrameLayout flTabContent = (FrameLayout)findViewById(android.R.id.tabcontent);
            flTabContent.setPadding(0, 0, 0, 0);
        }
    }

    private void redirectToLoginActivity() {
        setVisible(false);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NO_HISTORY | 
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | 
            Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
