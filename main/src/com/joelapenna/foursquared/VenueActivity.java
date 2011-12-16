/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.types.Checkin;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Mayor;
import com.joelapenna.foursquare.types.Stats;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.preferences.Preferences;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.StringFormatters;
import com.joelapenna.foursquared.util.UserUtils;
import com.joelapenna.foursquared.util.VenueUtils;
import com.joelapenna.foursquared.widget.PhotoStrip;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * We may be given a pre-fetched venue ready to display, or we might also get just
 * a venue ID. If we only get a venue ID, then we need to fetch it immediately from
 * the API.
 * 
 * The activity will set an intent result in EXTRA_VENUE_RETURNED if the venue status
 * changes as a result of a user modifying todos at the venue. Parent activities can
 * check the returned venue to see if this status has changed to update their UI.
 * For example, the NearbyVenues activity wants to show the todo corner png if the
 * venue has a todo. 
 * 
 * The result will also be set if the venue is fully fetched if originally given only 
 * a venue id or partial venue object. This way the parent can also cache the full venue 
 * object for next time they want to display this venue activity.
 * 
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *         -Replaced shout activity with CheckinGatherInfoActivity (3/10/2010).
 *         -Redesign from tabbed layout (9/15/2010).
 */
public class VenueActivity extends Activity {
    private static final String TAG = "VenueActivity";

    private static final boolean DEBUG = FoursquaredSettings.DEBUG;

    private static final int MENU_TIP_ADD    = 1;
    private static final int MENU_TODO_ADD   = 2;
    private static final int MENU_EDIT_VENUE = 3;
    private static final int MENU_CALL       = 4;
    private static final int MENU_SHARE      = 5;

    private static final int RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE = 1;
    private static final int RESULT_CODE_ACTIVITY_ADD_TIP         = 2;
    private static final int RESULT_CODE_ACTIVITY_ADD_TODO        = 3;
    private static final int RESULT_CODE_ACTIVITY_TIP             = 4;
    private static final int RESULT_CODE_ACTIVITY_TIPS            = 5;
    private static final int RESULT_CODE_ACTIVITY_TODO            = 6;
    private static final int RESULT_CODE_ACTIVITY_TODOS           = 7;
    
    public static final String INTENT_EXTRA_VENUE_ID = Foursquared.PACKAGE_NAME
            + ".VenueActivity.INTENT_EXTRA_VENUE_ID";
    public static final String INTENT_EXTRA_VENUE_PARTIAL = Foursquared.PACKAGE_NAME
            + ".VenueActivity.INTENT_EXTRA_VENUE_PARTIAL";
    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueActivity.INTENT_EXTRA_VENUE";
    
    public static final String EXTRA_VENUE_RETURNED = Foursquared.PACKAGE_NAME
            + ".VenueActivity.EXTRA_VENUE_RETURNED";
    
    private StateHolder mStateHolder;
    private Handler mHandler;

    private RemoteResourceManager mRrm;
    private RemoteResourceManagerObserver mResourcesObserver;
    

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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.venue_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        mHandler = new Handler();
        
        StateHolder holder = (StateHolder) getLastNonConfigurationInstance();
        if (holder != null) {
        	mStateHolder = holder;
        	mStateHolder.setActivityForTasks(this);
            prepareResultIntent();
        } else {
        	mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
            	mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_FULL);
    	    	mStateHolder.setVenue((Venue)getIntent().getParcelableExtra(INTENT_EXTRA_VENUE));
            } else if (getIntent().hasExtra(INTENT_EXTRA_VENUE_PARTIAL)) {
            	mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_PARTIAL);
    	    	mStateHolder.setVenue((Venue)getIntent().getParcelableExtra(INTENT_EXTRA_VENUE_PARTIAL));
    	    	mStateHolder.startTaskVenue(this);
    	    } else if (getIntent().hasExtra(INTENT_EXTRA_VENUE_ID)) {
            	mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_ID);
    	    	mStateHolder.setVenueId(getIntent().getStringExtra(INTENT_EXTRA_VENUE_ID));
    	    	mStateHolder.startTaskVenue(this);
    	    } else {
    	    	Log.e(TAG, "VenueActivity must be given a venue id or a venue parcel as intent extras.");
    	    	finish();
    	    	return;
    	    }
        }

        mRrm = ((Foursquared) getApplication()).getRemoteResourceManager();
        mResourcesObserver = new RemoteResourceManagerObserver();
        mRrm.addObserver(mResourcesObserver);
        
        ensureUi();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
        
        mHandler.removeCallbacks(mRunnableMayorPhoto);
        if (mRrm != null) {
            mRrm.deleteObserver(mResourcesObserver);
        }
    }
    
    @Override 
    public void onResume() {
        super.onResume();
        ensureUiCheckinButton();
        // TODO: ensure mayor photo.
    }
    
    private void ensureUi() {

    	TextView tvVenueTitle = (TextView)findViewById(R.id.venueActivityName);
    	TextView tvVenueAddress = (TextView)findViewById(R.id.venueActivityAddress);
    	LinearLayout progress = (LinearLayout)findViewById(R.id.venueActivityDetailsProgress);

        View viewMayor = findViewById(R.id.venueActivityMayor);
    	TextView tvMayorTitle = (TextView)findViewById(R.id.venueActivityMayorName);
    	TextView tvMayorText = (TextView)findViewById(R.id.venueActivityMayorText);
    	ImageView ivMayorPhoto = (ImageView)findViewById(R.id.venueActivityMayorPhoto);
    	ImageView ivMayorChevron = (ImageView)findViewById(R.id.venueActivityMayorChevron);

        View viewCheckins = findViewById(R.id.venueActivityCheckins);
    	TextView tvPeopleText = (TextView)findViewById(R.id.venueActivityPeopleText);
    	PhotoStrip psPeoplePhotos = (PhotoStrip)findViewById(R.id.venueActivityPeoplePhotos);

        View viewTips = findViewById(R.id.venueActivityTips);
    	TextView tvTipsText = (TextView)findViewById(R.id.venueActivityTipsText);
    	TextView tvTipsTextExtra = (TextView)findViewById(R.id.venueActivityTipsExtra);
    	ImageView ivTipsChevron = (ImageView)findViewById(R.id.venueActivityTipsChevron);

        View viewMoreInfo = findViewById(R.id.venueActivityMoreInfo);
        TextView tvMoreInfoText = (TextView)findViewById(R.id.venueActivityMoreTitle);

    	Venue venue = mStateHolder.getVenue();
    	if (mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_FULL || 
    		mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_PARTIAL) {
	    	
	    	tvVenueTitle.setText(venue.getName());
	    	tvVenueAddress.setText(StringFormatters.getVenueLocationFull(venue));
	    	
	    	ensureUiCheckinButton();

	    	if (mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_FULL) {
	    	
		    	Stats stats = venue.getStats();
		    	Mayor mayor = stats != null ? stats.getMayor() : null;
		    	
		    	if (mayor != null) {
		    		tvMayorTitle.setText(StringFormatters.getUserFullName(mayor.getUser()));
		    		tvMayorText.setText(getResources().getString(R.string.venue_activity_mayor_text));
		    		
		    		String photoUrl = mayor.getUser().getPhoto();
		        	Uri uriPhoto = Uri.parse(photoUrl);
		            if (mRrm.exists(uriPhoto)) {
		                try {
		                    Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(Uri.parse(photoUrl)));
		                    ivMayorPhoto.setImageBitmap(bitmap);
		                } catch (IOException e) {
		                }
		            } else {
		            	ivMayorPhoto.setImageResource(UserUtils.getDrawableByGenderForUserThumbnail(mayor.getUser()));
		            	ivMayorPhoto.setTag(photoUrl);
		            	mRrm.request(uriPhoto);
		            }
		    		ivMayorChevron.setVisibility(View.VISIBLE);
	                setClickHandlerMayor(viewMayor);
		    	} else {
		    		tvMayorTitle.setText(getResources().getString(R.string.venue_activity_mayor_name_none));
		    		tvMayorText.setText(getResources().getString(R.string.venue_activity_mayor_text_none));
                    ivMayorChevron.setVisibility(View.INVISIBLE);
		    	}
                viewMayor.setVisibility(View.VISIBLE);
		    	
		    	if (venue.getCheckins() != null && venue.getCheckins().size() > 0) {
		    	    int friendCount = getFriendCountAtVenue();
		    	    int rest = venue.getCheckins().size() - friendCount;
		    	    
		    	    if (friendCount > 0 && rest == 0) {
		    	        // N friends are here
		    	        tvPeopleText.setText(getResources().getString(
		    	            friendCount == 1 ?
                                R.string.venue_activity_people_count_friend_single : 
                                R.string.venue_activity_people_count_friend_plural,
                                friendCount));
		    	    } else if (friendCount > 0 && rest > 0) {
		    	        // N friends are here with N other people
		    	        if (friendCount == 1 && rest == 1) {
		    	            tvPeopleText.setText(getString(R.string.venue_activity_people_count_friend_single_single,
	                            friendCount, rest));
		    	        } else if (friendCount == 1 && rest > 1) {
                            tvPeopleText.setText(getString(R.string.venue_activity_people_count_friend_single_plural,
                                friendCount, rest));
		    	        } else if (friendCount > 1 && rest == 1) {
                            tvPeopleText.setText(getString(R.string.venue_activity_people_count_friend_plural_single,
                                friendCount, rest));
                        } else {
                            tvPeopleText.setText(getString(R.string.venue_activity_people_count_friend_plural_plural,
                                friendCount, rest));
                        }
                    } else {
		    	        // N people are here
		    	        tvPeopleText.setText(getResources().getString(
		    	            venue.getCheckins().size() == 1 ?
                                R.string.venue_activity_people_count_single :
                                R.string.venue_activity_people_count_plural, 
                                venue.getCheckins().size()));
		    	    }
		    	    
		    		psPeoplePhotos.setCheckinsAndRemoteResourcesManager(venue.getCheckins(), mRrm);
                    viewCheckins.setVisibility(View.VISIBLE);
                    setClickHandlerCheckins(viewCheckins);
		    	} else {
                    viewCheckins.setVisibility(View.GONE);
		    	}
		    	
		    	if (venue.getTips() != null && venue.getTips().size() > 0) {
		    	    int tipCountFriends = getTipCountFriendsAtVenue();
		    	    if (tipCountFriends > 0) {
		    	        tvTipsText.setText(
		    	                getString(tipCountFriends == 1 ? 
		    	                        R.string.venue_activity_tip_count_friend_single :
		    	                        R.string.venue_activity_tip_count_friend_plural,
		    	                        tipCountFriends));
		    	        
		    	        int rest = venue.getTips().size() - tipCountFriends;
		    	        if (rest > 0) {
    		    	        tvTipsTextExtra.setText(
                                getString(R.string.venue_activity_tip_count_other_people, rest));
    		    	        tvTipsTextExtra.setVisibility(View.VISIBLE);
		    	        }
		    	    } else {
		    	        tvTipsText.setText(
                            getString(venue.getTips().size() == 1 ? 
                                R.string.venue_activity_tip_count_single :
                                R.string.venue_activity_tip_count_plural,
                                venue.getTips().size()));
	                    tvTipsTextExtra.setVisibility(View.GONE);
		    	    }

		    		ivTipsChevron.setVisibility(View.VISIBLE);
	                setClickHandlerTips(viewTips);
		    	} else {
                    tvTipsText.setText(getResources().getString(R.string.venue_activity_tip_count_none));
                    tvTipsTextExtra.setVisibility(View.GONE);
		    		ivTipsChevron.setVisibility(View.INVISIBLE);
		    	}
                viewTips.setVisibility(View.VISIBLE);
                
                if (tvTipsTextExtra.getVisibility() != View.VISIBLE) {
                    tvTipsText.setPadding(tvTipsText.getPaddingLeft(), tvMoreInfoText.getPaddingTop(), 
                            tvTipsText.getPaddingRight(), tvMoreInfoText.getPaddingBottom());
                }

                viewMoreInfo.setVisibility(View.VISIBLE);
                setClickHandlerMoreInfo(viewMoreInfo);
                
	    		progress.setVisibility(View.GONE);
	    	}
    	}

    	ensureUiTodosHere();
    	
    	ImageView ivSpecialHere = (ImageView)findViewById(R.id.venueActivitySpecialHere);
    	if (VenueUtils.getSpecialHere(venue)) {
    		ivSpecialHere.setVisibility(View.VISIBLE);
        	ivSpecialHere.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					showWebViewForSpecial();
				}
        	});
    	} else {
    		ivSpecialHere.setVisibility(View.GONE);
    	}
    }
    
    private void ensureUiCheckinButton() {
    	Button btnCheckin = (Button)findViewById(R.id.venueActivityButtonCheckin);
    	if (mStateHolder.getCheckedInHere()) {
    		btnCheckin.setEnabled(false);
    	} else {
    		if (mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_ID) {
        		btnCheckin.setEnabled(false);
    		} else {
        		btnCheckin.setEnabled(true);
        		btnCheckin.setOnClickListener(new OnClickListener() {
    				@Override
    				public void onClick(View v) {
    				    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(
    	                        VenueActivity.this);
    	                if (settings.getBoolean(Preferences.PREFERENCE_IMMEDIATE_CHECKIN, false)) {
    	                    startCheckinQuick();
    	                } else {
    	                    startCheckin();   
    	                }
    				}
        		});
    		}
    	}
    }
    
    private void ensureUiTipAdded() {
    	Venue venue = mStateHolder.getVenue();
    	TextView tvTipsText = (TextView)findViewById(R.id.venueActivityTipsText);
    	ImageView ivTipsChevron = (ImageView)findViewById(R.id.venueActivityTipsChevron);
    	
    	if (venue.getTips().size() == 1) {
			tvTipsText.setText(getResources().getString(
					R.string.venue_activity_tip_count_single, venue.getTips().size()));
		} else {
			tvTipsText.setText(getResources().getString(
					R.string.venue_activity_tip_count_plural, venue.getTips().size()));
		}
    	
		ivTipsChevron.setVisibility(View.VISIBLE);
    }
    
    private void ensureUiTodosHere() {
    	Venue venue = mStateHolder.getVenue();
    	RelativeLayout rlTodoHere = (RelativeLayout)findViewById(R.id.venueActivityTodoHere);
    	if (venue != null && venue.getHasTodo()) {
    		rlTodoHere.setVisibility(View.VISIBLE);
    		rlTodoHere.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					showTodoHereActivity();
				}
    		});
    	} else {
    		rlTodoHere.setVisibility(View.GONE);
    	}
    }
    
    private void setClickHandlerMayor(View view) {
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VenueActivity.this, UserDetailsActivity.class);
                intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, 
                        mStateHolder.getVenue().getStats().getMayor().getUser());
                intent.putExtra(UserDetailsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS, true);
                startActivity(intent);
            }
        });
    }
        
    private void setClickHandlerCheckins(View view) {
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VenueActivity.this, VenueCheckinsActivity.class);
                intent.putExtra(VenueCheckinsActivity.INTENT_EXTRA_VENUE, mStateHolder.getVenue());
                startActivity(intent);
            }
        });
    }
    
    private void setClickHandlerTips(View view) {
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = null;
                if (mStateHolder.getVenue().getTips().size() == 1) {
                    Venue venue = new Venue();
                    venue.setName(mStateHolder.getVenue().getName());
                    venue.setAddress(mStateHolder.getVenue().getAddress());
                    venue.setCrossstreet(mStateHolder.getVenue().getCrossstreet());
                    
                    Tip tip = mStateHolder.getVenue().getTips().get(0);
                    tip.setVenue(venue);
                    
                    intent = new Intent(VenueActivity.this, TipActivity.class);
                    intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, tip);
                    intent.putExtra(TipActivity.EXTRA_VENUE_CLICKABLE, false);
                    startActivityForResult(intent, RESULT_CODE_ACTIVITY_TIP);
                } else {
                    intent = new Intent(VenueActivity.this, VenueTipsActivity.class);
                    intent.putExtra(VenueTipsActivity.INTENT_EXTRA_VENUE, mStateHolder.getVenue());
                    startActivityForResult(intent, RESULT_CODE_ACTIVITY_TIPS);
                }
            }
        });
    }

    private void setClickHandlerMoreInfo(View view) {
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VenueActivity.this, VenueMapActivity.class);
                intent.putExtra(VenueMapActivity.INTENT_EXTRA_VENUE, mStateHolder.getVenue());
                startActivity(intent);
            }
        });
    }
    
    private void prepareResultIntent() {
    	Venue venue = mStateHolder.getVenue();
    	
    	Intent intent = new Intent();
        if (venue != null) {
        	intent.putExtra(EXTRA_VENUE_RETURNED, venue);
        }
        setResult(Activity.RESULT_OK, intent);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(Menu.NONE, MENU_TIP_ADD, 1, R.string.venue_activity_menu_add_tip).setIcon(
                R.drawable.ic_menu_venue_leave_tip);

        menu.add(Menu.NONE, MENU_TODO_ADD, 2, R.string.venue_activity_menu_add_todo).setIcon(
        		R.drawable.ic_menu_venue_add_todo);

        menu.add(Menu.NONE, MENU_EDIT_VENUE, 3, R.string.venue_activity_menu_flag).setIcon(
        		R.drawable.ic_menu_venue_flag);
        
        menu.add(Menu.NONE, MENU_CALL, 4, R.string.venue_activity_menu_call).setIcon(
        		R.drawable.ic_menu_venue_contact);
        
        menu.add(Menu.NONE, MENU_SHARE, 5, R.string.venue_activity_menu_share).setIcon(
        		R.drawable.ic_menu_venue_share);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean callEnabled = mStateHolder.getVenue() != null
                && !TextUtils.isEmpty(mStateHolder.getVenue().getPhone());
        menu.findItem(MENU_CALL).setEnabled(callEnabled);
 
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
	        case MENU_TIP_ADD:
	        	Intent intentTip = new Intent(VenueActivity.this, AddTipActivity.class);
	        	intentTip.putExtra(AddTipActivity.INTENT_EXTRA_VENUE, mStateHolder.getVenue());
	            startActivityForResult(intentTip, RESULT_CODE_ACTIVITY_ADD_TIP);
	            return true;
            case MENU_TODO_ADD:   
            	Intent intentTodo = new Intent(VenueActivity.this, AddTodoActivity.class);
            	intentTodo.putExtra(AddTodoActivity.INTENT_EXTRA_VENUE, mStateHolder.getVenue());
                startActivityForResult(intentTodo, RESULT_CODE_ACTIVITY_ADD_TODO);
                return true;
            case MENU_EDIT_VENUE:
                Intent intentEditVenue = new Intent(this, EditVenueOptionsActivity.class);
                intentEditVenue.putExtra(
                        EditVenueOptionsActivity.EXTRA_VENUE_PARCELABLE, mStateHolder.getVenue());
                startActivity(intentEditVenue);
                return true;
            case MENU_CALL:
                try {
                    Intent dial = new Intent();
                    dial.setAction(Intent.ACTION_DIAL);
                    dial.setData(Uri.parse("tel:" + mStateHolder.getVenue().getPhone()));
                    startActivity(dial);
                } catch (Exception ex) {
                    Log.e(TAG, "Error starting phone dialer intent.", ex);
                    Toast.makeText(this, "Sorry, we couldn't find any app to place a phone call!",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            case MENU_SHARE:
                Intent intentShare = new Intent(this, VenueShareActivity.class);
                intentShare.putExtra(VenueShareActivity.INTENT_EXTRA_VENUE, mStateHolder.getVenue());
                startActivity(intentShare);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTasks(null);
        return mStateHolder;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE:
                if (resultCode == Activity.RESULT_OK) {
                    mStateHolder.setCheckedInHere(true);
                    ensureUiCheckinButton();
                }
                break;
            case RESULT_CODE_ACTIVITY_ADD_TIP:
            	if (resultCode == Activity.RESULT_OK) {
            		Tip tip = data.getParcelableExtra(AddTipActivity.EXTRA_TIP_RETURNED);
            		VenueUtils.addTip(mStateHolder.getVenue(), tip);
            		ensureUiTipAdded();
            		prepareResultIntent();
            		Toast.makeText(this, getResources().getString(R.string.venue_activity_tip_added_ok), 
            				Toast.LENGTH_SHORT).show();
            	}
            	break;
            case RESULT_CODE_ACTIVITY_ADD_TODO:
            	if (resultCode == Activity.RESULT_OK) {
            		Todo todo = data.getParcelableExtra(AddTodoActivity.EXTRA_TODO_RETURNED);
            		VenueUtils.addTodo(mStateHolder.getVenue(), todo.getTip(), todo);
            		ensureUiTodosHere();
            		prepareResultIntent();
            		Toast.makeText(this, getResources().getString(R.string.venue_activity_todo_added_ok), 
            				Toast.LENGTH_SHORT).show();
            	}
            	break;
            case RESULT_CODE_ACTIVITY_TIP:
            case RESULT_CODE_ACTIVITY_TODO:
            	if (resultCode == Activity.RESULT_OK && data.hasExtra(TipActivity.EXTRA_TIP_RETURNED)) {
    	    		Tip tip = (Tip)data.getParcelableExtra(TipActivity.EXTRA_TIP_RETURNED);
    	    		Todo todo = data.hasExtra(TipActivity.EXTRA_TODO_RETURNED) ? 
    	    				(Todo)data.getParcelableExtra(TipActivity.EXTRA_TODO_RETURNED) : null;
    	    		VenueUtils.handleTipChange(mStateHolder.getVenue(), tip, todo);
            		ensureUiTodosHere();
            		prepareResultIntent();
                }
            	break;
            case RESULT_CODE_ACTIVITY_TIPS:
            	if (resultCode == Activity.RESULT_OK && data.hasExtra(VenueTipsActivity.INTENT_EXTRA_RETURN_VENUE)) {
            		Venue venue = (Venue)data.getParcelableExtra(VenueTipsActivity.INTENT_EXTRA_RETURN_VENUE);
        			VenueUtils.replaceTipsAndTodos(mStateHolder.getVenue(), venue);
            		ensureUiTodosHere();
            		prepareResultIntent();
            	}
            	break;
            case RESULT_CODE_ACTIVITY_TODOS:
            	if (resultCode == Activity.RESULT_OK && data.hasExtra(VenueTodosActivity.INTENT_EXTRA_RETURN_VENUE)) {
        			Venue venue = (Venue)data.getParcelableExtra(VenueTodosActivity.INTENT_EXTRA_RETURN_VENUE);
        			VenueUtils.replaceTipsAndTodos(mStateHolder.getVenue(), venue);
            		ensureUiTodosHere();
            		prepareResultIntent();
            	}
            	break;
        }
    }
    
    private void startCheckin() {
        Intent intent = new Intent(this, CheckinOrShoutGatherInfoActivity.class);
        intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_IS_CHECKIN, true);
        intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_VENUE_ID, mStateHolder.getVenue().getId());
        intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_VENUE_NAME, mStateHolder.getVenue().getName());
        startActivityForResult(intent, RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE);
    }
    
    private void startCheckinQuick() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean tellFriends = settings.getBoolean(Preferences.PREFERENCE_SHARE_CHECKIN, true);
        boolean tellTwitter = settings.getBoolean(Preferences.PREFERENCE_TWITTER_CHECKIN, false);
        boolean tellFacebook = settings.getBoolean(Preferences.PREFERENCE_FACEBOOK_CHECKIN, false);
        
        Intent intent = new Intent(VenueActivity.this, CheckinExecuteActivity.class);
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_VENUE_ID, mStateHolder.getVenue().getId());
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_SHOUT, "");
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_TELL_FRIENDS, tellFriends);
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_TELL_TWITTER, tellTwitter);
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_TELL_FACEBOOK, tellFacebook);
        startActivityForResult(intent, RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE);
    }
    
    private void showWebViewForSpecial() {
        Intent intent = new Intent(this, SpecialWebViewActivity.class);
        intent.putExtra(SpecialWebViewActivity.EXTRA_CREDENTIALS_USERNAME, 
                PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.PREFERENCE_LOGIN, ""));
        intent.putExtra(SpecialWebViewActivity.EXTRA_CREDENTIALS_PASSWORD, 
                PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.PREFERENCE_PASSWORD, ""));
        intent.putExtra(SpecialWebViewActivity.EXTRA_SPECIAL_ID, 
                mStateHolder.getVenue().getSpecials().get(0).getId());
        startActivity(intent);
    }
    
    private void showTodoHereActivity() {
		Venue venue = new Venue();
		venue.setName(mStateHolder.getVenue().getName());
		venue.setAddress(mStateHolder.getVenue().getAddress());
		venue.setCrossstreet(mStateHolder.getVenue().getCrossstreet());
		
    	Group<Todo> todos = mStateHolder.getVenue().getTodos();
    	for (Todo it : todos) {
    		it.getTip().setVenue(venue);
    	}
    	
    	if (todos.size() == 1) {
        	Todo todo = (Todo) todos.get(0);
        	
        	Intent intent = new Intent(VenueActivity.this, TipActivity.class);
            intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, todo.getTip());
            intent.putExtra(TipActivity.EXTRA_VENUE_CLICKABLE, false);
            startActivityForResult(intent, RESULT_CODE_ACTIVITY_TODO);
    	} else if (todos.size() > 1) {
    		Intent intent = new Intent(VenueActivity.this, VenueTodosActivity.class);
	        intent.putExtra(VenueTodosActivity.INTENT_EXTRA_VENUE, mStateHolder.getVenue());
	        startActivityForResult(intent, RESULT_CODE_ACTIVITY_TODOS);
    	}
    }
	
    private int getFriendCountAtVenue() {
        int count = 0;
        
        Venue venue = mStateHolder.getVenue();
        if (venue.getCheckins() != null) {
            for (Checkin it : venue.getCheckins()) {
                User user = it.getUser();
                if (UserUtils.isFriend(user)) {
                    count++;
                }
            }
        }

        return count;
    }
    
    private int getTipCountFriendsAtVenue() {
        int count = 0;
        
        Venue venue = mStateHolder.getVenue();
        if (venue.getTips() != null) {
            for (Tip it : venue.getTips()) {
                User user = it.getUser();
                if (UserUtils.isFriend(user)) {
                    count++;
                }
            }
        }

        return count;
    }

    private static class TaskVenue extends AsyncTask<String, Void, Venue> {

        private VenueActivity mActivity;
        private Exception mReason;

        public TaskVenue(VenueActivity activity) {
        	mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Venue doInBackground(String... params) {
            try {
            	Foursquared foursquared = (Foursquared)mActivity.getApplication();
                return foursquared.getFoursquare().venue(
                        params[0],
                        LocationUtils.createFoursquareLocation(foursquared.getLastKnownLocation()));
            } catch (Exception e) {
                Log.e(TAG, "Error getting venue details.", e);
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Venue venue) {
        	if (mActivity != null) {
	        	mActivity.mStateHolder.setIsRunningTaskVenue(false);
	        	if (venue != null) {
	        		mActivity.mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_FULL);
	        		mActivity.mStateHolder.setVenue(venue);
	        		mActivity.prepareResultIntent();
	        		mActivity.ensureUi();
	        		
	        	} else {
	        		NotificationsUtil.ToastReasonForFailure(mActivity, mReason);
	        		mActivity.finish();
	        	}
        	}
        }

        @Override
        protected void onCancelled() {
        }
        
        public void setActivity(VenueActivity activity) {
        	mActivity = activity;
        }
    }


    private static final class StateHolder {
        private Venue mVenue;
        private String mVenueId;
        private boolean mCheckedInHere;
        
        private TaskVenue mTaskVenue;
        private boolean mIsRunningTaskVenue;
        private int mLoadType;
        
        public static final int LOAD_TYPE_VENUE_ID      = 0;
        public static final int LOAD_TYPE_VENUE_PARTIAL = 1;
        public static final int LOAD_TYPE_VENUE_FULL    = 2;
        
        public StateHolder() {
        	mIsRunningTaskVenue = false;
        }
        
        public Venue getVenue() {
        	return mVenue;
        }
        
        public void setVenue(Venue venue) {
        	mVenue = venue;
        }
        
        public void setVenueId(String venueId) {
        	mVenueId = venueId;
        }
        
        public boolean getCheckedInHere() {
        	return mCheckedInHere;
        }
        
        public void setCheckedInHere(boolean checkedInHere) {
        	mCheckedInHere = checkedInHere;
        }
        
        public void setIsRunningTaskVenue(boolean isRunningTaskVenue) {
        	mIsRunningTaskVenue = isRunningTaskVenue;
        }
        
        public void startTaskVenue(VenueActivity activity) {
        	if (!mIsRunningTaskVenue) {
        		mIsRunningTaskVenue = true;
	            mTaskVenue = new TaskVenue(activity);
	            if (mLoadType == LOAD_TYPE_VENUE_ID) {
	            	mTaskVenue.execute(mVenueId); 
	            } else if (mLoadType == LOAD_TYPE_VENUE_PARTIAL) {
	            	mTaskVenue.execute(mVenue.getId());
	            }
        	}
        }
        
        public void setActivityForTasks(VenueActivity activity) {
        	if (mTaskVenue != null) {
        		mTaskVenue.setActivity(activity);
        	}
        }
        
        public int getLoadType() {
        	return mLoadType;
        }
        
        public void setLoadType(int loadType) {
        	mLoadType = loadType;
        }
    }
    
    /**
     * Handles population of the mayor photo. The strip of checkin photos has its own
     * internal observer.
     */
    private class RemoteResourceManagerObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            mHandler.post(mRunnableMayorPhoto);
        }
    }
    
    private Runnable mRunnableMayorPhoto = new Runnable() {
        @Override
        public void run() {
        	ImageView ivMayorPhoto = (ImageView)findViewById(R.id.venueActivityMayorPhoto);
        	if (ivMayorPhoto.getTag() != null) {
        		String mayorPhotoUrl = (String)ivMayorPhoto.getTag();
        		try {
                    Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(Uri.parse(mayorPhotoUrl)));
                    ivMayorPhoto.setImageBitmap(bitmap);
                    ivMayorPhoto.setTag(null);
                    ivMayorPhoto.invalidate();
                } catch (IOException ex) {
                    Log.e(TAG, "Error decoding mayor photo on notification, ignoring.", ex);
                }
        	}
        }
    };
}
