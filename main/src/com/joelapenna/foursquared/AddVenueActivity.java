/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.Category;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Response;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

/**
 * Allows the user to add a new venue. This activity can also be used to submit
 * edits to an existing venue. Pass a venue parcelable using the EXTRA_VENUE_TO_EDIT
 * key to put the activity into edit mode.
 *  
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *   -Added support for using this activity to edit existing venues (June 8, 2010).
 */
public class AddVenueActivity extends Activity {
    private static final String TAG = "AddVenueActivity";
    private static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String EXTRA_VENUE_TO_EDIT = "com.joelapenna.foursquared.VenueParcel";

    private static final double MINIMUM_ACCURACY_FOR_ADDRESS = 100.0;
    
    private static final int DIALOG_PICK_CATEGORY = 1;
    private static final int DIALOG_ERROR = 2;
    

    private StateHolder mStateHolder;

    private EditText mNameEditText;
    private EditText mAddressEditText;
    private EditText mCrossstreetEditText;
    private EditText mCityEditText;
    private EditText mStateEditText;
    private EditText mZipEditText;
    private EditText mPhoneEditText;
    private Button mAddOrEditVenueButton;
    private LinearLayout mCategoryLayout;
    private ImageView mCategoryImageView;
    private TextView mCategoryTextView;
    private ProgressBar mCategoryProgressBar;

    private ProgressDialog mDlgProgress;
    
    
    
    private TextWatcher mNameFieldWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mAddOrEditVenueButton.setEnabled(canEnableSaveButton());
        }
    };

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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.add_venue_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        mAddOrEditVenueButton = (Button) findViewById(R.id.addVenueButton);
        mNameEditText = (EditText) findViewById(R.id.nameEditText);
        mAddressEditText = (EditText) findViewById(R.id.addressEditText);
        mCrossstreetEditText = (EditText) findViewById(R.id.crossstreetEditText);
        mCityEditText = (EditText) findViewById(R.id.cityEditText);
        mStateEditText = (EditText) findViewById(R.id.stateEditText);
        mZipEditText = (EditText) findViewById(R.id.zipEditText);
        mPhoneEditText = (EditText) findViewById(R.id.phoneEditText);
        mCategoryLayout = (LinearLayout) findViewById(R.id.addVenueCategoryLayout);
        mCategoryImageView = (ImageView) findViewById(R.id.addVenueCategoryIcon);
        mCategoryTextView = (TextView) findViewById(R.id.addVenueCategoryTextView);
        mCategoryProgressBar = (ProgressBar) findViewById(R.id.addVenueCategoryProgressBar);
        
        mCategoryLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showDialog(DIALOG_PICK_CATEGORY);
            }
        });
        mCategoryLayout.setEnabled(false);
        
        mAddOrEditVenueButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                String name = mNameEditText.getText().toString();
                String address = mAddressEditText.getText().toString();
                String crossstreet = mCrossstreetEditText.getText().toString();
                String city = mCityEditText.getText().toString();
                String state = mStateEditText.getText().toString();
                String zip = mZipEditText.getText().toString();
                String phone = mPhoneEditText.getText().toString();
                if (mStateHolder.getVenueBeingEdited() != null) {
                    if (TextUtils.isEmpty(name)) {
                        showDialogError(getResources().getString(
                                R.string.add_venue_activity_error_no_venue_name));
                        return;
                    } else if (TextUtils.isEmpty(address)) {
                        showDialogError(getResources().getString(
                                R.string.add_venue_activity_error_no_venue_address));
                        return;
                    } else if (TextUtils.isEmpty(city)) {
                        showDialogError(getResources().getString(
                                R.string.add_venue_activity_error_no_venue_city));
                        return;
                    } else if (TextUtils.isEmpty(state)) {
                        showDialogError(getResources().getString(
                                R.string.add_venue_activity_error_no_venue_state));
                        return;
                    }
                }
                
                mStateHolder.startTaskAddOrEditVenue(
                    AddVenueActivity.this,
                    new String[] {
                        name,
                        address,
                        crossstreet,
                        city,
                        state,
                        zip,
                        phone,
                        mStateHolder.getChosenCategory() != null ? 
                                mStateHolder.getChosenCategory().getId() : ""
                    },
                    // If editing a venue, pass in its id.
                    mStateHolder.getVenueBeingEdited() != null ? 
                            mStateHolder.getVenueBeingEdited().getId() : null);
            }
        });
        
        mNameEditText.addTextChangedListener(mNameFieldWatcher);

        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivity(this);
            
            setFields(mStateHolder.getAddressLookup());
            setChosenCategory(mStateHolder.getChosenCategory());
            if (mStateHolder.getCategories() != null && mStateHolder.getCategories().size() > 0) {
                mCategoryLayout.setEnabled(true);
                mCategoryProgressBar.setVisibility(View.GONE);
            }
        } else {
            mStateHolder = new StateHolder();
            mStateHolder.startTaskGetCategories(this);
            
            // If passed the venue parcelable, then we are in 'edit' mode.
            if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(EXTRA_VENUE_TO_EDIT)) {
                Venue venue = getIntent().getExtras().getParcelable(EXTRA_VENUE_TO_EDIT);
                if (venue != null) {
                    mStateHolder.setVenueBeingEdited(venue);
                    setFields(venue);
                
                    setTitle(getResources().getString(R.string.add_venue_activity_label_edit_venue));
                    
                    mAddOrEditVenueButton.setText(getResources().getString(
                            R.string.add_venue_activity_btn_submit_edits));
                } else {
                    Log.e(TAG, "Null venue parcelable supplied at startup, will finish immediately.");
                    finish();
                }
            } else {
                mStateHolder.startTaskAddressLookup(this);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ((Foursquared) getApplication()).requestLocationUpdates(true);
        
        if (mStateHolder.getIsRunningTaskAddOrEditVenue()) {
            startProgressBar();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        ((Foursquared) getApplication()).removeLocationUpdates();
        
        stopProgressBar();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivity(null);
        return mStateHolder;
    }
    
    private void showDialogError(String message) {
        mStateHolder.setError(message);
        showDialog(DIALOG_ERROR);
    }

    /**
     * Set fields from an address lookup, only used when adding a venue. This is done
     * to prepopulate some fields for the user.
     */
    private void setFields(AddressLookup addressLookup) {
        if (mStateHolder.getVenueBeingEdited() == null &&
            addressLookup != null && 
            addressLookup.getAddress() != null) {

            // Don't fill in the street unless we're reasonably confident we
            // know where the user is.
            String address = addressLookup.getAddress().getAddressLine(0);
            double accuracy = addressLookup.getLocation().getAccuracy();
            if (address != null && (accuracy > 0.0 && accuracy < MINIMUM_ACCURACY_FOR_ADDRESS)) {
                if (DEBUG) Log.d(TAG, "Accuracy good enough, setting address field.");
                mAddressEditText.setText(address);
            }

            String city = addressLookup.getAddress().getLocality();
            if (city != null) {
                mCityEditText.setText(city);
            }

            String state = addressLookup.getAddress().getAdminArea();
            if (state != null) {
                mStateEditText.setText(state);
            }

            String zip = addressLookup.getAddress().getPostalCode();
            if (zip != null) {
                mZipEditText.setText(zip);
            }

            String phone = addressLookup.getAddress().getPhone();
            if (phone != null) {
                mPhoneEditText.setText(phone);
            }
        }
    }
    
    /**
     * Set fields from an existing venue, this is only used when editing a venue.
     */
    private void setFields(Venue venue) {
        mNameEditText.setText(venue.getName());
        mCrossstreetEditText.setText(venue.getCrossstreet());
        mAddressEditText.setText(venue.getAddress());
        mCityEditText.setText(venue.getCity());
        mStateEditText.setText(venue.getState());
        mZipEditText.setText(venue.getZip());
        mPhoneEditText.setText(venue.getPhone());
    }
    
    private void startProgressBar() {
        startProgressBar(
                getResources().getString(
                    mStateHolder.getVenueBeingEdited() == null ? 
                            R.string.add_venue_progress_bar_message_add_venue :
                            R.string.add_venue_progress_bar_message_edit_venue));
    }
    
    private void startProgressBar(String message) {
        if (mDlgProgress == null) {
            mDlgProgress = ProgressDialog.show(this, null, message);
        }
        mDlgProgress.show();
    }

    private void stopProgressBar() {
        if (mDlgProgress != null) {
            mDlgProgress.dismiss();
            mDlgProgress = null;
        }
    }
    
    private void onGetCategoriesTaskComplete(Group<Category> categories, Exception ex) {
        mStateHolder.setIsRunningTaskGetCategories(false);
        try {
            // Populate the categories list now.
            if (categories != null) {
                mStateHolder.setCategories(categories);
                mCategoryLayout.setEnabled(true);
                mCategoryTextView.setText(getResources().getString(R.string.add_venue_activity_pick_category_label));
                mCategoryProgressBar.setVisibility(View.GONE);
                
                // If we are editing a venue, set its category here.
                if (mStateHolder.getVenueBeingEdited() != null) {
                    Venue venue = mStateHolder.getVenueBeingEdited();
                    if (venue.getCategory() != null) {
                        setChosenCategory(venue.getCategory());
                    }
                }
            } else {
                // If error, feed list adapter empty user group.
                mStateHolder.setCategories(new Group<Category>());
                NotificationsUtil.ToastReasonForFailure(this, ex);
            }
        } finally {
        }
        stopIndeterminateProgressBar();
    }
    
    private void ooGetAddressLookupTaskComplete(AddressLookup addressLookup, Exception ex) {
        mStateHolder.setIsRunningTaskAddressLookup(false);
        stopIndeterminateProgressBar();
        
        if (addressLookup != null) {
            mStateHolder.setAddressLookup(addressLookup);
            setFields(addressLookup);
        } else {
            // Nothing to do on failure, don't need to report.
        }
    }

    private void onAddOrEditVenueTaskComplete(Venue venue, String venueIdIfEditing, Exception ex) {
        mStateHolder.setIsRunningTaskAddOrEditVenue(false);
        stopProgressBar();
        
        if (venueIdIfEditing == null) {
            if (venue != null) {
                // If they added the venue ok, then send them to an activity displaying it
                // so they can play around with it.
                Intent intent = new Intent(AddVenueActivity.this, VenueActivity.class);
                intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_PARTIAL, venue);
                startActivity(intent);
                finish();
            } else {
                // Error, let them hang out here.
                NotificationsUtil.ToastReasonForFailure(this, ex);
            }
        } else {
            if (venue != null) {
                // Editing the venue worked ok, just return to caller.
                Toast.makeText(this, getResources().getString(
                        R.string.add_venue_activity_edit_venue_success), 
                        Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // Error, let them hang out here.
                NotificationsUtil.ToastReasonForFailure(this, ex);
            }
        }
    }
    
    private void stopIndeterminateProgressBar() {
        if (mStateHolder.getIsRunningTaskAddressLookup() == false &&
            mStateHolder.getIsRunningTaskGetCategories() == false) {
            setProgressBarIndeterminateVisibility(false);
        }
    }

    private static class AddOrEditVenueTask extends AsyncTask<Void, Void, Venue> {

        private AddVenueActivity mActivity;
        private String[] mParams;
        private String mVenueIdIfEditing;
        private Exception mReason;
        private Foursquared mFoursquared;
        private String mErrorMsgForEditVenue;

        public AddOrEditVenueTask(AddVenueActivity activity, 
                            String[] params,
                            String venueIdIfEditing) {
            mActivity = activity;
            mParams = params;
            mVenueIdIfEditing = venueIdIfEditing;
            mFoursquared = (Foursquared) activity.getApplication();
            mErrorMsgForEditVenue = activity.getResources().getString(
                    R.string.add_venue_activity_edit_venue_fail);
        }

        public void setActivity(AddVenueActivity activity) {
            mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.startProgressBar();
        }

        @Override
        protected Venue doInBackground(Void... params) {
            try {
                Foursquare foursquare = mFoursquared.getFoursquare();
                Location location = mFoursquared.getLastKnownLocationOrThrow();

                if (mVenueIdIfEditing == null) {
                    return foursquare.addVenue(
                            mParams[0], // name
                            mParams[1], // address
                            mParams[2], // cross street
                            mParams[3], // city
                            mParams[4], // state,
                            mParams[5], // zip
                            mParams[6], // phone
                            mParams[7], // category id
                            LocationUtils.createFoursquareLocation(location));
                } else {
                    Response response =
                        foursquare.proposeedit(
                            mVenueIdIfEditing,
                            mParams[0], // name
                            mParams[1], // address
                            mParams[2], // cross street
                            mParams[3], // city
                            mParams[4], // state,
                            mParams[5], // zip
                            mParams[6], // phone
                            mParams[7], // category id
                            LocationUtils.createFoursquareLocation(location));
                    if (response != null && response.getValue().equals("ok")) {
                        // TODO: Come up with a better method than returning an empty venue on success.
                        return new Venue();
                    } else {
                        throw new Exception(mErrorMsgForEditVenue);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during add or edit venue.", e);
                mReason = e;
            }
            
            return null;
        }

        @Override
        protected void onPostExecute(Venue venue) {
            if (DEBUG) Log.d(TAG, "onPostExecute()");
            if (mActivity != null) {
                mActivity.onAddOrEditVenueTaskComplete(venue, mVenueIdIfEditing, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onAddOrEditVenueTaskComplete(null, mVenueIdIfEditing, mReason);
            }
        }
    }

    private static class AddressLookupTask extends AsyncTask<Void, Void, AddressLookup> {

        private AddVenueActivity mActivity;
        private Exception mReason;

        public AddressLookupTask(AddVenueActivity activity) {
            mActivity = activity;
        }

        public void setActivity(AddVenueActivity activity) {
            mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected AddressLookup doInBackground(Void... params) {
            try {
                Location location = ((Foursquared)mActivity.getApplication()).getLastKnownLocationOrThrow();
                Geocoder geocoder = new Geocoder(mActivity);
                
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && addresses.size() > 0) {
                    Log.i(TAG, "Address found: " + addresses.toString());
                    return new AddressLookup(location, addresses.get(0));
                } else {
                    Log.i(TAG, "No address could be found for current location.");
                    throw new FoursquareException("No address could be found for the current geolocation.");
                }

            } catch (Exception ex) {
                Log.e(TAG, "Error during address lookup.", ex);
                mReason = ex;
            }
            return null;
        }

        @Override
        protected void onPostExecute(AddressLookup address) {
            if (mActivity != null) {
                mActivity.ooGetAddressLookupTaskComplete(address, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.ooGetAddressLookupTaskComplete(null, mReason);
            }
        }
    }

    private static class GetCategoriesTask extends AsyncTask<Void, Void, Group<Category>> {

        private AddVenueActivity mActivity;
        private Exception mReason;

        public GetCategoriesTask(AddVenueActivity activity) {
            mActivity = activity;
        }

        public void setActivity(AddVenueActivity activity) {
            mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Group<Category> doInBackground(Void... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();
                return foursquare.categories();
            } catch (Exception e) {
                if (DEBUG)
                    Log.d(TAG, "GetCategoriesTask: Exception doing send friend request.", e);
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Group<Category> categories) {
            if (DEBUG) Log.d(TAG, "GetCategoriesTask: onPostExecute()");
            if (mActivity != null) {
                mActivity.onGetCategoriesTaskComplete(categories, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onGetCategoriesTaskComplete(null,
                        new Exception("Get categories task request cancelled."));
            }
        }
    }

    private static class StateHolder {
        
        private AddressLookupTask mTaskGetAddress;
        private AddressLookup mAddressLookup;
        private boolean mIsRunningTaskAddressLookup;
        private GetCategoriesTask mTaskGetCategories;
        private Group<Category> mCategories;
        private boolean mIsRunningTaskGetCategories;
        private AddOrEditVenueTask mTaskAddOrEditVenue;
        private boolean mIsRunningTaskAddOrEditVenue;
        private Category mChosenCategory;
        private Venue mVenueBeingEdited;
        private String mError;
        
        
        public StateHolder() {
            mCategories = new Group<Category>();
            mIsRunningTaskAddressLookup = false;
            mIsRunningTaskGetCategories = false;
            mIsRunningTaskAddOrEditVenue = false;
            mVenueBeingEdited = null;
        }

        public void setCategories(Group<Category> categories) {
            mCategories = categories;
        }

        public void setAddressLookup(AddressLookup addressLookup) {
            mAddressLookup = addressLookup;
        }
        
        public Group<Category> getCategories() {
            return mCategories;
        }
        
        public AddressLookup getAddressLookup() {
            return mAddressLookup;
        }
        
        public Venue getVenueBeingEdited() {
            return mVenueBeingEdited;
        }
        
        public void setVenueBeingEdited(Venue venue) {
            mVenueBeingEdited = venue;
        }

        public void startTaskGetCategories(AddVenueActivity activity) {
            mIsRunningTaskGetCategories = true;
            mTaskGetCategories = new GetCategoriesTask(activity);
            mTaskGetCategories.execute();
        }

        public void startTaskAddressLookup(AddVenueActivity activity) {
            mIsRunningTaskAddressLookup = true;
            mTaskGetAddress = new AddressLookupTask(activity);
            mTaskGetAddress.execute();
        }

        public void startTaskAddOrEditVenue(AddVenueActivity activity, String[] params, 
                String venueIdIfEditing) {
            mIsRunningTaskAddOrEditVenue = true;
            mTaskAddOrEditVenue = new AddOrEditVenueTask(activity, params, venueIdIfEditing);
            mTaskAddOrEditVenue.execute();
        }

        public void setActivity(AddVenueActivity activity) {
            if (mTaskGetCategories != null) {
                mTaskGetCategories.setActivity(activity);
            }
            if (mTaskGetAddress != null) {
                mTaskGetAddress.setActivity(activity);
            }
            if (mTaskAddOrEditVenue != null) {
                mTaskAddOrEditVenue.setActivity(activity);
            }
        }
        
        public void setIsRunningTaskAddressLookup(boolean isRunning) {
            mIsRunningTaskAddressLookup = isRunning;
        }
        
        public void setIsRunningTaskGetCategories(boolean isRunning) {
            mIsRunningTaskGetCategories = isRunning;
        }

        public void setIsRunningTaskAddOrEditVenue(boolean isRunning) {
            mIsRunningTaskAddOrEditVenue = isRunning;
        }
        
        public boolean getIsRunningTaskAddressLookup() {
            return mIsRunningTaskAddressLookup;
        }

        public boolean getIsRunningTaskGetCategories() {
            return mIsRunningTaskGetCategories;
        }
        
        public boolean getIsRunningTaskAddOrEditVenue() {
            return mIsRunningTaskAddOrEditVenue;
        }
        
        public Category getChosenCategory() {
            return mChosenCategory;
        }
        
        public void setChosenCategory(Category category) {
            mChosenCategory = category;
        }
        
        public String getError() {
            return mError;
        }
        
        public void setError(String error) {
            mError = error;
        }
    }
    
    private static class AddressLookup {
        private Location mLocation;
        private Address mAddress;
        
        public AddressLookup(Location location, Address address) {
            mLocation = location;
            mAddress = address;
        }
        
        public Location getLocation() {
            return mLocation;
        }
        
        public Address getAddress() {
            return mAddress;
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_PICK_CATEGORY:
                // When the user cancels the dialog (by hitting the 'back' key), we
                // finish this activity. We don't listen to onDismiss() for this
                // action, because a device rotation will fire onDismiss(), and our
                // dialog would not be re-displayed after the rotation is complete.
                CategoryPickerDialog dlg = new CategoryPickerDialog(
                    this, 
                    mStateHolder.getCategories(), 
                    ((Foursquared)getApplication()));
                dlg.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        CategoryPickerDialog dlg = (CategoryPickerDialog)dialog;
                        setChosenCategory(dlg.getChosenCategory());
                        removeDialog(DIALOG_PICK_CATEGORY);
                    }
                });
                return dlg;
                
            case DIALOG_ERROR:
                AlertDialog dlgInfo = new AlertDialog.Builder(this)
                    .setIcon(0)
                    .setTitle(getResources().getString(R.string.add_venue_progress_bar_title_edit_venue))
                    .setMessage(mStateHolder.getError()).create();
                dlgInfo.setOnDismissListener(new OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        removeDialog(DIALOG_ERROR);
                    }
                });
            
            return dlgInfo;
        }
        return null;
    }
    
    private void setChosenCategory(Category category) {
        if (category == null) {
            mCategoryTextView.setText(getResources().getString(
                    R.string.add_venue_activity_pick_category_label));
            return;
        }
        
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(
                ((Foursquared)getApplication()).getRemoteResourceManager().getInputStream(
                    Uri.parse(category.getIconUrl())));
            mCategoryImageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "Error loading category icon.", e);
        }
        
        mCategoryTextView.setText(category.getNodeName());
        
        // Record the chosen category.
        mStateHolder.setChosenCategory(category);
        
        if (canEnableSaveButton()) {
            mAddOrEditVenueButton.setEnabled(canEnableSaveButton());
        }
    }
    
    private boolean canEnableSaveButton() {
        return mNameEditText.getText().length() > 0;
    }
}
