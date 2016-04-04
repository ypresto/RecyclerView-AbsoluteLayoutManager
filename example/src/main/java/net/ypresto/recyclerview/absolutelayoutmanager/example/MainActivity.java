/*
 * Copyright (C) 2015 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ypresto.recyclerview.absolutelayoutmanager.example;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager;

import java.lang.reflect.Field;

public class MainActivity extends AppCompatActivity {
    public static final String STATE_ITEM_COUNT = "itemCount";
    private SampleAdapter mAdapter;
    private AbsoluteLayoutManager mAbsoluteLayoutManager;
    private GridLayoutManager mGridLayoutManager;
    private RecyclerView mRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAdapter.setItemCount(mAdapter.getItemCount() + 1);
                mAdapter.notifyDataSetChanged();
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mAdapter = new SampleAdapter();
        if (savedInstanceState != null) {
            mAdapter.setItemCount(savedInstanceState.getInt(STATE_ITEM_COUNT));
        }
        mRecyclerView.setAdapter(mAdapter);
        mAbsoluteLayoutManager = new AbsoluteLayoutManager(new SquareVerticalLayoutProvider());
        setDebugFlag(mAbsoluteLayoutManager);
        mRecyclerView.setLayoutManager(mAbsoluteLayoutManager);
        mGridLayoutManager = new GridLayoutManager(this, 3);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ITEM_COUNT, mAdapter.getItemCount());
    }

    @SuppressWarnings("TryWithIdenticalCatches") // As it requires API >= 17 for reflection classes.
    private void setDebugFlag(AbsoluteLayoutManager layoutManager) {
        Field debugFlag;
        try {
            debugFlag = AbsoluteLayoutManager.class.getDeclaredField("DEBUG");
            debugFlag.setAccessible(true);
            debugFlag.setBoolean(layoutManager, true);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_scroll_to_bottom_minus_1:
                ((RecyclerView) findViewById(R.id.recycler_view)).smoothScrollToPosition(mAdapter.getItemCount() - 2);
                return true;
            case R.id.action_change_layout:
                if (mRecyclerView.getLayoutManager() == mAbsoluteLayoutManager) {
                    mRecyclerView.setLayoutManager(mGridLayoutManager);
                } else {
                    mRecyclerView.setLayoutManager(mAbsoluteLayoutManager);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
