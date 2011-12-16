/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.maps.CrashFixMyLocationOverlay;
import com.joelapenna.foursquared.maps.VenueItemizedOverlayWithIcons;
import com.joelapenna.foursquared.maps.VenueItemizedOverlayWithIcons.VenueItemizedOverlayTapListener;
import com.joelapenna.foursquared.util.GeoUtils;
import com.joelapenna.foursquared.util.UiUtil;
import com.joelapenna.foursquared.widget.MapCalloutView;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Takes an array of venues and shows them on a map.
 * 
 * @date June 30, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class NearbyVenuesMapActivity extends MapActivity {
    public static final String TAG = "NearbyVenuesMapActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    public static final String INTENT_EXTRA_VENUES = Foursquared.PACKAGE_NAME
            + ".NearbyVenuesMapActivity.INTENT_EXTRA_VENUES";

    private StateHolder mStateHolder;
    
    private String mTappedVenueId;
    private MapCalloutView mCallout;
    private MapView mMapView;
    private MapController mMapController;
    private ArrayList<VenueItemizedOverlayWithIcons> mVenueGroupOverlays = 
        new ArrayList<VenueItemizedOverlayWithIcons>();
    private MyLocationOverlay mMyLocationOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_map_activity);
        
        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
        } else {
            if (getIntent().hasExtra(INTENT_EXTRA_VENUES)) {
                Parcelable[] parcelables = getIntent().getParcelableArrayExtra(
                        INTENT_EXTRA_VENUES);
                
                Group<Venue> venues = new Group<Venue>();
                for (int i = 0; i < parcelables.length; i++) {
                    venues.add((Venue)parcelables[i]);
                }
                
                mStateHolder = new StateHolder(venues);
            } else {
                Log.e(TAG, TAG + " requires venue array in intent extras.");
                finish();
                return;
            }
        }
        
        ensureUi();
    }
    
    private void ensureUi() {
        
        mMapView = (MapView) findViewById(R.id.mapView);
        mMapView.setBuiltInZoomControls(true);
        mMapController = mMapView.getController();

        mMyLocationOverlay = new CrashFixMyLocationOverlay(this, mMapView);
        mMapView.getOverlays().add(mMyLocationOverlay);

        mCallout = (MapCalloutView) findViewById(R.id.map_callout);
        mCallout.setVisibility(View.GONE);
        mCallout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(NearbyVenuesMapActivity.this, VenueActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_ID, mTappedVenueId);
                startActivity(intent);
            }
        });
        
        // One CheckinItemizedOverlay per group!
        VenueItemizedOverlayWithIcons mappableVenuesOverlay = createMappableVenuesOverlay(
                mStateHolder.getVenues());

        if (mappableVenuesOverlay != null) {
            mVenueGroupOverlays.add(mappableVenuesOverlay);
        }
        
        if (mVenueGroupOverlays.size() > 0) {
            mMapView.getOverlays().addAll(mVenueGroupOverlays);
            
            recenterMap();
        } else {
            Toast.makeText(this, getResources().getString(
                    R.string.friendsmapactivity_no_checkins), Toast.LENGTH_LONG).show();
            finish();
        }
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
        if (UiUtil.sdkVersion() > 3) {
            mMyLocationOverlay.disableCompass();
        }
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    /**
     * We can do something more fun here like create an overlay per category, so the user
     * can hide parks and show only bars, for example.
     */
    private VenueItemizedOverlayWithIcons createMappableVenuesOverlay(Group<Venue> venues) {
        
        Group<Venue> mappableVenues = new Group<Venue>();
        for (Venue it : venues) {
            mappableVenues.add(it);
        }
        
        if (mappableVenues.size() > 0) {
            VenueItemizedOverlayWithIcons overlay = new VenueItemizedOverlayWithIcons(
                    this,
                    ((Foursquared) getApplication()).getRemoteResourceManager(),
                    getResources().getDrawable(R.drawable.pin_checkin_multiple),
                    mVenueOverlayTapListener);
            overlay.setGroup(mappableVenues);
            return overlay;
        } else {
            return null;
        }
    }

    private void recenterMap() {
        // Previously we'd try to zoom to span, but this gives us odd results a lot of times,
        // so falling back to zoom at a fixed level.
        GeoPoint center = mMyLocationOverlay.getMyLocation();
        if (center != null) {
            mMapController.animateTo(center);
            mMapController.setZoom(14);
        } else {
            // Location overlay wasn't ready yet, try using last known geolocation from manager.
            Location bestLocation = GeoUtils.getBestLastGeolocation(this);
            if (bestLocation != null) {
                mMapController.animateTo(GeoUtils.locationToGeoPoint(bestLocation));
                mMapController.setZoom(14);
            } else {
                // We have no location information at all, so we'll just show the map at a high
                // zoom level and the user can zoom in as they wish.
                Venue venue = mStateHolder.getVenues().get(0);
                mMapController.animateTo(new GeoPoint(
                        (int)(Float.valueOf(venue.getGeolat()) * 1E6), 
                        (int)(Float.valueOf(venue.getGeolong()) * 1E6)));
                mMapController.setZoom(8);
            }
        }
    }
 
    /** 
     * Handle taps on one of the pins. 
     */
    private VenueItemizedOverlayTapListener mVenueOverlayTapListener = 
        new VenueItemizedOverlayTapListener() {
        @Override
        public void onTap(OverlayItem itemSelected, OverlayItem itemLastSelected, Venue venue) {
            mTappedVenueId = venue.getId();
            mCallout.setTitle(venue.getName());
            mCallout.setMessage(venue.getAddress());
            mCallout.setVisibility(View.VISIBLE);

            mMapController.animateTo(GeoUtils.stringLocationToGeoPoint(
                    venue.getGeolat(), venue.getGeolong()));
        }

        @Override
        public void onTap(GeoPoint p, MapView mapView) {
            mCallout.setVisibility(View.GONE);
        }
    };
    
    private class StateHolder {
        private Group<Venue> mVenues;
        
        public StateHolder(Group<Venue> venues) {
            mVenues = venues;
        }
        
        public Group<Venue> getVenues() {
            return mVenues;
        }
    }
}
