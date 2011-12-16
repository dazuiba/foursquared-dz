/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;
import com.joelapenna.foursquare.types.Checkin;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquare.util.VenueUtils;
import com.joelapenna.foursquared.maps.CheckinGroup;
import com.joelapenna.foursquared.maps.CheckinGroupItemizedOverlay;
import com.joelapenna.foursquared.maps.CheckinGroupItemizedOverlay.CheckinGroupOverlayTapListener;
import com.joelapenna.foursquared.maps.CrashFixMyLocationOverlay;
import com.joelapenna.foursquared.util.CheckinTimestampSort;
import com.joelapenna.foursquared.util.GeoUtils;
import com.joelapenna.foursquared.util.UiUtil;
import com.joelapenna.foursquared.widget.MapCalloutView;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *   -Added support for checkingroup items, also stopped recreation
 *    of overlay group in onResume(). [2010-06-21]
 */
public class FriendsMapActivity extends MapActivity {
    public static final String TAG = "FriendsMapActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String EXTRA_CHECKIN_PARCELS = Foursquared.PACKAGE_NAME
    		+ ".FriendsMapActivity.EXTRA_CHECKIN_PARCELS";
    
    private StateHolder mStateHolder;
    private Venue mTappedVenue;
    private MapCalloutView mCallout;
    private MapView mMapView;
    private MapController mMapController;
    private List<CheckinGroupItemizedOverlay> mCheckinGroupOverlays;
    private MyLocationOverlay mMyLocationOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_map_activity);
        
        if (getLastNonConfigurationInstance() != null) {
            mStateHolder = (StateHolder) getLastNonConfigurationInstance();
        } else {
        	if (getIntent().hasExtra(EXTRA_CHECKIN_PARCELS)) {
        		Parcelable[] parcelables = getIntent().getParcelableArrayExtra(EXTRA_CHECKIN_PARCELS);
        		Group<Checkin> checkins = new Group<Checkin>();
        		for (int i = 0; i < parcelables.length; i++) {
        			checkins.add((Checkin)parcelables[i]);
        		}
        		
        		mStateHolder = new StateHolder();
        		mStateHolder.setCheckins(checkins);
        	} else {
        		Log.e(TAG, "FriendsMapActivity requires checkin array in intent extras.");
        		finish();
        		return;
        	}
        }
        
        initMap();
    }

    @Override
    public void onResume() {
        super.onResume();
        
        mMyLocationOverlay.enableMyLocation();
        if (UiUtil.sdkVersion() > 3) {
        	mMyLocationOverlay.enableCompass();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        
        mMyLocationOverlay.disableMyLocation();
        mMyLocationOverlay.disableCompass();
    }

    private void initMap() {
    	
        mMapView = (MapView) findViewById(R.id.mapView);
        mMapView.setBuiltInZoomControls(true);
        mMapController = mMapView.getController();

        mMyLocationOverlay = new CrashFixMyLocationOverlay(this, mMapView);
        mMapView.getOverlays().add(mMyLocationOverlay);
        
        loadSearchResults(mStateHolder.getCheckins());
        
        mCallout = (MapCalloutView) findViewById(R.id.map_callout);
        mCallout.setVisibility(View.GONE);
        mCallout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(FriendsMapActivity.this, VenueActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_PARTIAL, mTappedVenue);
                startActivity(intent);
            }
        });
        
        recenterMap();
    }

    private void loadSearchResults(Group<Checkin> checkins) {
        // One CheckinItemizedOverlay per group!
        CheckinGroupItemizedOverlay mappableCheckinsOverlay = createMappableCheckinsOverlay(checkins);

        mCheckinGroupOverlays = new ArrayList<CheckinGroupItemizedOverlay>();
    	if (mappableCheckinsOverlay != null) {
            mCheckinGroupOverlays.add(mappableCheckinsOverlay);
        }
        // Only add the list of checkin group overlays if it contains any overlays.
        if (mCheckinGroupOverlays.size() > 0) {
            mMapView.getOverlays().addAll(mCheckinGroupOverlays);
        } else {
            Toast.makeText(this, getResources().getString(
                    R.string.friendsmapactivity_no_checkins), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Create an overlay that contains a specific group's list of mappable
     * checkins.
     */
    private CheckinGroupItemizedOverlay createMappableCheckinsOverlay(Group<Checkin> group) {
        // We want to group checkins by venue. Do max three checkins per venue, a total
        // of 100 venues total. We should only also display checkins that are within a
        // city radius, and are at most three hours old.
        CheckinTimestampSort timestamps = new CheckinTimestampSort();

        Map<String, CheckinGroup> checkinMap = new HashMap<String, CheckinGroup>();
        for (int i = 0, m = group.size(); i < m; i++) {
            Checkin checkin = (Checkin)group.get(i);
            Venue venue = checkin.getVenue();
            if (VenueUtils.hasValidLocation(venue)) {
                // Make sure the venue is within city radius.
                try {
                    int distance = Integer.parseInt(checkin.getDistance());
                    if (distance > FriendsActivity.CITY_RADIUS_IN_METERS) {
                        continue;
                    }
                } catch (NumberFormatException ex) {
                    // Distance was invalid, ignore this checkin.
                    continue;
                }
                
                // Make sure the checkin happened within the last three hours.
                try {
                    Date date = new Date(checkin.getCreated());
                    if (date.before(timestamps.getBoundaryRecent())) {
                        continue;
                    }
                } catch (Exception ex) {
                    // Timestamps was invalid, ignore this checkin.
                    continue;
                }
                
                String venueId = venue.getId();
                CheckinGroup cg = checkinMap.get(venueId);
                if (cg == null) {
                    cg = new CheckinGroup();
                    checkinMap.put(venueId, cg);
                }
                
                // Stop appending if we already have three checkins here.
                if (cg.getCheckinCount() < 3) {
                    cg.appendCheckin(checkin);
                }
            }
            
            // We can't have too many pins on the map.
            if (checkinMap.size() > 99) {
                break;
            }
        }

        Group<CheckinGroup> mappableCheckins = new Group<CheckinGroup>(checkinMap.values());
        if (mappableCheckins.size() > 0) {
            CheckinGroupItemizedOverlay mappableCheckinsGroupOverlay = new CheckinGroupItemizedOverlay(
                    this,
                    ((Foursquared) getApplication()).getRemoteResourceManager(),
                    this.getResources().getDrawable(R.drawable.pin_checkin_multiple),
                    mCheckinGroupOverlayTapListener);
            mappableCheckinsGroupOverlay.setGroup(mappableCheckins);
            return mappableCheckinsGroupOverlay;
        } else {
            return null;
        }
    }

    private void recenterMap() {
        // Previously we'd try to zoom to span, but this gives us odd results a lot of times,
        // so falling back to zoom at a fixed level.
        GeoPoint center = mMyLocationOverlay.getMyLocation();
        if (center != null) {
            Log.i(TAG, "Using my location overlay as center point for map centering.");
            mMapController.animateTo(center);
            mMapController.setZoom(16);
        } else {
            // Location overlay wasn't ready yet, try using last known geolocation from manager.
            Location bestLocation = GeoUtils.getBestLastGeolocation(this);
            if (bestLocation != null) {
                Log.i(TAG, "Using last known location for map centering.");
                mMapController.animateTo(GeoUtils.locationToGeoPoint(bestLocation));
                mMapController.setZoom(16);
            } else {
                // We have no location information at all, so we'll just show the map at a high
                // zoom level and the user can zoom in as they wish.
                Log.i(TAG, "No location available for map centering.");
                mMapController.setZoom(8);
            }
        }
    }
 
    /** Handle taps on one of the pins. */
    private CheckinGroupOverlayTapListener mCheckinGroupOverlayTapListener = 
        new CheckinGroupOverlayTapListener() {
        @Override
        public void onTap(OverlayItem itemSelected, OverlayItem itemLastSelected, CheckinGroup cg) {
        	mTappedVenue = cg.getVenue();
            mCallout.setTitle(cg.getVenue().getName());
            mCallout.setMessage(cg.getDescription());
            mCallout.setVisibility(View.VISIBLE);

            mMapController.animateTo(new GeoPoint(cg.getLatE6(), cg.getLonE6()));
        }

        @Override
        public void onTap(GeoPoint p, MapView mapView) {
            mCallout.setVisibility(View.GONE);
        }
    };
    
    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
    
    private static class StateHolder {
    	private Group<Checkin> mCheckins;
    	
    	public StateHolder() {
    		mCheckins = new Group<Checkin>();
    	}
    	
    	public Group<Checkin> getCheckins() {
    		return mCheckins;
    	}
    	
    	public void setCheckins(Group<Checkin> checkins) {
    		mCheckins = checkins;
    	}
    }
}
