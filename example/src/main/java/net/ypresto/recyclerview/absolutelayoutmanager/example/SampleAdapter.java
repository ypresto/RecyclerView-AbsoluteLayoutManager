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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SampleAdapter extends RecyclerView.Adapter<SampleAdapter.SampleViewHolder> {
    private int mCount = 3;

    @Override
    public SampleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_cell, parent, false);
        return new SampleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SampleViewHolder holder, int position) {
        int color = getColorForPosition(position);
        holder.itemView.setBackgroundColor(color);
    }

    private int getColorForPosition(int position) {
        switch (position % 3) {
            case 0:
                return 0xffff0000; // red
            case 1:
                return 0xff00ff00; // green
            case 2:
                return 0xff0000ff; // blue
            default:
                return 0xffffffff; // white
        }
    }

    @Override
    public int getItemCount() {
        return mCount;
    }

    public int getCount() {
        return mCount;
    }

    public void setCount(int count) {
        mCount = count;
    }

    public static class SampleViewHolder extends RecyclerView.ViewHolder {
        public SampleViewHolder(View itemView) {
            super(itemView);
        }
    }
}
