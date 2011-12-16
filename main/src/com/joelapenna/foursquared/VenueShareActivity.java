/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.util.VenueUtils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Queries the system for any apps that can be used for sharing data,
 * like sms or email. Package exploration is largely taken from Mark 
 * Murphy's commonsware projects:
 * 
 * http://github.com/commonsguy/cw-advandroid
 * 
 * @date September 22, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 * 
 */
public class VenueShareActivity extends Activity {
    public static final String TAG = "VenueShareActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueShareActivity.INTENT_EXTRA_VENUE";
    
    private StateHolder mStateHolder;
    private ShareAdapter mListAdapter;
        
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
        setContentView(R.layout.venue_share_activity);
        setTitle(getString(R.string.venue_share_activity_title));

        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
        } else {
            mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
                mStateHolder.setVenue((Venue)getIntent().getExtras().getParcelable(INTENT_EXTRA_VENUE));
            } else {
                Log.e(TAG, "VenueShareActivity requires a venue parcel its intent extras.");
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
            unregisterReceiver(mLoggedOutReceiver);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return mStateHolder;
    }

    private void ensureUi() {

        mListAdapter = new ShareAdapter(this, getPackageManager(), findAppsForSharing());

        ListView listView = (ListView)findViewById(R.id.listview);
        listView.setAdapter(mListAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launchAppIntent(position);
            }
        });
    }
    
    private void launchAppIntent(int position) {
        ResolveInfo launchable = mListAdapter.getItem(position);
        ActivityInfo activity = launchable.activityInfo;
        ComponentName componentName = new ComponentName(
                activity.applicationInfo.packageName, activity.name);
        
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setComponent(componentName);
        intent.setType("text/plain"); 
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, 
                "Foursquare Venue Share");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, 
                VenueUtils.toStringVenueShare(mStateHolder.getVenue()));
        startActivity(intent);           
        
        finish();
    }
    
    private List<ResolveInfo> findAppsForSharing() {
        Intent intent = new Intent(Intent.ACTION_SEND, null);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setType("text/plain");
        List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0); 
        
        TreeMap<String, ResolveInfo> alpha = new TreeMap<String, ResolveInfo>();
        for (ResolveInfo it : activities) {
            alpha.put(it.loadLabel(getPackageManager()).toString(), it);
        }
        
        return new ArrayList<ResolveInfo>(alpha.values());
    }
    
    private class ShareAdapter extends ArrayAdapter<ResolveInfo> {
        
        public ShareAdapter(Context context, PackageManager pm, List<ResolveInfo> apps) {
            super(context, R.layout.user_actions_list_item, apps);
        }
  
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = newView(parent);
            }
            bindView(position, convertView);
            return(convertView);
        }
        
        private View newView(ViewGroup parent) {
            return (getLayoutInflater().inflate(R.layout.user_actions_list_item, parent, false));
        }
 
        private void bindView(int position, View view) {
            PackageManager packageManager = getPackageManager();
            ImageView icon = (ImageView)view.findViewById(R.id.userActionsListItemIcon);
            icon.setImageDrawable(getItem(position).loadIcon(packageManager));
            TextView label = (TextView)view.findViewById(R.id.userActionsListItemLabel);
            label.setText(getItem(position).loadLabel(packageManager));
        }
    }
    
    private static class StateHolder {
        
        private Venue mVenue;
        
        public StateHolder() {
        }
 
        public Venue getVenue() {
            return mVenue;
        }
        
        public void setVenue(Venue venue) {
            mVenue = venue;
        }
    }
}
