/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.FoursquareType;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.util.StringFormatters;
import com.joelapenna.foursquared.util.TipUtils;

/**
 * Shows actions a user can perform on a tip, which includes marking a tip
 * as a to-do, marking a tip as done, un-marking a tip. Marking a tip as
 * a to-do will generate a to-do, which has the tip as a child object.
 * 
 * The intent will return a Tip object and a Todo object (if the final state
 * of the tip was marked as a Todo). In the case where a Todo is returned,
 * the Tip will be the representation as found within the Todo object.
 * 
 * If the user does not modify the tip, no intent data is returned. If the
 * final state of the tip was not marked as a to-do, the Todo object is
 * not returned.
 * 
 * @date September 2, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 * 
 */
public class TipActivity extends Activity {
    private static final String TAG = "TipActivity";
    private static final boolean DEBUG = FoursquaredSettings.DEBUG;

    public static final String EXTRA_TIP_PARCEL = Foursquared.PACKAGE_NAME
        + ".TipActivity.EXTRA_TIP_PARCEL";
    public static final String EXTRA_VENUE_CLICKABLE = Foursquared.PACKAGE_NAME
        + ".TipActivity.EXTRA_VENUE_CLICKABLE";
    
    /** 
     * Always returned if the user modifies the tip in any way. Captures the 
     * new <status> attribute of the tip. It may not have been changed by the 
     * user.
     */
    public static final String EXTRA_TIP_RETURNED = Foursquared.PACKAGE_NAME
        + ".TipActivity.EXTRA_TIP_RETURNED";
    
    /** 
     * If the user marks the tip as to-do as the final state, then a to-do object
     * will also be returned here. The to-do object has the same tip object as 
     * returned in EXTRA_TIP_PARCEL_RETURNED as a child member.
     */
    public static final String EXTRA_TODO_RETURNED = Foursquared.PACKAGE_NAME
        + ".TipActivity.EXTRA_TODO_RETURNED";
    
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tip_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));
        
        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivityForTipTask(this);
            setPreparedResultIntent();
        } else {
            mStateHolder = new StateHolder();
            if (getIntent().getExtras() != null) {
            	if (getIntent().hasExtra(EXTRA_TIP_PARCEL)) {
            		Tip tip = getIntent().getExtras().getParcelable(EXTRA_TIP_PARCEL);
            		mStateHolder.setTip(tip);
            	} else {
                    Log.e(TAG, "TipActivity requires a tip pareclable in its intent extras.");
                    finish();
                    return;
            	}
            	
                if (getIntent().hasExtra(EXTRA_VENUE_CLICKABLE)) {
                	mStateHolder.setVenueClickable(
                			getIntent().getBooleanExtra(EXTRA_VENUE_CLICKABLE, true));
                }
            } else {
                Log.e(TAG, "TipActivity requires a tip pareclable in its intent extras.");
                finish();
                return;
            }
        }

        ensureUi();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        if (mStateHolder.getIsRunningTipTask()) {
            startProgressBar();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        if (isFinishing()) {
            stopProgressBar();
        }
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTipTask(null);
        return mStateHolder;
    }

    private void ensureUi() {
        Tip tip = mStateHolder.getTip();
        Venue venue = tip.getVenue();
        
        LinearLayout llHeader = (LinearLayout)findViewById(R.id.tipActivityHeaderView);
        if (mStateHolder.getVenueClickable()) {
	        llHeader.setOnClickListener(new OnClickListener() {
	            @Override
	            public void onClick(View v) {
	                showVenueDetailsActivity(mStateHolder.getTip().getVenue());
	            }
	        });
        }
        
        ImageView ivVenueChevron = (ImageView)findViewById(R.id.tipActivityVenueChevron);
        if (mStateHolder.getVenueClickable()) {
        	ivVenueChevron.setVisibility(View.VISIBLE);
        } else {
        	ivVenueChevron.setVisibility(View.INVISIBLE);
        }

        TextView tvTitle = (TextView)findViewById(R.id.tipActivityName);
        TextView tvAddress = (TextView)findViewById(R.id.tipActivityAddress);
        if (venue != null) {
	        tvTitle.setText(venue.getName());
	        
	        tvAddress.setText(
	            venue.getAddress() + 
	            (TextUtils.isEmpty(venue.getCrossstreet()) ? 
	                    "" : " (" + venue.getCrossstreet() + ")"));
        } else {
        	tvTitle.setText("");
        	tvAddress.setText("");
        }
        
        TextView tvBody = (TextView)findViewById(R.id.tipActivityBody);
        tvBody.setText(tip.getText());
        
        String created = getResources().getString(
                R.string.tip_activity_created, 
                StringFormatters.getTipAge(getResources(), tip.getCreated()));
        TextView tvDate = (TextView)findViewById(R.id.tipActivityDate);
        tvDate.setText(created);
        
        TextView tvAuthor = (TextView)findViewById(R.id.tipActivityAuthor);
        if (tip.getUser() != null) {
            tvAuthor.setText(tip.getUser().getFirstname());
            tvAuthor.setClickable(true);
            tvAuthor.setFocusable(true);
            tvAuthor.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showUserDetailsActivity(mStateHolder.getTip().getUser());
                }
            });
            
            tvDate.setText(tvDate.getText() + getResources().getString(
                    R.string.tip_activity_created_by));
        } else {
            tvAuthor.setText("");
        }
        
        Button btn1 = (Button)findViewById(R.id.tipActivityyAddTodoList);
        Button btn2 = (Button)findViewById(R.id.tipActivityIveDoneThis);
        btn1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnTodo();
            }
        });
        
        btn2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnDone();
            }
        });
        
        updateButtonStates();
    }
    
    private void onBtnTodo() {
        Tip tip = mStateHolder.getTip();
        if (TipUtils.isTodo(tip)) {
            mStateHolder.startTipTask(TipActivity.this, mStateHolder.getTip().getId(), 
                    TipTask.ACTION_UNMARK_TODO);
        } else {
            mStateHolder.startTipTask(TipActivity.this, mStateHolder.getTip().getId(), 
                    TipTask.ACTION_TODO);
        }
    }
    
    private void onBtnDone() {
        Tip tip = mStateHolder.getTip();
        if (TipUtils.isDone(tip)) {
            mStateHolder.startTipTask(TipActivity.this, mStateHolder.getTip().getId(), 
                    TipTask.ACTION_UNMARK_DONE);
        } else {
            mStateHolder.startTipTask(TipActivity.this, mStateHolder.getTip().getId(), 
                    TipTask.ACTION_DONE);
        }
    }
    
    private void updateButtonStates() {
        Button btn1 = (Button)findViewById(R.id.tipActivityyAddTodoList);
        Button btn2 = (Button)findViewById(R.id.tipActivityIveDoneThis);
        TextView tv = (TextView)findViewById(R.id.tipActivityCongrats);
        Tip tip = mStateHolder.getTip();
        
        if (TipUtils.isTodo(tip)) {
            btn1.setText(getResources().getString(R.string.tip_activity_btn_tip_1)); // "REMOVE FROM MY TO-DO LIST"
            btn2.setText(getResources().getString(R.string.tip_activity_btn_tip_2)); // "I'VE DONE THIS"
            btn1.setVisibility(View.VISIBLE);
            tv.setVisibility(View.GONE);
        } else if (TipUtils.isDone(tip)) {
            tv.setText(getResources().getString(R.string.tip_activity_btn_tip_4));   // "CONGRATS! YOU'VE DONE THIS"
            btn2.setText(getResources().getString(R.string.tip_activity_btn_tip_3)); // "UNDO THIS"
            btn1.setVisibility(View.GONE);
            tv.setVisibility(View.VISIBLE);
        } else {
            btn1.setText(getResources().getString(R.string.tip_activity_btn_tip_0)); // "ADD TO MY TO-DO LIST"
            btn2.setText(getResources().getString(R.string.tip_activity_btn_tip_2)); // "I'VE DONE THIS"
            btn1.setVisibility(View.VISIBLE);
            tv.setVisibility(View.GONE);
        }
    }
    
    private void showUserDetailsActivity(User user) {
        Intent intent = new Intent(this, UserDetailsActivity.class);
        intent.putExtra(UserDetailsActivity.EXTRA_USER_ID, user.getId());
        startActivity(intent);
    }
    
    private void showVenueDetailsActivity(Venue venue) {
        Intent intent = new Intent(this, VenueActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_PARTIAL, venue);
        startActivity(intent);
    }
    
    private void startProgressBar() {
        if (mDlgProgress == null) {
            mDlgProgress = ProgressDialog.show(this, "",
                    getResources().getString(R.string.tip_activity_progress_message));
        }
        mDlgProgress.show();
    }

    private void stopProgressBar() {
        if (mDlgProgress != null) {
            mDlgProgress.dismiss();
            mDlgProgress = null;
        }
    }
    
    private void prepareResultIntent(Tip tip, Todo todo) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_TIP_RETURNED, tip);
        if (todo != null) {
        	intent.putExtra(EXTRA_TODO_RETURNED, todo); // tip is also a part of the to-do.
        }
        mStateHolder.setPreparedResult(intent);
        setPreparedResultIntent();
    }
    
    private void setPreparedResultIntent() {
    	if (mStateHolder.getPreparedResult() != null) {
    		setResult(Activity.RESULT_OK, mStateHolder.getPreparedResult());
    	}
    }
    
    private void onTipTaskComplete(FoursquareType tipOrTodo, int type, Exception ex) {
        stopProgressBar();
        mStateHolder.setIsRunningTipTask(false);
        if (tipOrTodo != null) {
        	// When the tip and todo are serialized into the intent result, the
        	// link between them will be lost, they'll appear as two separate
        	// tip object instances (ids etc will all be the same though).
        	if (tipOrTodo instanceof Tip) {
        		Tip tip = (Tip)tipOrTodo;
        		mStateHolder.setTip(tip);
            	prepareResultIntent(tip, null);
        	} else {
        		Todo todo = (Todo)tipOrTodo;
        		Tip tip = todo.getTip();
        		mStateHolder.setTip(tip);
            	prepareResultIntent(tip, todo);
        	}
            
        } else if (ex != null) {
            Toast.makeText(this, ex.toString(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Error updating tip!", Toast.LENGTH_LONG).show();
        }
        
        ensureUi();
    }
    
    private static class TipTask extends AsyncTask<String, Void, FoursquareType> {
        private TipActivity mActivity;
        private String mTipId;
        private int mTask;
        private Exception mReason;
        
        public static final int ACTION_TODO        = 0;
        public static final int ACTION_DONE        = 1;
        public static final int ACTION_UNMARK_TODO = 2;
        public static final int ACTION_UNMARK_DONE = 3;

        public TipTask(TipActivity activity, String tipid, int task) {
            mActivity = activity;
            mTipId = tipid;
            mTask = task;
        }

        public void setActivity(TipActivity activity) {
            mActivity = activity;
        }

        @Override
        protected void onPreExecute() {
            mActivity.startProgressBar();
        }
        
        @Override
        protected FoursquareType doInBackground(String... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();
                switch (mTask) {
                    case ACTION_TODO:
                        return foursquare.markTodo(mTipId);   // returns a todo.
                    case ACTION_DONE:
                        return foursquare.markDone(mTipId);   // returns a tip.
                    case ACTION_UNMARK_TODO:
                    	return foursquare.unmarkTodo(mTipId); // returns a tip
                    case ACTION_UNMARK_DONE:
                    	return foursquare.unmarkDone(mTipId); // returns a tip
                	default:
                		return null;
                }
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "TipTask: Exception performing tip task.", e);
                mReason = e;
            }
            
            return null;
        }

        @Override
        protected void onPostExecute(FoursquareType tipOrTodo) {
            if (DEBUG) Log.d(TAG, "TipTask: onPostExecute()");
            if (mActivity != null) {
                mActivity.onTipTaskComplete(tipOrTodo, mTask, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTipTaskComplete(null, mTask, new Exception("Tip task cancelled."));
            }
        }
    }
    
    private static class StateHolder {
        private Tip mTip;
        private TipTask mTipTask;
        private boolean mIsRunningTipTask;
        private boolean mVenueClickable;
        private Intent mPreparedResult;
        
        
        public StateHolder() {
        	mTip = null;
            mPreparedResult = null;
            mIsRunningTipTask = false;
            mVenueClickable = true;
        }
        
        public Tip getTip() {
        	return mTip;
        }
        
        public void setTip(Tip tip) {
        	mTip = tip;
        }
        
        public void startTipTask(TipActivity activity, String tipId, int task) {
            mIsRunningTipTask = true;
            mTipTask = new TipTask(activity, tipId, task);
            mTipTask.execute();
        }

        public void setActivityForTipTask(TipActivity activity) {
            if (mTipTask != null) {
                mTipTask.setActivity(activity);
            }
        }
        
        public void setIsRunningTipTask(boolean isRunningTipTask) {
            mIsRunningTipTask = isRunningTipTask;
        }
        
        public boolean getIsRunningTipTask() {
            return mIsRunningTipTask;
        }
        
        public boolean getVenueClickable() {
        	return mVenueClickable;
        }
        
        public void setVenueClickable(boolean venueClickable) {
        	mVenueClickable = venueClickable;
        }
        
        public Intent getPreparedResult() {
        	return mPreparedResult;
        }
        
        public void setPreparedResult(Intent intent) {
        	mPreparedResult = intent;
        }
    }
}
