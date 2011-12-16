/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;


import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.preferences.Preferences;
import com.joelapenna.foursquared.util.ImageUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Takes a path to an image, then displays it by filling all available space while
 * retaining w/h ratio. This is meant to be a (poor) replacement to the native 
 * image viewer intent on some devices. For example, the nexus-one gallery viewer
 * takes about 11 seconds to start up when using the following:
 * 
 *     Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
 *     intent.setDataAndType(uri, "image/" + extension);
 *     startActivity(intent);
 *     
 * other devices might have their own issues.
 * 
 * We can support zooming/panning later on if it's important to users. 
 * 
 * No attempt is made to check the size of the input image, for now we're trusting
 * the foursquare api is keeping these images < 200kb.
 * 
 * The INTENT_EXTRA_ALLOW_SET_NEW_PHOTO flag lets the user pick a new photo from
 * their phone for their user profile.
 * 
 * @date July 28, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 * 
 */
public class FullSizeImageActivity extends Activity {
    private static final String TAG = "FullSizeImageActivity";

    public static final String INTENT_EXTRA_IMAGE_PATH = Foursquared.PACKAGE_NAME
        + ".FullSizeImageActivity.INTENT_EXTRA_IMAGE_PATH";
    public static final String INTENT_EXTRA_ALLOW_SET_NEW_PHOTO = Foursquared.PACKAGE_NAME
        + ".FullSizeImageActivity.INTENT_EXTRA_ALLOW_SET_NEW_PHOTO";
    
    public static final String INTENT_RETURN_NEW_PHOTO_PATH_DISK = Foursquared.PACKAGE_NAME
        + ".FullSizeImageActivity.INTENT_RETURN_NEW_PHOTO_PATH_DISK";
    public static final String INTENT_RETURN_NEW_PHOTO_URL = Foursquared.PACKAGE_NAME
        + ".FullSizeImageActivity.INTENT_RETURN_NEW_PHOTO_URL";
    
    private static final int ACTIVITY_REQUEST_CODE_GALLERY = 500;
    
    private static final int DIALOG_SET_USER_PHOTO_YES_NO = 500;
    
    private StateHolder mStateHolder;
    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.full_size_image_activity);
        
        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivity(this);
            setPreparedResultIntent();
        } else {
            String imagePath = getIntent().getStringExtra(INTENT_EXTRA_IMAGE_PATH);
            if (!TextUtils.isEmpty(imagePath)) {
                mStateHolder = new StateHolder();
                mStateHolder.setImagePath(imagePath);
                mStateHolder.setAllowSetPhoto(getIntent().getBooleanExtra(
                        INTENT_EXTRA_ALLOW_SET_NEW_PHOTO, false));
            } else {
                Log.e(TAG, TAG + " requires input image path as an intent extra.");
                finish();
                return;
            }
        }
        
        ensureUi();
    }
    
    private void ensureUi() {
        ImageView iv = (ImageView)findViewById(R.id.imageView);
        try {
            Bitmap bmp = BitmapFactory.decodeFile(mStateHolder.getImagePath());
            iv.setImageBitmap(bmp);
        } catch (Exception ex) {
            Log.e(TAG, "Couldn't load supplied image.", ex);
            finish();
            return;
        }
        
        LinearLayout llSetPhoto = (LinearLayout)findViewById(R.id.setPhotoOption);
        Button btnSetPhoto = (Button)findViewById(R.id.setPhotoOptionBtn);
        if (mStateHolder.getAllowSetPhoto()) {
            llSetPhoto.setVisibility(View.VISIBLE);
            btnSetPhoto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    startGalleryIntent();
                }
            });
        } else {
            llSetPhoto.setVisibility(View.GONE);
        }
        
        if (mStateHolder.getIsRunningTaskSetPhoto()) {
            setProgressBarIndeterminateVisibility(true);
            btnSetPhoto.setEnabled(false);
        } else {
            setProgressBarIndeterminateVisibility(false);
            btnSetPhoto.setEnabled(true);
        }
    }
    
    private void startGalleryIntent() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT); 
            intent.setType("image/*"); 
            startActivityForResult(intent, ACTIVITY_REQUEST_CODE_GALLERY);
        }
        catch (Exception ex) {
            Toast.makeText(this, getResources().getString(R.string.user_details_activity_error_no_photo_gallery), 
                Toast.LENGTH_SHORT).show(); 
        }
    }
    
    private void prepareResultIntent(String newPhotoUrl) {
        Intent intent = new Intent();
        intent.putExtra(INTENT_RETURN_NEW_PHOTO_PATH_DISK, mStateHolder.getImagePath());
        intent.putExtra(INTENT_RETURN_NEW_PHOTO_URL, newPhotoUrl);
        mStateHolder.setPreparedResult(intent);
        setPreparedResultIntent();
    }
    
    private void setPreparedResultIntent() {
        if (mStateHolder.getPreparedResult() != null) {
            setResult(Activity.RESULT_OK, mStateHolder.getPreparedResult());
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)  {
        String pathInput = null;
        switch (requestCode) {
            case ACTIVITY_REQUEST_CODE_GALLERY:
                if (resultCode == Activity.RESULT_OK) { 
                    try {
                        String [] proj = { MediaStore.Images.Media.DATA };  
                        Cursor cursor = managedQuery(data.getData(), proj, null, null, null);  
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);  
                        cursor.moveToFirst();  
                        pathInput = cursor.getString(column_index); 
                    }
                    catch (Exception ex) {
                        Toast.makeText(this, getResources().getString(R.string.user_details_activity_error_set_photo_load), 
                                Toast.LENGTH_SHORT).show();
                    }

                    // If everything worked ok, ask the user if they're sure they want to upload?
                    try {
                        String pathOutput = Environment.getExternalStorageDirectory() + "/tmp_fsquare.jpg";
                        ImageUtils.resampleImageAndSaveToNewLocation(pathInput, pathOutput);
                        mStateHolder.setImagePath(pathOutput);
                        ensureUi();
                        showDialog(DIALOG_SET_USER_PHOTO_YES_NO);
                    }
                    catch (Exception ex) {
                        Toast.makeText(this, getResources().getString(R.string.user_details_activity_error_set_photo_resample), 
                                Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    return;
                }
                break;
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) { 
        switch (id) {  
            case DIALOG_SET_USER_PHOTO_YES_NO: 
                return new AlertDialog.Builder(this) 
                    .setTitle(getResources().getString(R.string.user_details_activity_set_photo_confirm_title)) 
                    .setMessage(getResources().getString(R.string.user_details_activity_set_photo_confirm_message))
                    .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() { 
                        public void onClick(DialogInterface dialog, int whichButton) {
                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(FullSizeImageActivity.this);
                            String username = sp.getString(Preferences.PREFERENCE_LOGIN, "");
                            String password = sp.getString(Preferences.PREFERENCE_PASSWORD, "");
                            mStateHolder.startTaskSetPhoto(
                                    FullSizeImageActivity.this, mStateHolder.getImagePath(), username, password);
                        }
                    }) 
                    .setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() { 
                        public void onClick(DialogInterface dialog, int whichButton) { 
                        }
                    }) 
                    .create(); 
            default:
                return null; 
        } 
    }
    
    private void onTaskSetPhotoCompleteStart() {
        ensureUi();
    }
    
    private void onTaskSetPhotoComplete(User user, Exception ex) {
        mStateHolder.setIsRunningTaskSetPhoto(false);
        if (user != null) {
            Toast.makeText(this, "Photo set ok!", Toast.LENGTH_SHORT).show();
            prepareResultIntent(user.getPhoto());
        } else {
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        ensureUi();
    }
    
    private static class TaskSetPhoto extends AsyncTask<String, Void, User> {

        private FullSizeImageActivity mActivity;
        private Exception mReason;
        

        public TaskSetPhoto(FullSizeImageActivity activity) {
            mActivity = activity;
        }
        
        public void setActivity(FullSizeImageActivity activity) {
            mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.onTaskSetPhotoCompleteStart();
        }
 
        /** Params should be image path, username, password. */
        @Override
        protected User doInBackground(String... params) {
            try {
                return ((Foursquared) mActivity.getApplication()).getFoursquare().userUpdate(
                        params[0], params[1], params[2]);
            } catch (Exception ex) {
                Log.e(TAG, "Error submitting new profile photo.", ex);
                mReason = ex;
            }
            return null;
        }
 
        @Override
        protected void onPostExecute(User user) {
            if (mActivity != null) {
                mActivity.onTaskSetPhotoComplete(user, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskSetPhotoComplete(null, new FoursquareException(
                        mActivity.getResources().getString(R.string.user_details_activity_set_photo_cancel)));
            }
        }
    }
    
    
    private static class StateHolder {
        private String mImagePath;
        private boolean mAllowSetPhoto;
        
        private boolean mIsRunningTaskSetPhoto;
        private TaskSetPhoto mTaskSetPhoto;
        
        private Intent mPreparedResult;
        
        
        public StateHolder() {
            mAllowSetPhoto = false;
            mIsRunningTaskSetPhoto = false;
        }
        
        public String getImagePath() {
            return mImagePath;
        }
        
        public void setImagePath(String imagePath) {
            mImagePath = imagePath;
        }
        
        public boolean getAllowSetPhoto() {
            return mAllowSetPhoto;
        }
        
        public void setAllowSetPhoto(boolean allowSetPhoto) {
            mAllowSetPhoto = allowSetPhoto;
        }
        
        public boolean getIsRunningTaskSetPhoto() {
            return mIsRunningTaskSetPhoto;
        }
        
        public void setIsRunningTaskSetPhoto(boolean isRunning) {
            mIsRunningTaskSetPhoto = isRunning;
        }
        
        public void setActivity(FullSizeImageActivity activity) {
            if (mTaskSetPhoto != null) {
                mTaskSetPhoto.setActivity(activity);
            }
        }
        
        public void startTaskSetPhoto(FullSizeImageActivity activity, 
                String pathImage, String username, String password) {
            if (!mIsRunningTaskSetPhoto) {
                mIsRunningTaskSetPhoto = true;
                mTaskSetPhoto = new TaskSetPhoto(activity);
                mTaskSetPhoto.execute(pathImage, username, password);
            }
        }
        
        public Intent getPreparedResult() {
            return mPreparedResult;
        }
        
        public void setPreparedResult(Intent intent) {
            mPreparedResult = intent;
        }
    }
}
