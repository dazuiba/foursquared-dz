/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.error.FoursquareException;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Tip;
import com.joelapenna.foursquare.types.Todo;
import com.joelapenna.foursquared.app.LoadableListActivityWithViewAndHeader;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.util.MenuUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.TipUtils;
import com.joelapenna.foursquared.widget.SegmentedButton;
import com.joelapenna.foursquared.widget.SegmentedButton.OnClickListenerSegmentedButton;
import com.joelapenna.foursquared.widget.TodosListAdapter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.Observable;
import java.util.Observer;

/**
 * Shows a list of a user's todos. We can operate on the logged-in user,
 * or a friend user, specified through the intent extras. 
 * 
 * If operating on the logged-in user, we remove items from the todo list
 * if they mark a todo as done or un-mark it. If operating on another user,
 * we do not remove them from the list.
 * 
 * @date September 12, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class TodosActivity extends LoadableListActivityWithViewAndHeader {
    static final String TAG = "TodosActivity";
    static final boolean DEBUG = FoursquaredSettings.DEBUG;
    

    public static final String INTENT_EXTRA_USER_ID = Foursquared.PACKAGE_NAME
            + ".TodosActivity.INTENT_EXTRA_USER_ID";
    public static final String INTENT_EXTRA_USER_NAME = Foursquared.PACKAGE_NAME
            + ".TodosActivity.INTENT_EXTRA_USER_NAME";
    
    private static final int ACTIVITY_TIP = 500;
    
    private StateHolder mStateHolder;
    private TodosListAdapter mListAdapter;
    private SearchLocationObserver mSearchLocationObserver = new SearchLocationObserver();
    private View mLayoutEmpty;
    
    private static final int MENU_REFRESH = 0;


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
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));
        
        Object retained = getLastNonConfigurationInstance();
        if (retained != null && retained instanceof StateHolder) {
            mStateHolder = (StateHolder) retained;
            mStateHolder.setActivity(this);
        } else {
            // Optional user id and username, if not present, will be null and default to 
            // logged-in user.
            mStateHolder = new StateHolder(
                    getIntent().getStringExtra(INTENT_EXTRA_USER_ID),
                    getIntent().getStringExtra(INTENT_EXTRA_USER_NAME));
            mStateHolder.setRecentOnly(false);
        }
        
        ensureUi();
        
        // Nearby todos is shown first by default so auto-fetch it if necessary.
        // Nearby is the right button, not the left one, which is a bit strange
        // but this was a design req.
        if (!mStateHolder.getRanOnceTodosNearby()) {
            mStateHolder.startTaskTodos(this, false);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();

        ((Foursquared) getApplication()).requestLocationUpdates(mSearchLocationObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        
        ((Foursquared) getApplication()).removeLocationUpdates(mSearchLocationObserver);
        
        if (isFinishing()) {
            mStateHolder.cancelTasks();
            mListAdapter.removeObserver();
            unregisterReceiver(mLoggedOutReceiver);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivity(null);
        return mStateHolder;
    }

    private void ensureUi() {
        LayoutInflater inflater = LayoutInflater.from(this);
        
        setTitle(getString(R.string.todos_activity_title, mStateHolder.getUsername()));
        
        mLayoutEmpty = inflater.inflate(
                R.layout.todos_activity_empty, null);     
        mLayoutEmpty.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        
        
        mListAdapter = new TodosListAdapter(this, 
            ((Foursquared) getApplication()).getRemoteResourceManager());
        if (mStateHolder.getRecentOnly()) {
            mListAdapter.setGroup(mStateHolder.getTodosRecent());
            if (mStateHolder.getTodosRecent().size() == 0) {
                if (mStateHolder.getRanOnceTodosRecent()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        } else {
            mListAdapter.setGroup(mStateHolder.getTodosNearby());
            if (mStateHolder.getTodosNearby().size() == 0) {
                if (mStateHolder.getRanOnceTodosNearby()) {
                    setEmptyView(mLayoutEmpty);
                } else {
                    setLoadingView();
                }
            }
        }
        
        
        SegmentedButton buttons = getHeaderButton();
        buttons.clearButtons();
        buttons.addButtons(
                getString(R.string.todos_activity_btn_recent),
                getString(R.string.todos_activity_btn_nearby));
        if (mStateHolder.getRecentOnly()) {
            buttons.setPushedButtonIndex(0);
        } else {
            buttons.setPushedButtonIndex(1);
        }
        
        buttons.setOnClickListener(new OnClickListenerSegmentedButton() {
            @Override
            public void onClick(int index) {
                if (index == 0) {
                    mStateHolder.setRecentOnly(true);
                    mListAdapter.setGroup(mStateHolder.getTodosRecent());
                    if (mStateHolder.getTodosRecent().size() < 1) {
                        if (mStateHolder.getRanOnceTodosRecent()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTaskTodos(TodosActivity.this, true);
                        }
                    }
                } else {
                    mStateHolder.setRecentOnly(false);
                    mListAdapter.setGroup(mStateHolder.getTodosNearby());
                    if (mStateHolder.getTodosNearby().size() < 1) {
                        if (mStateHolder.getRanOnceTodosNearby()) {
                            setEmptyView(mLayoutEmpty);
                        } else {
                            setLoadingView();
                            mStateHolder.startTaskTodos(TodosActivity.this, false);
                        }
                    }
                }
                
                mListAdapter.notifyDataSetChanged();
                getListView().setSelection(0);
            }
        });

        ListView listView = getListView();
        listView.setAdapter(mListAdapter);
        listView.setSmoothScrollbarEnabled(false);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Todo todo = (Todo) parent.getAdapter().getItem(position);
                if (todo.getTip() != null) {
                    Intent intent = new Intent(TodosActivity.this, TipActivity.class);
                    intent.putExtra(TipActivity.EXTRA_TIP_PARCEL, todo.getTip());
                    startActivityForResult(intent, ACTIVITY_TIP);
                }
            }
        });
        
        if (mStateHolder.getIsRunningTaskTodosRecent() || 
            mStateHolder.getIsRunningTaskTodosNearby()) {
            setProgressBarIndeterminateVisibility(true);
        } else {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.refresh)
            .setIcon(R.drawable.ic_menu_refresh);
        MenuUtils.addPreferencesToMenu(this, menu);
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                mStateHolder.startTaskTodos(this, mStateHolder.getRecentOnly());
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	// We ignore the returned to-do (if any). We search for any to-dos in our
    	// state holder by the linked tip ID for update.
    	if (requestCode == ACTIVITY_TIP && resultCode == Activity.RESULT_OK) {
        	if (data.hasExtra(TipActivity.EXTRA_TIP_RETURNED)) {
        		updateTodo((Tip)data.getParcelableExtra(TipActivity.EXTRA_TIP_RETURNED));
        	}
    	}
    }
    
    private void updateTodo(Tip tip) {
		mStateHolder.updateTodo(tip);
		mListAdapter.notifyDataSetInvalidated();
    }
    
    private void onStartTaskTodos() {
        if (mListAdapter != null) {
            if (mStateHolder.getRecentOnly()) {
                mStateHolder.setIsRunningTaskTodosRecent(true);
                mListAdapter.setGroup(mStateHolder.getTodosRecent());
            } else {
                mStateHolder.setIsRunningTaskTodosNearby(true);
                mListAdapter.setGroup(mStateHolder.getTodosNearby());
            }
            mListAdapter.notifyDataSetChanged();
        }

        setProgressBarIndeterminateVisibility(true);
        setLoadingView();
    }
    
    private void onTaskTodosComplete(Group<Todo> group, boolean recentOnly, Exception ex) {
        SegmentedButton buttons = getHeaderButton();
        
        boolean update = false;
        if (group != null) {
            if (recentOnly) {
                mStateHolder.setTodosRecent(group);
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getTodosRecent());
                    update = true;
                }
            } else {
                mStateHolder.setTodosNearby(group);
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getTodosNearby());
                    update = true;
                }
            }
        }
        else {
            if (recentOnly) {
                mStateHolder.setTodosRecent(new Group<Todo>());
                if (buttons.getSelectedButtonIndex() == 0) {
                    mListAdapter.setGroup(mStateHolder.getTodosRecent());
                    update = true;
                }
            } else {
                mStateHolder.setTodosNearby(new Group<Todo>());
                if (buttons.getSelectedButtonIndex() == 1) {
                    mListAdapter.setGroup(mStateHolder.getTodosNearby());
                    update = true;
                }
            }
            
            NotificationsUtil.ToastReasonForFailure(this, ex);
        }
        
        if (recentOnly) {
            mStateHolder.setIsRunningTaskTodosRecent(false);
            mStateHolder.setRanOnceTodosRecent(true);
            if (mStateHolder.getTodosRecent().size() == 0 && 
                    buttons.getSelectedButtonIndex() == 0) {
                setEmptyView(mLayoutEmpty);
            }
        } else {
            mStateHolder.setIsRunningTaskTodosNearby(false);
            mStateHolder.setRanOnceTodosNearby(true);
            if (mStateHolder.getTodosNearby().size() == 0 &&
                    buttons.getSelectedButtonIndex() == 1) {
                setEmptyView(mLayoutEmpty);
            }
        } 
        
        if (update) {
            mListAdapter.notifyDataSetChanged();
            getListView().setSelection(0);
        }
        
        if (!mStateHolder.getIsRunningTaskTodosRecent() &&
            !mStateHolder.getIsRunningTaskTodosNearby()) {
            setProgressBarIndeterminateVisibility(false);
        }
    }
    
    /**
     * Gets friends of the current user we're working for.
     */
    private static class TaskTodos extends AsyncTask<Void, Void, Group<Todo>> {

        private String mUserId;
        private TodosActivity mActivity;
        private boolean mRecentOnly;
        private Exception mReason;

        public TaskTodos(TodosActivity activity, String userId, boolean friendsOnly) {
            mActivity = activity;
            mUserId = userId;
            mRecentOnly = friendsOnly;
        }
        
        @Override
        protected void onPreExecute() {
            mActivity.onStartTaskTodos();
        }

        @Override
        protected Group<Todo> doInBackground(Void... params) {
            try {
                Foursquared foursquared = (Foursquared) mActivity.getApplication();
                Foursquare foursquare = foursquared.getFoursquare();

                Location loc = foursquared.getLastKnownLocation();
                if (loc == null) {
                    try { Thread.sleep(3000); } catch (InterruptedException ex) {}
                    loc = foursquared.getLastKnownLocation();
                    if (loc == null) {
                        throw new FoursquareException("Your location could not be determined!");
                    }
                } 
                
                return foursquare.todos(
                        LocationUtils.createFoursquareLocation(loc), 
                        mUserId,
                        mRecentOnly, 
                        !mRecentOnly,
                        30);
            } catch (Exception e) {
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Group<Todo> todos) {
            if (mActivity != null) {
                mActivity.onTaskTodosComplete(todos, mRecentOnly, mReason);
            }
        }

        @Override
        protected void onCancelled() {
            if (mActivity != null) {
                mActivity.onTaskTodosComplete(null, mRecentOnly, mReason);
            }
        }
        
        public void setActivity(TodosActivity activity) {
            mActivity = activity;
        }
    }
    
     
    private static class StateHolder {
        
        private Group<Todo> mTodosRecent;
        private Group<Todo> mTodosNearby;
        private boolean mIsRunningTaskTodosRecent;
        private boolean mIsRunningTaskTodosNearby;
        private boolean mRecentOnly;
        private boolean mRanOnceTodosRecent;
        private boolean mRanOnceTodosNearby;
        private TaskTodos mTaskTodosRecent;
        private TaskTodos mTaskTodosNearby;
        private String mUserId;
        private String mUsername;
        
        
        public StateHolder(String userId, String username) {
            mIsRunningTaskTodosRecent = false;
            mIsRunningTaskTodosNearby = false;
            mRanOnceTodosRecent = false;
            mRanOnceTodosNearby = false;
            mTodosRecent = new Group<Todo>();
            mTodosNearby = new Group<Todo>();
            mRecentOnly = false;
            mUserId = userId;
            mUsername = username;
        }
        
        public String getUsername() {
            return mUsername;
        }
        
        public Group<Todo> getTodosRecent() {
            return mTodosRecent;
        }
        
        public void setTodosRecent(Group<Todo> todosRecent) {
            mTodosRecent = todosRecent;
        }
        
        public Group<Todo> getTodosNearby() {
            return mTodosNearby;
        }
        
        public void setTodosNearby(Group<Todo> todosNearby) {
            mTodosNearby = todosNearby;
        }
        
        public void startTaskTodos(TodosActivity activity,
                                   boolean recentOnly) {
            if (recentOnly) {
                if (mIsRunningTaskTodosRecent) {
                    return;
                }
                mIsRunningTaskTodosRecent = true;
                mTaskTodosRecent = new TaskTodos(activity, mUserId, recentOnly);
                mTaskTodosRecent.execute();
            } else {
                if (mIsRunningTaskTodosNearby) {
                    return;
                }
                mIsRunningTaskTodosNearby = true;
                mTaskTodosNearby = new TaskTodos(activity, mUserId, recentOnly);
                mTaskTodosNearby.execute();
            }
        }

        public void setActivity(TodosActivity activity) {
            if (mTaskTodosRecent != null) {
                mTaskTodosRecent.setActivity(activity);
            }
            if (mTaskTodosNearby != null) {
                mTaskTodosNearby.setActivity(activity);
            }
        }

        public boolean getIsRunningTaskTodosRecent() {
            return mIsRunningTaskTodosRecent;
        }
        
        public void setIsRunningTaskTodosRecent(boolean isRunning) {
            mIsRunningTaskTodosRecent = isRunning;
        }
        
        public boolean getIsRunningTaskTodosNearby() {
            return mIsRunningTaskTodosNearby;
        }
        
        public void setIsRunningTaskTodosNearby(boolean isRunning) {
            mIsRunningTaskTodosNearby = isRunning;
        }

        public void cancelTasks() {
            if (mTaskTodosRecent != null) {
                mTaskTodosRecent.setActivity(null);
                mTaskTodosRecent.cancel(true);
            }
            if (mTaskTodosNearby != null) {
                mTaskTodosNearby.setActivity(null);
                mTaskTodosNearby.cancel(true);
            }
        }
        
        public boolean getRecentOnly() {
            return mRecentOnly;
        }
        
        public void setRecentOnly(boolean recentOnly) {
            mRecentOnly = recentOnly;
        }
        
        public boolean getRanOnceTodosRecent() {
            return mRanOnceTodosRecent;
        }
        
        public void setRanOnceTodosRecent(boolean ranOnce) {
            mRanOnceTodosRecent = ranOnce;
        }
        
        public boolean getRanOnceTodosNearby() {
            return mRanOnceTodosNearby;
        }
        
        public void setRanOnceTodosNearby(boolean ranOnce) {
            mRanOnceTodosNearby = ranOnce;
        }
        
        public void updateTodo(Tip tip) {
            updateTodoFromArray(tip, mTodosRecent);
            updateTodoFromArray(tip, mTodosNearby);
        }
        
        private void updateTodoFromArray(Tip tip, Group<Todo> target) {
            for (int i = 0, m = target.size(); i < m; i++) {
                Todo todo = target.get(i);
                if (todo.getTip() != null) { // Fix for old garbage todos/tips from the API.
                    if (todo.getTip().getId().equals(tip.getId())) {
                        if (mUserId == null) {
                            // Activity is operating on logged-in user, only removing todos
                            // from the list, don't have to worry about updating states.
                            if (!TipUtils.isTodo(tip)) {
                                target.remove(todo);
                            }
                        } else {
                            // Activity is operating on another user, so just update the status
                            // of the tip within the todo.
                            todo.getTip().setStatus(tip.getStatus());
                        }
                        break;
                    }
                }
            }
        }
    }
    
    /** 
     * This is really just a dummy observer to get the GPS running
     * since this is the new splash page. After getting a fix, we
     * might want to stop registering this observer thereafter so
     * it doesn't annoy the user too much.
     */
    private class SearchLocationObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
        }
    }
}
