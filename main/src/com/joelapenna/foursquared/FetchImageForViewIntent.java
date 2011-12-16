/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquared.util.NotificationsUtil;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Handles fetching an image from the web, then writes it to a temporary file on
 * the sdcard so we can hand it off to a view intent. This activity can be
 * styled with a custom transparent-background theme, so that it appears like a
 * simple progress dialog to the user over whichever activity launches it,
 * instead of two seaparate activities before they finally get to the
 * image-viewing activity. The only required intent extra is the URL to download
 * the image, the others are optional:
 * <ul>
 * <li>IMAGE_URL - String, url of the image to download, either jpeg or png.</li>
 * <li>CONNECTION_TIMEOUT_IN_SECONDS - int, optional, max timeout wait for
 * download, in seconds.</li>
 * <li>READ_TIMEOUT_IN_SECONDS - int, optional, max timeout wait for read of
 * image, in seconds.</li>
 * <li>PROGRESS_BAR_TITLE - String, optional, title of the progress bar during
 * download.</li>
 * <li>PROGRESS_BAR_MESSAGE - String, optional, message body of the progress bar
 * during download.</li>
 * </ul>
 * 
 * When the download is complete, the activity will return the path of the 
 * saved image on disk. The calling activity can then choose to launch an
 * intent to view the image.
 * 
 * @date February 25, 2010
 * @author Mark Wyszomierski (markww@gmail.com), foursquare.
 */
public class FetchImageForViewIntent extends Activity {
    private static final String TAG = "FetchImageForViewIntent";
    private static final boolean DEBUG = FoursquaredSettings.DEBUG;
    private static final String TEMP_FILE_NAME = "tmp_fsq";

    public static final String IMAGE_URL = Foursquared.PACKAGE_NAME
            + ".FetchImageForViewIntent.IMAGE_URL";
    public static final String CONNECTION_TIMEOUT_IN_SECONDS = Foursquared.PACKAGE_NAME
            + ".FetchImageForViewIntent.CONNECTION_TIMEOUT_IN_SECONDS";
    public static final String READ_TIMEOUT_IN_SECONDS = Foursquared.PACKAGE_NAME
            + ".FetchImageForViewIntent.READ_TIMEOUT_IN_SECONDS";
    public static final String PROGRESS_BAR_TITLE = Foursquared.PACKAGE_NAME
            + ".FetchImageForViewIntent.PROGRESS_BAR_TITLE";
    public static final String PROGRESS_BAR_MESSAGE = Foursquared.PACKAGE_NAME
            + ".FetchImageForViewIntent.PROGRESS_BAR_MESSAGE";
    public static final String LAUNCH_VIEW_INTENT_ON_COMPLETION = Foursquared.PACKAGE_NAME
            + ".FetchImageForViewIntent.LAUNCH_VIEW_INTENT_ON_COMPLETION";
    

    public static final String EXTRA_SAVED_IMAGE_PATH_RETURNED = Foursquared.PACKAGE_NAME
            + ".FetchImageForViewIntent.EXTRA_SAVED_IMAGE_PATH_RETURNED";

    private StateHolder mStateHolder;
    private ProgressDialog mDlgProgress;

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
        setContentView(R.layout.fetch_image_for_view_intent_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivity(this);
        } else {
            String url = null;
            if (getIntent().getExtras().containsKey(IMAGE_URL)) {
                url = getIntent().getExtras().getString(IMAGE_URL);
            } else {
                Log.e(TAG, "FetchImageForViewIntent requires intent extras parameter 'IMAGE_URL'.");
                finish();
                return;
            }

            if (DEBUG) Log.d(TAG, "Fetching image: " + url);

            // Grab the extension of the file that should be present at the end
            // of the url. We can do a better job of this, and could even check 
            // that the extension is of an expected type.
            int posdot = url.lastIndexOf(".");
            if (posdot < 0) {
                Log.e(TAG, "FetchImageForViewIntent requires a url to an image resource with a file extension in its name.");
                finish();
                return;
            }

            String progressBarTitle = getIntent().getStringExtra(PROGRESS_BAR_TITLE);
            String progressBarMessage = getIntent().getStringExtra(PROGRESS_BAR_MESSAGE);
            if (progressBarMessage == null) {
                progressBarMessage = "Fetching image...";
            }
            
            mStateHolder = new StateHolder();
            mStateHolder.setLaunchViewIntentOnCompletion(
                    getIntent().getBooleanExtra(LAUNCH_VIEW_INTENT_ON_COMPLETION, true));
            mStateHolder.startTask(
                FetchImageForViewIntent.this, 
                url, 
                url.substring(posdot),
                progressBarTitle, 
                progressBarMessage, 
                getIntent().getIntExtra(CONNECTION_TIMEOUT_IN_SECONDS, 20), 
                getIntent().getIntExtra(READ_TIMEOUT_IN_SECONDS, 20));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mStateHolder.getIsRunning()) {
            startProgressBar(mStateHolder.getProgressTitle(), mStateHolder.getProgressMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
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

    private void startProgressBar(String title, String message) {
        if (mDlgProgress == null) {
            mDlgProgress = ProgressDialog.show(this, title, message);
            mDlgProgress.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dlg) {
                    mStateHolder.cancel();
                    finish();
                }
            });
            mDlgProgress.setCancelable(true);
        }
        mDlgProgress.setTitle(title);
        mDlgProgress.setMessage(message);
    }

    private void stopProgressBar() {
        if (mDlgProgress != null && mDlgProgress.isShowing()) {
            mDlgProgress.dismiss();
        }
        mDlgProgress = null;
    }

    private void onFetchImageTaskComplete(Boolean result, String path, String extension,
            Exception ex) {
        try {
            // If successful, start an intent to view the image.
            if (result != null && result.equals(Boolean.TRUE)) {
                // If the image can't be loaded or an intent can't be found to
                // view it, launchViewIntent() will create a toast with an error
                // message.
                if (mStateHolder.getLaunchViewIntentOnCompletion()) {
                    launchViewIntent(path, extension);
                } else {
                    // We'll finish now by handing the save image path back to the
                    // calling activity.
                    Intent intent = new Intent();
                    intent.putExtra(EXTRA_SAVED_IMAGE_PATH_RETURNED, path);
                    setResult(Activity.RESULT_OK, intent);
                }
            } else {
                NotificationsUtil.ToastReasonForFailure(FetchImageForViewIntent.this, ex);
            }
        } finally {
            // Whether download worked or not, we finish ourselves now. If an
            // error occurred, the toast should remain on the calling activity.
            mStateHolder.setIsRunning(false);
            stopProgressBar();
            finish();
        }
    }

    private boolean launchViewIntent(String outputPath, String extension) {
        
        Foursquared foursquared = (Foursquared) getApplication();
        if (foursquared.getUseNativeImageViewerForFullScreenImages()) {
            // Try to open the file now to create the uri we'll hand to the intent.
            Uri uri = null;
            try {
                File file = new File(outputPath);
                uri = Uri.fromFile(file);
            } catch (Exception ex) {
                Log.e(TAG, "Error opening downloaded image from temp location: ", ex);
                Toast.makeText(this, "No application could be found to diplay the full image.",
                        Toast.LENGTH_SHORT);
                return false;
            }
            
            // Try to start an intent to view the image. It's possible that the user
            // may not have any intents to handle the request.
            try {
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "image/" + extension);
                startActivity(intent);
            } catch (Exception ex) {
                Log.e(TAG, "Error starting intent to view image: ", ex);
                Toast.makeText(this, "There was an error displaying the image.", Toast.LENGTH_SHORT);
                return false;
            }
        } else {
            Intent intent = new Intent(this, FullSizeImageActivity.class);
            intent.putExtra(FullSizeImageActivity.INTENT_EXTRA_IMAGE_PATH, outputPath);
            startActivity(intent);
        }
        
        return true;
    }

    /**
     * Handles fetching the image from the net and saving it to disk in a task.
     */
    private static class FetchImageTask extends AsyncTask<Void, Void, Boolean> {

        private FetchImageForViewIntent mActivity;
        private final String mUrl;
        private String mExtension;
        private final String mOutputPath;
        private final int mConnectionTimeoutInSeconds;
        private final int mReadTimeoutInSeconds;
        private Exception mReason;

        public FetchImageTask(FetchImageForViewIntent activity, String url, String extension,
                int connectionTimeoutInSeconds, int readTimeoutInSeconds) {
            mActivity = activity;
            mUrl = url;
            mExtension = extension;
            mOutputPath = Environment.getExternalStorageDirectory() + "/" + TEMP_FILE_NAME;
            mConnectionTimeoutInSeconds = connectionTimeoutInSeconds;
            mReadTimeoutInSeconds = readTimeoutInSeconds;
        }

        public void setActivity(FetchImageForViewIntent activity) {
            mActivity = activity;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                saveImage(mUrl, mOutputPath, mConnectionTimeoutInSeconds, mReadTimeoutInSeconds);
                return Boolean.TRUE;

            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "FetchImageTask: Exception while fetching image.", e);
                mReason = e;
            }
            return Boolean.FALSE;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (DEBUG) Log.d(TAG, "FetchImageTask: onPostExecute()");
            if (mActivity != null) {
                mActivity.onFetchImageTaskComplete(result, mOutputPath, mExtension, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onFetchImageTaskComplete(null, null, null, 
                    new FoursquareException("Image download cancelled."));
            }
        }
    }

    public static void saveImage(String urlImage, String savePath, int connectionTimeoutInSeconds,
            int readTimeoutInSeconds) throws Exception {
        URL url = new URL(urlImage);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(connectionTimeoutInSeconds * 1000);
        conn.setReadTimeout(readTimeoutInSeconds * 1000);
        int contentLength = conn.getContentLength();
        InputStream raw = conn.getInputStream();
        InputStream in = new BufferedInputStream(raw);
        byte[] data = new byte[contentLength];
        int bytesRead = 0;
        int offset = 0;
        while (offset < contentLength) {
            bytesRead = in.read(data, offset, data.length - offset);
            if (bytesRead == -1) {
                break;
            }
            offset += bytesRead;
        }
        in.close();

        if (offset != contentLength) {
            Log.e(TAG, "Error fetching image, only read " + offset + " bytes of " + contentLength
                    + " total.");
            throw new FoursquareException("Error fetching full image, please try again.");
        }

        // This will fail if the user has no sdcard present, catch it specifically
        // to alert user.
        try {
            FileOutputStream out = new FileOutputStream(savePath);
            out.write(data);
            out.flush();
            out.close();
        } catch (Exception ex) {
            Log.e(TAG, "Error saving fetched image to disk.", ex);
            throw new FoursquareException("Error opening fetched image, make sure an sdcard is present.");
        }
    }

    /** Maintains state between rotations. */
    private static class StateHolder {
        FetchImageTask mTaskFetchImage;
        boolean mIsRunning;
        String mProgressTitle;
        String mProgressMessage;
        boolean mLaunchViewIntentOnCompletion;

        public StateHolder() {
            mIsRunning = false;
            mLaunchViewIntentOnCompletion = true;
        }

        public void startTask(FetchImageForViewIntent activity, String url, String extension,
                String progressBarTitle, String progressBarMessage, int connectionTimeoutInSeconds,
                int readTimeoutInSeconds) {
            mIsRunning = true;
            mProgressTitle = progressBarTitle;
            mProgressMessage = progressBarMessage;
            mTaskFetchImage = new FetchImageTask(activity, url, extension,
                    connectionTimeoutInSeconds, readTimeoutInSeconds);
            mTaskFetchImage.execute();
        }

        public void setActivity(FetchImageForViewIntent activity) {
            if (mTaskFetchImage != null) {
                mTaskFetchImage.setActivity(activity);
            }
        }

        public void setIsRunning(boolean isRunning) {
            mIsRunning = isRunning;
        }

        public boolean getIsRunning() {
            return mIsRunning;
        }

        public String getProgressTitle() {
            return mProgressTitle;
        }

        public String getProgressMessage() {
            return mProgressMessage;
        }
        
        public void cancel() {
            if (mTaskFetchImage != null) {
                mTaskFetchImage.cancel(true);
                mIsRunning = false;
            }
        }
        
        public boolean getLaunchViewIntentOnCompletion() {
            return mLaunchViewIntentOnCompletion;
        }
        
        public void setLaunchViewIntentOnCompletion(boolean launchViewIntentOnCompletion) {
            mLaunchViewIntentOnCompletion = launchViewIntentOnCompletion;
        }
    }
}
