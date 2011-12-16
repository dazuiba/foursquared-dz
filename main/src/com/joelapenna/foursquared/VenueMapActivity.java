/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquare.util.VenueUtils;
import com.joelapenna.foursquared.maps.CrashFixMyLocationOverlay;
import com.joelapenna.foursquared.maps.VenueItemizedOverlay;
import com.joelapenna.foursquared.util.UiUtil;

import android.os.Bundle;
import android.util.Log;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class VenueMapActivity extends MapActivity {
    public static final String TAG = "VenueMapActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueMapActivity.INTENT_EXTRA_VENUE";
 
    private MapView mMapView;
    private MapController mMapController;
    private VenueItemizedOverlay mOverlay = null;
    private MyLocationOverlay mMyLocationOverlay = null;
    
    private StateHolder mStateHolder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.venue_map_activity);
        
        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
        } else {
            mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
            	mStateHolder.setVenue((Venue)getIntent().getExtras().getParcelable(INTENT_EXTRA_VENUE));
            } else {
                Log.e(TAG, "VenueMapActivity requires a venue parcel its intent extras.");
                finish();
                return;
            }
        }
        
        ensureUi();
    }
    
    private void ensureUi() {

        /*
        Button mapsButton = (Button) findViewById(R.id.mapsButton);
        mapsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse( //
                        "geo:0,0?q=" + mStateHolder.getVenue().getName() + " near " + 
                        mStateHolder.getVenue().getCity()));
                startActivity(intent);
            }
        });
        
        if (FoursquaredSettings.SHOW_VENUE_MAP_BUTTON_MORE == false) {
            mapsButton.setVisibility(View.GONE);
        }
        */
        
        setTitle(getString(R.string.venue_map_activity_title, mStateHolder.getVenue().getName()));

        mMapView = (MapView) findViewById(R.id.mapView);
        mMapView.setBuiltInZoomControls(true);
        mMapController = mMapView.getController();

        mMyLocationOverlay = new CrashFixMyLocationOverlay(this, mMapView);
        mMapView.getOverlays().add(mMyLocationOverlay);

        mOverlay = new VenueItemizedOverlay(this.getResources().getDrawable(
                R.drawable.map_marker_blue));
        
        if (VenueUtils.hasValidLocation(mStateHolder.getVenue())) {
	        Group<Venue> venueGroup = new Group<Venue>();
	        venueGroup.setType("Current Venue");
	        venueGroup.add(mStateHolder.getVenue());
            mOverlay.setGroup(venueGroup);
            mMapView.getOverlays().add(mOverlay);
        }
        
        updateMap();
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

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    private void updateMap() {
        if (mOverlay != null && mOverlay.size() > 0) {
            GeoPoint center = mOverlay.getCenter();
            mMapController.animateTo(center);
            mMapController.setZoom(17);
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
