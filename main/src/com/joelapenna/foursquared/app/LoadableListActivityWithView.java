/**
 * Copyright 2010 Mark Wyszomierski
 */

package com.joelapenna.foursquared.app;

import com.joelapenna.foursquared.R;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

/**
 * This is pretty much a direct copy of LoadableListActivity. It just gives the caller
 * a chance to set their own view for the empty state. This is used by FriendsActivity
 * to show a button like 'Find some friends!' when the list is empty (in the case that
 * they are a new user and have no friends initially).
 * 
 * By default, loadable_list_activity_with_view is used as the intial empty view with
 * a progress bar and textview description. The owner can then call setEmptyView()
 * with their own view to show if there are no results.
 *  
 * @date April 25, 2010
 * @author Mark Wyszomierski (markww@gmail.com)
 */
public class LoadableListActivityWithView extends ListActivity {

    private LinearLayout mLayoutHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.loadable_list_activity_with_view);
        mLayoutHeader = (LinearLayout)findViewById(R.id.header);
        
        getListView().setDividerHeight(0);
    }

    public void setEmptyView(View view) {
        LinearLayout parent = (LinearLayout)findViewById(R.id.loadableListHolder);

        parent.getChildAt(0).setVisibility(View.GONE);
        if (parent.getChildCount() > 1) {
            parent.removeViewAt(1);
        }
        
        parent.addView(view);
    }
 
    public void setLoadingView() {
        LinearLayout parent = (LinearLayout)findViewById(R.id.loadableListHolder);

        if (parent.getChildCount() > 1) {
            parent.removeViewAt(1);
        }
        
        parent.getChildAt(0).setVisibility(View.VISIBLE);
    }
    
    public int getNoSearchResultsStringId() {
        return R.string.no_search_results;
    }
    
    public LinearLayout getHeaderLayout() {
        return mLayoutHeader;
    }
}
