/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.facebook.android.Facebook;
import com.facebook.android.FacebookUtil;
import com.facebook.android.FacebookWebViewActivity;
import com.facebook.android.Facebook.PreparedUrl;
import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.FriendInvitesResult;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquared.util.AddressBookEmailBuilder;
import com.joelapenna.foursquared.util.AddressBookUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.AddressBookEmailBuilder.ContactSimple;
import com.joelapenna.foursquared.widget.FriendSearchAddFriendAdapter;
import com.joelapenna.foursquared.widget.FriendSearchInviteNonFoursquareUserAdapter;
import com.joelapenna.foursquared.widget.SeparatedListAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnDismissListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user search for friends via first+last name, phone number, or
 * twitter names. Once a list of matching users is found, the user can click on
 * elements in the list to send a friend request to them. When the request is
 * successfully sent, that user is removed from the list. You can add the
 * INPUT_TYPE key to the intent while launching the activity to control what
 * type of friend search the activity will perform. Pass in one of the following
 * values:
 * <ul>
 * <li>INPUT_TYPE_NAME_OR_PHONE</li>
 * <li>INPUT_TYPE_TWITTERNAME</li>
 * <li>INPUT_TYPE_ADDRESSBOOK</li>
 * <li>INPUT_TYPE_ADDRESSBOOK_INVITE</li>
 * </ul>
 * 
 * @date February 11, 2010
 * @author Mark Wyszomierski (markww@gmail.com), foursquare.
 */
public class AddFriendsByUserInputActivity extends Activity {
    private static final String TAG = "AddFriendsByUserInputActivity";
    private static final boolean DEBUG = FoursquaredSettings.DEBUG;
    
    private static final int DIALOG_ID_CONFIRM_INVITE_ALL = 1;

    public static final String INPUT_TYPE = "com.joelapenna.foursquared.AddFriendsByUserInputActivity.INPUT_TYPE";
    public static final int INPUT_TYPE_NAME_OR_PHONE = 0;
    public static final int INPUT_TYPE_TWITTERNAME = 1;
    public static final int INPUT_TYPE_ADDRESSBOOK = 2;
    public static final int INPUT_TYPE_ADDRESSBOOK_INVITE = 3;
    public static final int INPUT_TYPE_FACEBOOK = 4;
    
    private static final int ACTIVITY_RESULT_FACEBOOK_WEBVIEW_ACTIVITY = 5;

    private TextView mTextViewInstructions;
    private TextView mTextViewAdditionalInstructions;
    private EditText mEditInput;
    private Button mBtnSearch;
    private ListView mListView;
    private ProgressDialog mDlgProgress;

    private int mInputType;
    private SeparatedListAdapter mListAdapter;

    private StateHolder mStateHolder;

    
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
        setContentView(R.layout.add_friends_by_user_input_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        mTextViewInstructions = (TextView) findViewById(R.id.addFriendInstructionsTextView);
        mTextViewAdditionalInstructions = (TextView) findViewById(R.id.addFriendInstructionsAdditionalTextView);
        mEditInput = (EditText) findViewById(R.id.addFriendInputEditText);
        mBtnSearch = (Button) findViewById(R.id.addFriendSearchButton);
        mListView = (ListView) findViewById(R.id.addFriendResultsListView);
 
        mListAdapter = new SeparatedListAdapter(this);
        mListView.setAdapter(mListAdapter);
        mListView.setItemsCanFocus(true);
        mListView.setDividerHeight(0);

        mBtnSearch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startSearch(mEditInput.getText().toString());
            }
        });
        mEditInput.addTextChangedListener(mNamesFieldWatcher);
        mEditInput.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL) {
                    startSearch(mEditInput.getText().toString());
                }
                return false;
            }
        });
        mBtnSearch.setEnabled(false);

        mInputType = getIntent().getIntExtra(INPUT_TYPE, INPUT_TYPE_NAME_OR_PHONE);
        switch (mInputType) {
            case INPUT_TYPE_TWITTERNAME:
                mTextViewInstructions.setText(getResources().getString(
                        R.string.add_friends_by_twitter_instructions));
                mEditInput.setHint(getResources().getString(R.string.add_friends_by_twitter_hint));
                mEditInput.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            case INPUT_TYPE_ADDRESSBOOK:
                mTextViewInstructions.setText(getResources().getString(
                        R.string.add_friends_by_addressbook_instructions));
                mTextViewAdditionalInstructions.setText(getResources().getString(
                        R.string.add_friends_by_addressbook_additional_instructions));
                mEditInput.setVisibility(View.GONE);
                mBtnSearch.setVisibility(View.GONE);
                break;
            case INPUT_TYPE_ADDRESSBOOK_INVITE:
                mTextViewInstructions.setText(getResources().getString(
                        R.string.add_friends_by_addressbook_instructions));
                mTextViewAdditionalInstructions.setText(getResources().getString(
                        R.string.add_friends_by_addressbook_additional_instructions));
                mEditInput.setVisibility(View.GONE);
                mBtnSearch.setVisibility(View.GONE);
                break;
            case INPUT_TYPE_FACEBOOK:
                mTextViewInstructions.setText(getResources().getString(
                        R.string.add_friends_by_facebook_instructions));
                mTextViewAdditionalInstructions.setText(getResources().getString(
                        R.string.add_friends_by_facebook_additional_instructions));
                mEditInput.setVisibility(View.GONE);
                mBtnSearch.setVisibility(View.GONE);
                break;
            default:
                mTextViewInstructions.setText(getResources().getString(
                        R.string.add_friends_by_name_or_phone_instructions));
                mEditInput.setHint(getResources().getString(R.string.add_friends_by_name_or_phone_hint));
                mEditInput.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                break;
        }

        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivityForTaskFindFriends(this);
            mStateHolder.setActivityForTaskFriendRequest(this);
            mStateHolder.setActivityForTaskSendInvite(this);

            // If we have run before, restore matches divider.
            if (mStateHolder.getRanOnce()) {
                populateListFromStateHolder();
            }
        } else {
            mStateHolder = new StateHolder();

            // If we are scanning the address book, or a facebook search, we should 
            // kick off immediately.
            switch (mInputType) {
                case INPUT_TYPE_ADDRESSBOOK:
                case INPUT_TYPE_ADDRESSBOOK_INVITE:
                    startSearch("");
                    break;
                case INPUT_TYPE_FACEBOOK:
                    startFacebookWebViewActivity();
                    break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mStateHolder.getIsRunningTaskFindFriends()) {
            startProgressBar(getResources().getString(R.string.add_friends_activity_label),
                    getResources().getString(R.string.add_friends_progress_bar_message_find));
        } else if (mStateHolder.getIsRunningTaskSendFriendRequest()) {
            startProgressBar(getResources().getString(R.string.add_friends_activity_label),
                    getResources()
                            .getString(R.string.add_friends_progress_bar_message_send_request));
        } else if (mStateHolder.getIsRunningTaskSendInvite()) {
            startProgressBar(getResources().getString(R.string.add_friends_activity_label),
                    getResources()
                            .getString(R.string.add_friends_progress_bar_message_send_invite));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopProgressBar();
        
        if (isFinishing()) {
            mListAdapter.removeObserver();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLoggedOutReceiver);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTaskFindFriends(null);
        mStateHolder.setActivityForTaskFriendRequest(null);
        mStateHolder.setActivityForTaskSendInvite(null);
        return mStateHolder;
    }

    private void userAdd(User user) {
        startProgressBar(getResources().getString(R.string.add_friends_activity_label),
                getResources().getString(R.string.add_friends_progress_bar_message_send_request));
        mStateHolder.startTaskSendFriendRequest(AddFriendsByUserInputActivity.this, user.getId());
    }

    private void userInfo(User user) {
        Intent intent = new Intent(AddFriendsByUserInputActivity.this, UserDetailsActivity.class);
        intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, user);
        startActivity(intent);
    }
    
    private void userInvite(ContactSimple contact) {
        startProgressBar(getResources().getString(R.string.add_friends_activity_label),
                getResources().getString(R.string.add_friends_progress_bar_message_send_invite));
        mStateHolder.startTaskSendInvite(AddFriendsByUserInputActivity.this, contact.mEmail, false);
    }
    
    private void inviteAll() {
        startProgressBar(getResources().getString(R.string.add_friends_activity_label),
                getResources().getString(R.string.add_friends_progress_bar_message_send_invite));
        mStateHolder.startTaskSendInvite(
                AddFriendsByUserInputActivity.this, mStateHolder.getUsersNotOnFoursquareAsCommaSepString(), true);
    }
    
    private void startSearch(String input) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditInput.getWindowToken(), 0);
    
        mEditInput.setEnabled(false);
        mBtnSearch.setEnabled(false);
        startProgressBar(getResources().getString(R.string.add_friends_activity_label),
                getResources().getString(R.string.add_friends_progress_bar_message_find));
        mStateHolder.startTaskFindFriends(AddFriendsByUserInputActivity.this, input);
    }
    
    private void startFacebookWebViewActivity() {
        Intent intent = new Intent(this, FacebookWebViewActivity.class);
        intent.putExtra(FacebookWebViewActivity.INTENT_EXTRA_ACTION, Facebook.LOGIN);
        intent.putExtra(FacebookWebViewActivity.INTENT_EXTRA_KEY_APP_ID, 
                getResources().getString(R.string.facebook_api_key));
        intent.putExtra(FacebookWebViewActivity.INTENT_EXTRA_KEY_PERMISSIONS, 
                new String[] {}); //{"publish_stream", "read_stream", "offline_access"});
        intent.putExtra(FacebookWebViewActivity.INTENT_EXTRA_KEY_DEBUG, false);
        intent.putExtra(FacebookWebViewActivity.INTENT_EXTRA_KEY_CLEAR_COOKIES, true);
        startActivityForResult(intent, ACTIVITY_RESULT_FACEBOOK_WEBVIEW_ACTIVITY);
    }

    private void startProgressBar(String title, String message) {
        if (mDlgProgress == null) {
            mDlgProgress = ProgressDialog.show(this, title, message);
        }
        mDlgProgress.show();
    }

    private void stopProgressBar() {
        if (mDlgProgress != null) {
            mDlgProgress.dismiss();
            mDlgProgress = null;
        }
    }
     
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ID_CONFIRM_INVITE_ALL:
                AlertDialog dlgInfo = new AlertDialog.Builder(this)
                    .setTitle(getResources().getString(R.string.add_friends_contacts_title_invite_all))
                    .setIcon(0)
                    .setPositiveButton(getResources().getString(R.string.yes), 
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    inviteAll();
                                }
                            })
                    .setNegativeButton(getResources().getString(R.string.no), 
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                    .setMessage(getResources().getString(R.string.add_friends_contacts_message_invite_all,
                            String.valueOf(mStateHolder.getUsersNotOnFoursquare().size())))
                    .create();
                dlgInfo.setOnDismissListener(new OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        removeDialog(DIALOG_ID_CONFIRM_INVITE_ALL);
                    }
                });
                
                return dlgInfo;
        }
        return null;
    }
    
    /**
     * Listen for FacebookWebViewActivity finishing, inspect success/failure and returned
     * request parameters.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_RESULT_FACEBOOK_WEBVIEW_ACTIVITY) {
            // If RESULT_OK, means the request was attempted, but we still have to check the return status.
            if (resultCode == RESULT_OK) {
                // Check return status.
                if (data.getBooleanExtra(FacebookWebViewActivity.INTENT_RESULT_KEY_RESULT_STATUS, false)) {
                        
                    // If ok, the result bundle will contain all data from the webview.
                    Bundle bundle = data.getBundleExtra(FacebookWebViewActivity.INTENT_RESULT_KEY_RESULT_BUNDLE);
                        
                    // We can switch on the action here, the activity echoes it back to us for convenience.
                    String suppliedAction = data.getStringExtra(FacebookWebViewActivity.INTENT_RESULT_KEY_SUPPLIED_ACTION);
                    if (suppliedAction.equals(Facebook.LOGIN)) {
                        // We can now start a task to fetch foursquare friends using their facebook id.
                        mStateHolder.startTaskFindFriends(
                                AddFriendsByUserInputActivity.this, bundle.getString(Facebook.TOKEN));
                    }
                } else {
                    // Error running the operation, report to user perhaps.
                    String error = data.getStringExtra(FacebookWebViewActivity.INTENT_RESULT_KEY_ERROR);
                    Log.e(TAG, error);
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                // If the user cancelled enterting their facebook credentials, exit here too.
                finish();
            }
        }
    }

    private void populateListFromStateHolder() {
        mListAdapter.removeObserver();
        mListAdapter = new SeparatedListAdapter(this);
        
        if (mStateHolder.getUsersOnFoursquare().size() + mStateHolder.getUsersNotOnFoursquare().size() > 0) {
            if (mStateHolder.getUsersOnFoursquare().size() > 0) {
                FriendSearchAddFriendAdapter adapter = 
                    new FriendSearchAddFriendAdapter(
                        this,
                        mButtonRowClickHandler,
                        ((Foursquared)getApplication()).getRemoteResourceManager());
                adapter.setGroup(mStateHolder.getUsersOnFoursquare());
                mListAdapter.addSection(
                    getResources().getString(R.string.add_friends_contacts_found_on_foursqare),
                    adapter);
            }
            if (mStateHolder.getUsersNotOnFoursquare().size() > 0) {
                FriendSearchInviteNonFoursquareUserAdapter adapter = 
                    new FriendSearchInviteNonFoursquareUserAdapter(
                        this,
                        mAdapterListenerInvites);
                adapter.setContacts(mStateHolder.getUsersNotOnFoursquare());
                mListAdapter.addSection(
                    getResources().getString(R.string.add_friends_contacts_not_found_on_foursqare),
                    adapter);
            }
        } else {
            Toast.makeText(this, getResources().getString(R.string.add_friends_no_matches),
                    Toast.LENGTH_SHORT).show();
        }
        mListView.setAdapter(mListAdapter);
    }
    
    private void onFindFriendsTaskComplete(FindFriendsResult result, Exception ex) {
        if (result != null) {
            mStateHolder.setUsersOnFoursquare(result.getUsersOnFoursquare());
            mStateHolder.setUsersNotOnFoursquare(result.getUsersNotOnFoursquare());
            
            if (result.getUsersOnFoursquare().size() + result.getUsersNotOnFoursquare().size() < 1) {
                Toast.makeText(this, getResources().getString(R.string.add_friends_no_matches),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            NotificationsUtil.ToastReasonForFailure(AddFriendsByUserInputActivity.this, ex);
        }
        
        populateListFromStateHolder();
        
        mEditInput.setEnabled(true);
        mBtnSearch.setEnabled(true);
        mStateHolder.setIsRunningTaskFindFriends(false);
        stopProgressBar();
    }

    private void onSendFriendRequestTaskComplete(User friendRequestRecipient, Exception ex) {
        
        if (friendRequestRecipient != null) {
            // We do a linear search to find the row to remove, ouch.
            int position = 0;
            for (User it : mStateHolder.getUsersOnFoursquare()) {
                if (it.getId().equals(friendRequestRecipient.getId())) {
                    mStateHolder.getUsersOnFoursquare().remove(position);
                    break;
                }
                position++;
            }
            
            mListAdapter.notifyDataSetChanged();
            
            Toast.makeText(AddFriendsByUserInputActivity.this,
                    getResources().getString(R.string.add_friends_request_sent_ok),
                    Toast.LENGTH_SHORT).show();
        } else {
            NotificationsUtil.ToastReasonForFailure(AddFriendsByUserInputActivity.this, ex);
        }
        
        mEditInput.setEnabled(true);
        mBtnSearch.setEnabled(true);
        mStateHolder.setIsRunningTaskSendFriendRequest(false);
        stopProgressBar();
    }
    
    private void onSendInviteTaskComplete(String email, boolean isAllEmails, Exception ex) {
        if (email != null) {
            if (isAllEmails) {
                mStateHolder.getUsersNotOnFoursquare().clear();
                
                Toast.makeText(AddFriendsByUserInputActivity.this,
                        getResources().getString(R.string.add_friends_invites_sent_ok),
                        Toast.LENGTH_SHORT).show();
            } else {
                // We do a linear search to find the row to remove, ouch.
                int position = 0;
                for (ContactSimple it : mStateHolder.getUsersNotOnFoursquare()) {
                    if (it.mEmail.equals(email)) {
                        mStateHolder.getUsersNotOnFoursquare().remove(position);
                        break;
                    }
                    position++;  
                }
                
                Toast.makeText(AddFriendsByUserInputActivity.this,
                        getResources().getString(R.string.add_friends_invite_sent_ok),
                        Toast.LENGTH_SHORT).show();
            }
            
            mListAdapter.notifyDataSetChanged();
        } else {
            NotificationsUtil.ToastReasonForFailure(AddFriendsByUserInputActivity.this, ex);
        }
        
        mEditInput.setEnabled(true);
        mBtnSearch.setEnabled(true);
        mStateHolder.setIsRunningTaskSendInvite(false);
        stopProgressBar();
    }

    private static class FindFriendsTask extends AsyncTask<String, Void, FindFriendsResult> {

        private AddFriendsByUserInputActivity mActivity;
        private Exception mReason;

        public FindFriendsTask(AddFriendsByUserInputActivity activity) {
            mActivity = activity;
        }

        public void setActivity(AddFriendsByUserInputActivity activity) {
            mActivity = activity;
        }

        @Override
        protected void onPreExecute() {
            mActivity.startProgressBar(mActivity.getResources().getString(
                    R.string.add_friends_activity_label), mActivity.getResources().getString(
                    R.string.add_friends_progress_bar_message_find));
        }

        @Override
        protected FindFriendsResult doInBackground(String... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();

                FindFriendsResult result = new FindFriendsResult();
                switch (mActivity.mInputType) {
                    case INPUT_TYPE_TWITTERNAME:
                        result.setUsersOnFoursquare(foursquare.findFriendsByTwitter(params[0]));
                        break;
                    case INPUT_TYPE_ADDRESSBOOK:
                        scanAddressBook(result, foursquare, foursquared, false);
                        break;
                    case INPUT_TYPE_ADDRESSBOOK_INVITE:
                        scanAddressBook(result, foursquare, foursquared, true);
                        break;
                    case INPUT_TYPE_FACEBOOK:
                        // For facebook, we need to first get all friend uids, then use that with the foursquare api.
                        String facebookFriendIds = getFacebookFriendIds(params[0]);
                        if (!TextUtils.isEmpty(facebookFriendIds)) {
                            result.setUsersOnFoursquare(foursquare.findFriendsByFacebook(facebookFriendIds));
                        } else {
                            result.setUsersOnFoursquare(new Group<User>());
                        }
                        break;
                    default:
                        // Combine searches for name/phone, results returned in one list.
                        Group<User> users = new Group<User>();
                        users.addAll(foursquare.findFriendsByPhone(params[0]));
                        users.addAll(foursquare.findFriendsByName(params[0]));
                        result.setUsersOnFoursquare(users);
                        break;
                }
                return result;
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "FindFriendsTask: Exception doing add friends by name", e);
                mReason = e;
            }
            return null;
        }
        
        private String getFacebookFriendIds(String facebookToken) {
            Facebook facebook = new Facebook();
            facebook.setAccessToken(facebookToken);
            
            String friendsAsJson = "";
            try {
                PreparedUrl purl = facebook.requestUrl("me/friends");
                friendsAsJson = FacebookUtil.openUrl(purl.getUrl(), purl.getHttpMethod(), purl.getParameters());
            } catch (Exception ex) {
                Log.e(TAG, "Error getting facebook friends as json.", ex);
                return friendsAsJson;
            }
            
            // {"data":[{"name":"Friends Name","id":"12345"}]}
            StringBuilder sb = new StringBuilder(2048);
            try {
                JSONObject json = new JSONObject(friendsAsJson);
                JSONArray friends = json.getJSONArray("data");
                for (int i = 0, m = friends.length(); i < m; i++) {
                    JSONObject friend = friends.getJSONObject(i);
                    sb.append(friend.get("id"));
                    sb.append(",");
                }
                if (sb.length() > 0 && sb.charAt(sb.length()-1) == ',') {
                    sb.deleteCharAt(sb.length()-1);
                }
            }
            catch (Exception ex) {
                Log.e(TAG, "Error deserializing facebook friends json object.", ex);
            }
            
            return sb.toString();
        }
        
        private void scanAddressBook(FindFriendsResult result,
                                     Foursquare foursquare,
                                     Foursquared foursquared,
                                     boolean invites) 
            throws Exception {
            
            AddressBookUtils addr = AddressBookUtils.addressBookUtils();
            AddressBookEmailBuilder bld = addr.getAllContactsEmailAddressesInfo(mActivity);
            String phones = addr.getAllContactsPhoneNumbers(mActivity);
            String emails = bld.getEmailsCommaSeparated();
            
            if (!TextUtils.isEmpty(phones) || !TextUtils.isEmpty(emails)) {
                FriendInvitesResult xml = foursquare.findFriendsByPhoneOrEmail(phones, emails);
                result.setUsersOnFoursquare(xml.getContactsOnFoursquare());
                if (invites) {
                    
                    // Get a contact name for each email address we can send an invite to.
                    List<ContactSimple> contactsNotOnFoursquare = new ArrayList<ContactSimple>();
                    for (String it : xml.getContactEmailsNotOnFoursquare()) {
                        ContactSimple contact = new ContactSimple();
                        contact.mEmail = it;
                        contact.mName  = bld.getNameForEmail(it);
                        contactsNotOnFoursquare.add(contact);
                    }
                    result.setUsersNotOnFoursquare(contactsNotOnFoursquare);
                }
            }
        }
        
        @Override
        protected void onPostExecute(FindFriendsResult result) {
            if (DEBUG) Log.d(TAG, "FindFriendsTask: onPostExecute()");
            if (mActivity != null) {
                mActivity.onFindFriendsTaskComplete(result, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onFindFriendsTaskComplete(
                    null, new Exception("Friend search cancelled."));
            }
        }
    }

    private static class SendFriendRequestTask extends AsyncTask<String, Void, User> {

        private AddFriendsByUserInputActivity mActivity;
        private Exception mReason;

        public SendFriendRequestTask(AddFriendsByUserInputActivity activity) {
            mActivity = activity;
        }

        public void setActivity(AddFriendsByUserInputActivity activity) {
            mActivity = activity;
        }

        @Override
        protected void onPreExecute() {
            mActivity.startProgressBar(mActivity.getResources().getString(
                    R.string.add_friends_activity_label), mActivity.getResources().getString(
                    R.string.add_friends_progress_bar_message_send_request));
        }

        @Override
        protected User doInBackground(String... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();

                User user = foursquare.friendSendrequest(params[0]);
                return user;
            } catch (Exception e) {
                if (DEBUG)
                    Log.d(TAG, "SendFriendRequestTask: Exception doing send friend request.", e);
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(User user) {
            if (DEBUG) Log.d(TAG, "SendFriendRequestTask: onPostExecute()");
            if (mActivity != null) {
                mActivity.onSendFriendRequestTaskComplete(user, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onSendFriendRequestTaskComplete(null, new Exception(
                        "Friend invitation cancelled."));
            }
        }
    }
    
    private static class SendInviteTask extends AsyncTask<String, Void, String> {

        private AddFriendsByUserInputActivity mActivity;
        private boolean mIsAllEmails;
        private Exception mReason;

        public SendInviteTask(AddFriendsByUserInputActivity activity, boolean isAllEmails) {
            mActivity = activity;
            mIsAllEmails = isAllEmails;
        }

        public void setActivity(AddFriendsByUserInputActivity activity) {
            mActivity = activity;
        }

        @Override
        protected void onPreExecute() {
            mActivity.startProgressBar(mActivity.getResources().getString(
                    R.string.add_friends_activity_label), mActivity.getResources().getString(
                    R.string.add_friends_progress_bar_message_send_invite));
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();

                foursquare.inviteByEmail(params[0]);
                return params[0];
            } catch (Exception e) {
                Log.e(TAG, "SendInviteTask: Exception sending invite.", e);
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(String email) {
            if (DEBUG) Log.d(TAG, "SendInviteTask: onPostExecute()");
            if (mActivity != null) {
                mActivity.onSendInviteTaskComplete(email, mIsAllEmails, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) { 
                mActivity.onSendInviteTaskComplete(null, mIsAllEmails, 
                        new Exception("Invite send cancelled."));
            }
        }
    }

    private static class StateHolder {
        FindFriendsTask mTaskFindFriends;
        SendFriendRequestTask mTaskSendFriendRequest;
        SendInviteTask mTaskSendInvite;
        Group<User> mUsersOnFoursquare;
        List<ContactSimple> mUsersNotOnFoursquare;
        boolean mIsRunningTaskFindFriends;
        boolean mIsRunningTaskSendFriendRequest;
        boolean mIsRunningTaskSendInvite;
        boolean mRanOnce;

        public StateHolder() {
            mUsersOnFoursquare = new Group<User>();
            mUsersNotOnFoursquare = new ArrayList<ContactSimple>();
            mIsRunningTaskFindFriends = false;
            mIsRunningTaskSendFriendRequest = false;
            mIsRunningTaskSendInvite = false;
            mRanOnce = false;
        }

        public Group<User> getUsersOnFoursquare() {
            return mUsersOnFoursquare;
        }
        
        public void setUsersOnFoursquare(Group<User> usersOnFoursquare) {
            mUsersOnFoursquare = usersOnFoursquare;
            mRanOnce = true;
        }

        public List<ContactSimple> getUsersNotOnFoursquare() {
            return mUsersNotOnFoursquare;
        }
        
        public void setUsersNotOnFoursquare(List<ContactSimple> usersNotOnFoursquare) {
            mUsersNotOnFoursquare = usersNotOnFoursquare;
        }

        public void startTaskFindFriends(AddFriendsByUserInputActivity activity, String input) {
            mIsRunningTaskFindFriends = true;
            mTaskFindFriends = new FindFriendsTask(activity);
            mTaskFindFriends.execute(input);
        }

        public void startTaskSendFriendRequest(AddFriendsByUserInputActivity activity, String userId) {
            mIsRunningTaskSendFriendRequest = true;
            mTaskSendFriendRequest = new SendFriendRequestTask(activity);
            mTaskSendFriendRequest.execute(userId);
        }
        
        public void startTaskSendInvite(AddFriendsByUserInputActivity activity, String email, boolean isAllEmails) {
            mIsRunningTaskSendInvite = true;
            mTaskSendInvite = new SendInviteTask(activity, isAllEmails);
            mTaskSendInvite.execute(email);
        }
        
        public void setActivityForTaskFindFriends(AddFriendsByUserInputActivity activity) {
            if (mTaskFindFriends != null) {
                mTaskFindFriends.setActivity(activity);
            }
        }

        public void setActivityForTaskFriendRequest(AddFriendsByUserInputActivity activity) {
            if (mTaskSendFriendRequest != null) {
                mTaskSendFriendRequest.setActivity(activity);
            }
        }
        
        public void setActivityForTaskSendInvite(AddFriendsByUserInputActivity activity) {
            if (mTaskSendInvite != null) {
                mTaskSendInvite.setActivity(activity);
            }
        }
        
        public void setIsRunningTaskFindFriends(boolean isRunning) {
            mIsRunningTaskFindFriends = isRunning;
        }

        public void setIsRunningTaskSendFriendRequest(boolean isRunning) {
            mIsRunningTaskSendFriendRequest = isRunning;
        }

        public void setIsRunningTaskSendInvite(boolean isRunning) {
            mIsRunningTaskSendInvite = isRunning;
        }
        
        public boolean getIsRunningTaskFindFriends() {
            return mIsRunningTaskFindFriends;
        }

        public boolean getIsRunningTaskSendFriendRequest() {
            return mIsRunningTaskSendFriendRequest;
        }

        public boolean getIsRunningTaskSendInvite() {
            return mIsRunningTaskSendInvite;
        }
        
        public boolean getRanOnce() {
            return mRanOnce;
        }
        
        public String getUsersNotOnFoursquareAsCommaSepString() {
            StringBuilder sb = new StringBuilder(2048);
            for (ContactSimple it : mUsersNotOnFoursquare) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(it.mEmail);
            }
            return sb.toString();
        }
    }

    private TextWatcher mNamesFieldWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mBtnSearch.setEnabled(!TextUtils.isEmpty(s));
        }
    };
    
    /** 
     * This handler will be called when the user clicks on buttons in one of the
     * listview's rows.
     */
    private FriendSearchAddFriendAdapter.ButtonRowClickHandler mButtonRowClickHandler = 
        new FriendSearchAddFriendAdapter.ButtonRowClickHandler() {
            @Override
            public void onBtnClickAdd(User user) {
                userAdd(user);
            }
    
            @Override
            public void onInfoAreaClick(User user) {
                userInfo(user);
            }
    };
    
    private FriendSearchInviteNonFoursquareUserAdapter.AdapterListener mAdapterListenerInvites = 
        new FriendSearchInviteNonFoursquareUserAdapter.AdapterListener() {
            @Override
            public void onBtnClickInvite(ContactSimple contact) {
                userInvite(contact);
            }
    
            @Override
            public void onInfoAreaClick(ContactSimple contact) {
                // We could popup an intent for this contact so they can see 
                // who we're talking about?
            }

            @Override
            public void onInviteAll() {
                showDialog(DIALOG_ID_CONFIRM_INVITE_ALL);
            }
    };
    
    private static class FindFriendsResult {
        private Group<User> mUsersOnFoursquare;
        private List<ContactSimple> mUsersNotOnFoursquare;
        
        public FindFriendsResult() {
            mUsersOnFoursquare = new Group<User>();
            mUsersNotOnFoursquare = new ArrayList<ContactSimple>();
        }
        
        public Group<User> getUsersOnFoursquare() {
            return mUsersOnFoursquare;
        }
        
        public void setUsersOnFoursquare(Group<User> users) {
            if (users != null) {
                mUsersOnFoursquare = users;
            }
        }

        public List<ContactSimple> getUsersNotOnFoursquare() {
            return mUsersNotOnFoursquare;
        }
        
        public void setUsersNotOnFoursquare(List<ContactSimple> usersNotOnFoursquare) {
            if (usersNotOnFoursquare != null) {
                mUsersNotOnFoursquare = usersNotOnFoursquare;
            }
        }
    }
}
