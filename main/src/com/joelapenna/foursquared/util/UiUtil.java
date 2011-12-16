/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

/**
 * @date September 15, 2010.
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class UiUtil {
    
    private static final String TAG = "UiUtil";

    public static int sdkVersion() {
    	return new Integer(Build.VERSION.SDK).intValue();
    }
    
    public static void startDialer(Context context, String phoneNumber) {
        try {
            Intent dial = new Intent();
            dial.setAction(Intent.ACTION_DIAL);
            dial.setData(Uri.parse("tel:" + phoneNumber));
            context.startActivity(dial);
        } catch (Exception ex) {
            Log.e(TAG, "Error starting phone dialer intent.", ex);
            Toast.makeText(context, "Sorry, we couldn't find any app to place a phone call!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static void startSmsIntent(Context context, String phoneNumber) {
        try {
            Uri uri = Uri.parse("sms:" + phoneNumber);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra("address", phoneNumber);
            intent.setType("vnd.android-dir/mms-sms");
            context.startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Error starting sms intent.", ex);
            Toast.makeText(context, "Sorry, we couldn't find any app to send an SMS!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static void startEmailIntent(Context context, String emailAddress) {
        try {
            Intent intent = new Intent(android.content.Intent.ACTION_SEND);
            intent.setType("plain/text");
            intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] {
                emailAddress
            });
            context.startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Error starting email intent.", ex);
            Toast.makeText(context, "Sorry, we couldn't find any app for sending emails!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static void startWebIntent(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Error starting url intent.", ex);
            Toast.makeText(context, "Sorry, we couldn't find any app for viewing this url!",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
