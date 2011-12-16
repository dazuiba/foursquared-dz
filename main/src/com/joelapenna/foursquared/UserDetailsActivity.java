/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.MenuUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.StringFormatters;
import com.joelapenna.foursquared.util.UiUtil;
import com.joelapenna.foursquared.util.UserUtils;
import com.joelapenna.foursquared.widget.PhotoStrip;
import com.joelapenna.foursquared.widget.UserContactAdapter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * @date March 8, 2010.
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class UserDetailsActivity extends Activity {
    private static final String TAG = "UserDetailsActivity";
    private static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    private static final int ACTIVITY_REQUEST_CODE_PINGS              = 815;
    private static final int ACTIVITY_REQUEST_CODE_FETCH_IMAGE        = 816;
    private static final int ACTIVITY_REQUEST_CODE_VIEW_AND_SET_IMAGE = 817;

    public static final String EXTRA_USER_PARCEL = Foursquared.PACKAGE_NAME
        + ".UserDetailsActivity.EXTRA_USER_PARCEL";
    public static final String EXTRA_USER_ID = Foursquared.PACKAGE_NAME
        + ".UserDetailsActivity.EXTRA_USER_ID";
    
    public static final String EXTRA_SHOW_ADD_FRIEND_OPTIONS = Foursquared.PACKAGE_NAME
        + ".UserDetailsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS";
    

    private static final int LOAD_TYPE_USER_NONE    = 0;
    private static final int LOAD_TYPE_USER_ID      = 1;
    private static final int LOAD_TYPE_USER_PARTIAL = 2;
    private static final int LOAD_TYPE_USER_FULL    = 3;
    
    private static final int MENU_REFRESH   = 0;
    private static final int MENU_CONTACT   = 1;
    private static final int MENU_PINGS     = 2;
    
    private static final int DIALOG_CONTACTS = 0;
    
    private StateHolder mStateHolder;
    private RemoteResourceManager mRrm;
    private RemoteResourceManagerObserver mResourcesObserver;
    private Handler mHandler;

    

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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.user_details_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        Object retained = getLastNonConfigurationInstance();
        if (retained != null) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivityForTasks(this);
        } else {

            mStateHolder = new StateHolder();
            if (getIntent().hasExtra(EXTRA_USER_PARCEL)) {
                Log.i(TAG, "Starting " + TAG + " with full user parcel.");
                User user = getIntent().getExtras().getParcelable(EXTRA_USER_PARCEL);
                mStateHolder.setUser(user);
                mStateHolder.setLoadType(LOAD_TYPE_USER_PARTIAL);
            } else if (getIntent().hasExtra(EXTRA_USER_ID)) {
                Log.i(TAG, "Starting " + TAG + " with user ID.");
                User user = new User();
                user.setId(getIntent().getExtras().getString(EXTRA_USER_ID));
                mStateHolder.setUser(user);
                mStateHolder.setLoadType(LOAD_TYPE_USER_ID);
            } else {
                Log.i(TAG, "Starting " + TAG + " as logged-in user.");
                User user = new User();
                user.setId(null);
                mStateHolder.setUser(user);
                mStateHolder.setLoadType(LOAD_TYPE_USER_ID);
            }
                
            mStateHolder.setIsLoggedInUser(
              mStateHolder.getUser().getId() == null ||
              mStateHolder.getUser().getId().equals(
                  ((Foursquared) getApplication()).getUserId()));
        }
        
        mHandler = new Handler();
        mRrm = ((Foursquared) getApplication()).getRemoteResourceManager();
        mResourcesObserver = new RemoteResourceManagerObserver();
        mRrm.addObserver(mResourcesObserver);

        ensureUi();

        if (mStateHolder.getLoadType() != LOAD_TYPE_USER_FULL && 
           !mStateHolder.getIsRunningUserDetailsTask() &&
           !mStateHolder.getRanOnce()) {
            mStateHolder.startTaskUserDetails(this, mStateHolder.getUser().getId());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        
        if (isFinishing()) {
            mStateHolder.cancelTasks();
            mHandler.removeCallbacks(mRunnableUpdateUserPhoto);

            RemoteResourceManager rrm = ((Foursquared) getApplication()).getRemoteResourceManager();
            rrm.deleteObserver(mResourcesObserver);
        }
    }
    
    @Override 
    protected void onDestroy() {
        super.onDestroy();
        
        unregisterReceiver(mLoggedOutReceiver);
    }

    private void ensureUi() {
        
        int sdk = UiUtil.sdkVersion();
        
        View viewProgressBar = findViewById(R.id.venueActivityDetailsProgress);
        TextView tvUsername = (TextView)findViewById(R.id.userDetailsActivityUsername);
        TextView tvLastSeen = (TextView)findViewById(R.id.userDetailsActivityHometownOrLastSeen);
        Button btnFriend = (Button)findViewById(R.id.userDetailsActivityFriendButton);
        View viewMayorships = findViewById(R.id.userDetailsActivityGeneralMayorships);
        View viewBadges = findViewById(R.id.userDetailsActivityGeneralBadges);
        View viewTips = findViewById(R.id.userDetailsActivityGeneralTips);
        TextView tvMayorships = (TextView)findViewById(R.id.userDetailsActivityGeneralMayorshipsValue);
        TextView tvBadges = (TextView)findViewById(R.id.userDetailsActivityGeneralBadgesValue);
        TextView tvTips = (TextView)findViewById(R.id.userDetailsActivityGeneralTipsValue);
        ImageView ivMayorshipsChevron = (ImageView)findViewById(R.id.userDetailsActivityGeneralMayorshipsChevron);
        ImageView ivBadgesChevron = (ImageView)findViewById(R.id.userDetailsActivityGeneralBadgesChevron);
        ImageView ivTipsChevron = (ImageView)findViewById(R.id.userDetailsActivityGeneralTipsChevron);
        View viewCheckins = findViewById(R.id.userDetailsActivityCheckins);
        View viewFriendsFollowers = findViewById(R.id.userDetailsActivityFriendsFollowers);
        View viewAddFriends = findViewById(R.id.userDetailsActivityAddFriends);
        View viewTodos = findViewById(R.id.userDetailsActivityTodos);
        View viewFriends = findViewById(R.id.userDetailsActivityFriends);
        TextView tvCheckins = (TextView)findViewById(R.id.userDetailsActivityCheckinsText);
        ImageView ivCheckinsChevron = (ImageView)findViewById(R.id.userDetailsActivityCheckinsChevron);
        TextView tvFriendsFollowers = (TextView)findViewById(R.id.userDetailsActivityFriendsFollowersText);
        ImageView ivFriendsFollowersChevron = (ImageView)findViewById(R.id.userDetailsActivityFriendsFollowersChevron);
        TextView tvTodos = (TextView)findViewById(R.id.userDetailsActivityTodosText);
        ImageView ivTodos = (ImageView)findViewById(R.id.userDetailsActivityTodosChevron);
        TextView tvFriends = (TextView)findViewById(R.id.userDetailsActivityFriendsText);
        ImageView ivFriends = (ImageView)findViewById(R.id.userDetailsActivityFriendsChevron);
        PhotoStrip psFriends = (PhotoStrip)findViewById(R.id.userDetailsActivityFriendsPhotos);
        
        viewProgressBar.setVisibility(View.VISIBLE);
        tvUsername.setText("");
        tvLastSeen.setText("");
        viewMayorships.setFocusable(false);
        viewBadges.setFocusable(false);
        viewTips.setFocusable(false);
        tvMayorships.setText("0");
        tvBadges.setText("0");
        tvTips.setText("0");
        ivMayorshipsChevron.setVisibility(View.INVISIBLE);
        ivBadgesChevron.setVisibility(View.INVISIBLE);
        ivTipsChevron.setVisibility(View.INVISIBLE);
        btnFriend.setVisibility(View.INVISIBLE);

        viewCheckins.setFocusable(false);
        viewFriendsFollowers.setFocusable(false);
        viewAddFriends.setFocusable(false);
        viewTodos.setFocusable(false);
        viewFriends.setFocusable(false);
        viewCheckins.setVisibility(View.GONE);
        viewFriendsFollowers.setVisibility(View.GONE);
        viewAddFriends.setVisibility(View.GONE);
        viewTodos.setVisibility(View.GONE);
        viewFriends.setVisibility(View.GONE);
        ivCheckinsChevron.setVisibility(View.INVISIBLE);
        ivFriendsFollowersChevron.setVisibility(View.INVISIBLE);
        ivTodos.setVisibility(View.INVISIBLE);
        ivFriends.setVisibility(View.INVISIBLE);
        psFriends.setVisibility(View.GONE);
        tvCheckins.setText("");
        tvFriendsFollowers.setText("");
        tvTodos.setText("");
        tvFriends.setText("");
        
        if (mStateHolder.getLoadType() >= LOAD_TYPE_USER_PARTIAL) {
            User user = mStateHolder.getUser();
            
            ensureUiPhoto(user);
        
            if (mStateHolder.getIsLoggedInUser() || UserUtils.isFriend(user)) {
                tvUsername.setText(StringFormatters.getUserFullName(user));
            } else {
                tvUsername.setText(StringFormatters.getUserAbbreviatedName(user));
            }
            
            tvLastSeen.setText(user.getHometown());
            
            if (mStateHolder.getIsLoggedInUser() || 
               UserUtils.isFriend(user) || 
               UserUtils.isFriendStatusPendingThem(user) ||
               UserUtils.isFriendStatusFollowingThem(user)) {
                btnFriend.setVisibility(View.INVISIBLE);
            } else if (UserUtils.isFriendStatusPendingYou(user)) {
                btnFriend.setVisibility(View.VISIBLE);
                btnFriend.setText(getString(R.string.user_details_activity_friend_confirm));
                btnFriend.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mStateHolder.startTaskFriend(UserDetailsActivity.this, StateHolder.TASK_FRIEND_ACCEPT);
                    }
                });
            } else {
                btnFriend.setVisibility(View.VISIBLE);
                btnFriend.setText(getString(R.string.user_details_activity_friend_add));
                btnFriend.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        view.setEnabled(false);
                        mStateHolder.startTaskFriend(UserDetailsActivity.this, StateHolder.TASK_FRIEND_ADD);
                    }                   
                });
            }
        
            if (mStateHolder.getLoadType() >= LOAD_TYPE_USER_FULL) {
                viewProgressBar.setVisibility(View.GONE);
                tvMayorships.setText(String.valueOf(user.getMayorCount()));
                tvBadges.setText(String.valueOf(user.getBadgeCount()));
                tvTips.setText(String.valueOf(user.getTipCount()));
                
                if (user.getCheckin() != null && user.getCheckin().getVenue() != null) {
                    String fixed = getResources().getString(R.string.user_details_activity_last_seen);
                    String full = fixed + " " + user.getCheckin().getVenue().getName();
                    CharacterStyle bold = new StyleSpan(Typeface.BOLD);
                    SpannableString ss = new SpannableString(full);
                    ss.setSpan(bold, fixed.length(), full.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    tvLastSeen.setText(ss);
                    
                    tvLastSeen.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            startVenueActivity();
                        }
                    });
                }
                
                if (user.getMayorships() != null && user.getMayorships().size() > 0) {
                    viewMayorships.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startMayorshipsActivity();
                        }
                    });
                    viewMayorships.setFocusable(true);
                    if (sdk > 3) {
                        ivMayorshipsChevron.setVisibility(View.VISIBLE);
                    }
                }
                
                if (user.getBadges() != null && user.getBadges().size() > 0) {
                    viewBadges.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startBadgesActivity();
                        }
                    });
                    viewBadges.setFocusable(true);
                    if (sdk > 3) {
                        ivBadgesChevron.setVisibility(View.VISIBLE);
                    }
                }
                
                if (user.getTipCount() > 0) {
                    viewTips.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startTipsActivity();
                        }
                    });
                    viewTips.setFocusable(true);
                    if (sdk > 3) {
                        ivTipsChevron.setVisibility(View.VISIBLE);
                    }
                }
                
                // The rest of the items depend on if we're viewing ourselves or not.
                if (mStateHolder.getIsLoggedInUser()) {
                    viewCheckins.setVisibility(View.VISIBLE);
                    viewFriendsFollowers.setVisibility(View.VISIBLE);
                    viewAddFriends.setVisibility(View.VISIBLE);
                    
                    tvCheckins.setText(
                        user.getCheckinCount() == 1 ? 
                            getResources().getString(
                                R.string.user_details_activity_checkins_text_single, user.getCheckinCount()) :
                            getResources().getString(
                                R.string.user_details_activity_checkins_text_plural, user.getCheckinCount()));
                    if (user.getCheckinCount() > 0) {
                        viewCheckins.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startCheckinsActivity();
                            }
                        });
                        viewCheckins.setFocusable(true);
                        ivCheckinsChevron.setVisibility(View.VISIBLE);
                    }
                    
                    if (user.getFollowerCount() > 0) {
                        tvFriendsFollowers.setText(
                            user.getFollowerCount() == 1 ? 
                                getResources().getString(
                                    R.string.user_details_activity_friends_followers_text_celeb_single, 
                                    user.getFollowerCount()) :
                                getResources().getString(
                                    R.string.user_details_activity_friends_followers_text_celeb_plural,
                                    user.getFollowerCount()));
                        
                        if (user.getFriendCount() > 0) {
                            tvFriendsFollowers.setText(tvFriendsFollowers.getText() + ", ");
                        }
                    }
                    
                    tvFriendsFollowers.setText(tvFriendsFollowers.getText().toString() + 
                        (user.getFriendCount() == 1 ?    
                             getResources().getString(
                                 R.string.user_details_activity_friends_followers_text_single,
                                 user.getFriendCount()) :
                             getResources().getString(
                                 R.string.user_details_activity_friends_followers_text_plural,
                                 user.getFriendCount())));
                                    
                    if (user.getFollowerCount() + user.getFriendCount() > 0) {
                        viewFriendsFollowers.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startFriendsFollowersActivity();
                            }
                        });
                        viewFriendsFollowers.setFocusable(true);
                        ivFriendsFollowersChevron.setVisibility(View.VISIBLE);
                    }
                    
                    viewAddFriends.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startAddFriendsActivity();
                        }
                    });
                    viewAddFriends.setFocusable(true);
                    
                } else {
                    viewTodos.setVisibility(View.VISIBLE);
                    viewFriends.setVisibility(View.VISIBLE); 

                    tvTodos.setText(
                        user.getTodoCount() == 1 ? 
                            getResources().getString(
                                R.string.user_details_activity_todos_text_single, user.getTodoCount()) :
                            getResources().getString(
                                R.string.user_details_activity_todos_text_plural, user.getTodoCount()));

                    if (user.getTodoCount() > 0 && UserUtils.isFriend(user)) {
                        viewTodos.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startTodosActivity();
                            }
                        });
                        viewTodos.setFocusable(true);
                        ivTodos.setVisibility(View.VISIBLE);
                    }
                                     
                    tvFriends.setText(
                        user.getFriendCount() == 1 ? 
                            getResources().getString(
                                R.string.user_details_activity_friends_text_single, 
                                user.getFriendCount()) :
                            getResources().getString(
                                R.string.user_details_activity_friends_text_plural,
                                user.getFriendCount()));
                    
                    int friendsInCommon = user.getFriendsInCommon() == null ? 0 :
                        user.getFriendsInCommon().size();
                    if (friendsInCommon > 0) {
                        tvFriends.setText(tvFriends.getText().toString() + 
                            (friendsInCommon == 1 ?    
                                 getResources().getString(
                                     R.string.user_details_activity_friends_in_common_text_single,
                                     friendsInCommon) :
                                 getResources().getString(
                                     R.string.user_details_activity_friends_in_common_text_plural,
                                     friendsInCommon)));
                    }
                    
                    if (user.getFriendCount() > 0) {
                        viewFriends.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startFriendsInCommonActivity();
                            }
                        });
                        viewFriends.setFocusable(true);
                        ivFriends.setVisibility(View.VISIBLE);
                    }
                        
                    if (friendsInCommon > 0) {
                        psFriends.setVisibility(View.VISIBLE);
                        psFriends.setUsersAndRemoteResourcesManager(user.getFriendsInCommon(), mRrm);
                    } else {
                        tvFriends.setPadding(tvFriends.getPaddingLeft(), tvTodos.getPaddingTop(),
                            tvFriends.getPaddingRight(), tvTodos.getPaddingBottom());
                    }
                }
            } else {
                // Haven't done a full load.
                if (mStateHolder.getRanOnce()) {
                    viewProgressBar.setVisibility(View.GONE);
                }
            }
        } else {
            // Haven't done a full load.
            if (mStateHolder.getRanOnce()) {
                viewProgressBar.setVisibility(View.GONE);
            }
        }
        
        // Regardless of load state, if running a task, show titlebar progress bar.
        if (mStateHolder.getIsTaskRunning()) {
            setProgressBarIndeterminateVisibility(true);
        } else {
            setProgressBarIndeterminateVisibility(false);
        }
        
        // Disable friend button if running friend task.
        if (mStateHolder.getIsRunningFriendTask()) {
            btnFriend.setEnabled(false);
        } else {
            btnFriend.setEnabled(true);
        }
    }
    
    private void ensureUiPhoto(User user) {
        ImageView ivPhoto = (ImageView)findViewById(R.id.userDetailsActivityPhoto);
        
        if (user == null || user.getPhoto() == null) {
            ivPhoto.setImageResource(R.drawable.blank_boy);
            return;
        }
        
        Uri uriPhoto = Uri.parse(user.getPhoto());
        if (mRrm.exists(uriPhoto)) {
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(Uri.parse(user
                        .getPhoto())));
                ivPhoto.setImageBitmap(bitmap);
            } catch (IOException e) {
                setUserPhotoMissing(ivPhoto, user);
            }
        } else {
            mRrm.request(uriPhoto);
            setUserPhotoMissing(ivPhoto, user);
        }
        
        ivPhoto.postInvalidate();
        ivPhoto.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStateHolder.getLoadType() == LOAD_TYPE_USER_FULL) {
                    User user = mStateHolder.getUser();
                    
                    // If "_thumbs" exists, remove it to get the url of the
                    // full-size image.
                    String photoUrl = user.getPhoto().replace("_thumbs", "");
                    
                    // If we're viewing our own page, clicking the thumbnail should send the user
                    // to our built-in image viewer. Here we can give them the option of setting
                    // a new photo for themselves.
                    Intent intent = new Intent(UserDetailsActivity.this, FetchImageForViewIntent.class);
                    intent.putExtra(FetchImageForViewIntent.IMAGE_URL, photoUrl);
                    intent.putExtra(FetchImageForViewIntent.PROGRESS_BAR_MESSAGE, getResources()
                            .getString(R.string.user_activity_fetch_full_image_message));
                    
                    if (mStateHolder.getIsLoggedInUser()) {
                        intent.putExtra(FetchImageForViewIntent.LAUNCH_VIEW_INTENT_ON_COMPLETION, false);
                        startActivityForResult(intent, ACTIVITY_REQUEST_CODE_FETCH_IMAGE);
                    } else {
                        startActivity(intent);
                    }
                }
            }
        });
    }
    
    private void setUserPhotoMissing(ImageView ivPhoto, User user) {
        if (Foursquare.MALE.equals(user.getGender())) {
            ivPhoto.setImageResource(R.drawable.blank_boy);
        } else {
            ivPhoto.setImageResource(R.drawable.blank_girl);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTasks(null);
        return mStateHolder;
    }

    private void startBadgesActivity() {
        if (mStateHolder.getUser() != null) {
            Intent intent = new Intent(UserDetailsActivity.this, BadgesActivity.class);
            intent.putParcelableArrayListExtra(BadgesActivity.EXTRA_BADGE_ARRAY_LIST_PARCEL,
                    mStateHolder.getUser().getBadges());
            intent.putExtra(BadgesActivity.EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
            startActivity(intent);
        }
    }
    
    private void startMayorshipsActivity() {
        if (mStateHolder.getUser() != null) {
            Intent intent = new Intent(UserDetailsActivity.this, UserMayorshipsActivity.class);
            intent.putExtra(UserMayorshipsActivity.EXTRA_USER_ID, mStateHolder.getUser().getId());
            intent.putExtra(UserMayorshipsActivity.EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
            startActivity(intent); 
        }
    }
    
    private void startCheckinsActivity() {
        Intent intent = new Intent(UserDetailsActivity.this, UserHistoryActivity.class);
        intent.putExtra(UserHistoryActivity.EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
        startActivity(intent); 
    }
    
    private void startFriendsFollowersActivity() {
        User user = mStateHolder.getUser();

        Intent intent = null;
        if (user.getFollowerCount() > 0) {
            intent = new Intent(UserDetailsActivity.this, UserDetailsFriendsFollowersActivity.class);
            intent.putExtra(UserDetailsFriendsFollowersActivity.EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
        } else {
            intent = new Intent(UserDetailsActivity.this, UserDetailsFriendsActivity.class);
            intent.putExtra(UserDetailsFriendsActivity.EXTRA_USER_ID, mStateHolder.getUser().getId());
            intent.putExtra(UserDetailsFriendsActivity.EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
        }
        startActivity(intent); 
    }
    
    private void startAddFriendsActivity() {
        Intent intent = new Intent(UserDetailsActivity.this, AddFriendsActivity.class);
        startActivity(intent); 
    }
    
    private void startFriendsInCommonActivity() {
        User user = mStateHolder.getUser();
        
        Intent intent = null;
        if (user.getFriendsInCommon() != null && user.getFriendsInCommon().size() > 0) {
            intent = new Intent(UserDetailsActivity.this, UserDetailsFriendsInCommonActivity.class);
            intent.putExtra(UserDetailsFriendsInCommonActivity.EXTRA_USER_PARCEL, mStateHolder.getUser());
        } else {
            intent = new Intent(UserDetailsActivity.this, UserDetailsFriendsActivity.class);
            intent.putExtra(UserDetailsFriendsActivity.EXTRA_USER_ID, mStateHolder.getUser().getId());
            intent.putExtra(UserDetailsFriendsActivity.EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
        }
        startActivity(intent); 
    }
    
    private void startTodosActivity() {
        Intent intent = new Intent(UserDetailsActivity.this, TodosActivity.class);
        intent.putExtra(TodosActivity.INTENT_EXTRA_USER_ID, mStateHolder.getUser().getId());
        intent.putExtra(TodosActivity.INTENT_EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
        startActivity(intent); 
    }
    
    private void startTipsActivity() {
        Intent intent = new Intent(UserDetailsActivity.this, UserDetailsTipsActivity.class);
        intent.putExtra(UserDetailsTipsActivity.INTENT_EXTRA_USER_ID, mStateHolder.getUser().getId());
        intent.putExtra(UserDetailsTipsActivity.INTENT_EXTRA_USER_NAME, mStateHolder.getUser().getFirstname());
        startActivity(intent); 
    }
    
    private void startVenueActivity() {
        User user = mStateHolder.getUser();
        if (user.getCheckin() != null && 
            user.getCheckin().getVenue() != null) {
            Intent intent = new Intent(this, VenueActivity.class);
            intent.putExtra(VenueActivity.INTENT_EXTRA_VENUE_PARTIAL, user.getCheckin().getVenue());
            startActivity(intent);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.refresh)
            .setIcon(R.drawable.ic_menu_refresh);
            
        if (mStateHolder.getIsLoggedInUser()) {
            MenuUtils.addPreferencesToMenu(this, menu);
        } else {
            menu.add(Menu.NONE, MENU_CONTACT, Menu.NONE, R.string.user_details_activity_friends_menu_contact)
                .setIcon(R.drawable.ic_menu_user_contact);
            
            if (UserUtils.isFriend(mStateHolder.getUser())) {
                menu.add(Menu.NONE, MENU_PINGS, Menu.NONE, R.string.user_details_activity_friends_menu_pings)
                    .setIcon(android.R.drawable.ic_menu_rotate);
            }
        }
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        User user = mStateHolder.getUser();
        
        MenuItem refresh = menu.findItem(MENU_REFRESH);
        MenuItem contact = menu.findItem(MENU_CONTACT);
        MenuItem pings   = menu.findItem(MENU_PINGS);
        if (!mStateHolder.getIsRunningUserDetailsTask()) {
            refresh.setEnabled(true);
            if (contact != null) {
                boolean contactEnabled = 
                    !TextUtils.isEmpty(user.getFacebook()) ||
                    !TextUtils.isEmpty(user.getTwitter()) ||
                    !TextUtils.isEmpty(user.getEmail()) || 
                    !TextUtils.isEmpty(user.getPhone());
                contact.setEnabled(contactEnabled);
            }
            if (pings != null) {
                pings.setEnabled(true);
            }
        } else {
            refresh.setEnabled(false);
            if (contact != null) {
                contact.setEnabled(false);
            }
            if (pings != null) {
                pings.setEnabled(false);
            }
        }
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                mStateHolder.startTaskUserDetails(this, mStateHolder.getUser().getId());
                return true;
            case MENU_CONTACT:
                showDialog(DIALOG_CONTACTS);
                return true;
            case MENU_PINGS:
                Intent intentPings = new Intent(this, UserDetailsPingsActivity.class);
                intentPings.putExtra(UserDetailsPingsActivity.EXTRA_USER_PARCEL, mStateHolder.getUser());
                startActivityForResult(intentPings, ACTIVITY_REQUEST_CODE_PINGS);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)  {
        switch (requestCode) {
            case ACTIVITY_REQUEST_CODE_PINGS:
                if (resultCode == Activity.RESULT_OK) {
                    User user = (User)data.getParcelableExtra(UserDetailsPingsActivity.EXTRA_USER_RETURNED);
                    if (user != null) {
                        mStateHolder.getUser().getSettings().setGetPings(user.getSettings().getGetPings());
                    }
                }
                break;
            case ACTIVITY_REQUEST_CODE_FETCH_IMAGE:
                if (resultCode == Activity.RESULT_OK) {
                    String imagePath = data.getStringExtra(FetchImageForViewIntent.EXTRA_SAVED_IMAGE_PATH_RETURNED);
                    if (mStateHolder.getIsLoggedInUser() && !TextUtils.isEmpty(imagePath)) {
                        Intent intent = new Intent(this, FullSizeImageActivity.class);
                        intent.putExtra(FullSizeImageActivity.INTENT_EXTRA_IMAGE_PATH, imagePath);
                        intent.putExtra(FullSizeImageActivity.INTENT_EXTRA_ALLOW_SET_NEW_PHOTO, true);
                        startActivityForResult(intent, ACTIVITY_REQUEST_CODE_VIEW_AND_SET_IMAGE);
                    }
                }
                break;
            case ACTIVITY_REQUEST_CODE_VIEW_AND_SET_IMAGE:
                if (resultCode == Activity.RESULT_OK) {
                    String imageUrl = data.getStringExtra(FullSizeImageActivity.INTENT_RETURN_NEW_PHOTO_URL);
                    if (!TextUtils.isEmpty(imageUrl)) {
                        mStateHolder.getUser().setPhoto(imageUrl);
                        ensureUiPhoto(mStateHolder.getUser());
                    }
                }
                break;
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CONTACTS:
                final UserContactAdapter adapter = new UserContactAdapter(this, mStateHolder.getUser());
                AlertDialog dlgInfo = new AlertDialog.Builder(this)
                  .setTitle(getResources().getString(R.string.user_details_activity_friends_menu_contact))
                  .setAdapter(adapter, new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dlg, int pos) {
                          UserContactAdapter.Action action = (UserContactAdapter.Action)adapter.getItem(pos);
                          switch (action.getActionId()) {
                              case UserContactAdapter.Action.ACTION_ID_SMS:
                                  UiUtil.startSmsIntent(UserDetailsActivity.this, mStateHolder.getUser().getPhone());
                                  break;
                              case UserContactAdapter.Action.ACTION_ID_EMAIL:
                                  UiUtil.startEmailIntent(UserDetailsActivity.this, mStateHolder.getUser().getEmail());
                                  break;
                              case UserContactAdapter.Action.ACTION_ID_PHONE:
                                  UiUtil.startDialer(UserDetailsActivity.this, mStateHolder.getUser().getPhone());
                                  break;
                              case UserContactAdapter.Action.ACTION_ID_TWITTER:
                                  UiUtil.startWebIntent(UserDetailsActivity.this, "http://www.twitter.com/" +
                                      mStateHolder.getUser().getTwitter());
                                  break;
                              case UserContactAdapter.Action.ACTION_ID_FACEBOOK:
                                  UiUtil.startWebIntent(UserDetailsActivity.this, "http://www.facebook.com/profile.php?id=" +
                                      mStateHolder.getUser().getFacebook());
                                  break;
                          }
                      }
                  })
                  .create();
                  return dlgInfo;
        }
        
        return null;
    }

    private void onUserDetailsTaskComplete(User user, Exception ex) {
        mStateHolder.setIsRunningUserDetailsTask(false);
        mStateHolder.setRanOnce(true);
        if (user != null) {
            mStateHolder.setUser(user);
            mStateHolder.setLoadType(LOAD_TYPE_USER_FULL);
        } else if (ex != null) {
            NotificationsUtil.ToastReasonForFailure(this, ex);
        } else {
            Toast.makeText(this, "A surprising new error has occurred!", Toast.LENGTH_SHORT).show();
        }
        
        ensureUi();
    }
    
    
    /**
     * Even if the caller supplies us with a User object parcelable, it won't
     * have all the badge etc extra info in it. As soon as the activity starts,
     * we launch this task to fetch a full user object, and merge it with
     * whatever is already supplied in mUser.
     */
    private static class UserDetailsTask extends AsyncTask<String, Void, User> {

        private UserDetailsActivity mActivity;
        private Exception mReason;

        public UserDetailsTask(UserDetailsActivity activity) {
            mActivity = activity;
        }

        @Override
        protected void onPreExecute() {
            mActivity.ensureUi();
        }

        @Override
        protected User doInBackground(String... params) {
            try {
                return ((Foursquared) mActivity.getApplication()).getFoursquare().user(
                        params[0],
                        true,
                        true,
                        true,
                        LocationUtils.createFoursquareLocation(((Foursquared) mActivity
                                .getApplication()).getLastKnownLocation()));
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(User user) {
            if (mActivity != null) {
                mActivity.onUserDetailsTaskComplete(user, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onUserDetailsTaskComplete(null, mReason);
            }
        }

        public void setActivity(UserDetailsActivity activity) {
            mActivity = activity;
        }
    }
    
    private void onFriendTaskComplete(User user, int action, Exception ex) {
        mStateHolder.setIsRunningFriendTask(false);
        
        // The api isn't returning an updated friend status flag here, so we'll
        // overwrite it manually for now, assuming success if the user object
        // was not null.
        User userCurrent = mStateHolder.getUser();
        if (user != null) {
            switch (action) {
                case StateHolder.TASK_FRIEND_ACCEPT:
                    userCurrent.setFirstname(user.getFirstname());
                    userCurrent.setLastname(user.getLastname());
                    userCurrent.setFriendstatus("friend");
                    break;
                case StateHolder.TASK_FRIEND_ADD:
                    userCurrent.setFriendstatus("pendingthem");
                    break;
            }
        } else {
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        ensureUi();
    }
    
    private static class FriendTask extends AsyncTask<Void, Void, User> {

        private UserDetailsActivity mActivity;
        private String mUserId;
        private int mAction;
        private Exception mReason;

        public FriendTask(UserDetailsActivity activity, String userId, int action) {
            mActivity = activity;
            mUserId = userId;
            mAction = action;
        }

        @Override
        protected void onPreExecute() {
            mActivity.ensureUi();
        }

        @Override
        protected User doInBackground(Void... params) {
            Foursquare foursquare = ((Foursquared) mActivity.getApplication()).getFoursquare();
            try {
                switch (mAction) {
                    case StateHolder.TASK_FRIEND_ACCEPT:
                        return foursquare.friendApprove(mUserId);
                    case StateHolder.TASK_FRIEND_ADD:
                        return foursquare.friendSendrequest(mUserId);
                    default:
                        throw new FoursquareException("Unknown action type supplied.");
                }
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(User user) {
            if (mActivity != null) {
                mActivity.onFriendTaskComplete(user, mAction, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onFriendTaskComplete(null, mAction, mReason);
            }
        }

        public void setActivity(UserDetailsActivity activity) {
            mActivity = activity;
        }
    }
    

    private static class StateHolder {
        public static final int TASK_FRIEND_ACCEPT = 0;
        public static final int TASK_FRIEND_ADD    = 1;
        
        private User mUser;
        private boolean mIsLoggedInUser;
        private UserDetailsTask mTaskUserDetails;
        private boolean mIsRunningUserDetailsTask;
        private boolean mRanOnce;
        private int mLoadType;
        
        private FriendTask mTaskFriend;
        private boolean mIsRunningFriendTask;
        
        
        public StateHolder() {
            mIsRunningUserDetailsTask = false;
            mIsRunningFriendTask = false;
            mIsLoggedInUser = false;
            mRanOnce = false;
            mLoadType = LOAD_TYPE_USER_NONE;
        }
        
        public boolean getIsLoggedInUser() {
            return mIsLoggedInUser;
        }
        
        public void setIsLoggedInUser(boolean isLoggedInUser) {
            mIsLoggedInUser = isLoggedInUser;
        }

        public User getUser() {
            return mUser;
        }

        public void setUser(User user) {
            mUser = user;
        }
        
        public int getLoadType() {
            return mLoadType;
        }
        
        public void setLoadType(int loadType) {
            mLoadType = loadType;
        }

        public void startTaskUserDetails(UserDetailsActivity activity, String userId) {
            if (!mIsRunningUserDetailsTask) {
                mIsRunningUserDetailsTask = true;
                mTaskUserDetails = new UserDetailsTask(activity);
                mTaskUserDetails.execute(userId);
            }
        }
        
        public void startTaskFriend(UserDetailsActivity activity, int action) {
            if (!mIsRunningFriendTask) {
                mIsRunningFriendTask = true;
                mTaskFriend = new FriendTask(activity, mUser.getId(), action);
                mTaskFriend.execute();
            }
        }
        
        public void setActivityForTasks(UserDetailsActivity activity) {
            if (mTaskUserDetails != null) {
                mTaskUserDetails.setActivity(activity);
            }
            if (mTaskFriend != null) {
                mTaskFriend.setActivity(activity);
            }
        }
        
        public boolean getIsRunningUserDetailsTask() {
            return mIsRunningUserDetailsTask;
        }

        public void setIsRunningUserDetailsTask(boolean isRunning) {
            mIsRunningUserDetailsTask = isRunning;
        }
        
        public boolean getRanOnce() {
            return mRanOnce;
        }
        
        public void setRanOnce(boolean ranOnce) {
            mRanOnce = ranOnce;
        }
        
        public boolean getIsRunningFriendTask() {
            return mIsRunningFriendTask;
        }
        
        public void setIsRunningFriendTask(boolean isRunning) {
            mIsRunningFriendTask = isRunning;
        }
        
        public void cancelTasks() {
            if (mTaskUserDetails != null) {
                mTaskUserDetails.setActivity(null);
                mTaskUserDetails.cancel(true);
            }
            if (mTaskFriend != null) {
                mTaskFriend.setActivity(null);
                mTaskFriend.cancel(true);
            }
        }
        
        public boolean getIsTaskRunning() {
            return mIsRunningUserDetailsTask || mIsRunningFriendTask;
        }
    }
    
    private class RemoteResourceManagerObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            mHandler.post(mRunnableUpdateUserPhoto);
        }
    }
    
    private Runnable mRunnableUpdateUserPhoto = new Runnable() {
        @Override 
        public void run() {
            ensureUiPhoto(mStateHolder.getUser());
        }
    };
}
