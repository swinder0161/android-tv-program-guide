/*
 * Copyright (c) 2020, Egeniq
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

package com.egeniq.androidtvprogramguide.row;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.egeniq.androidtvprogramguide.ProgramGuideHolder;
import com.egeniq.androidtvprogramguide.ProgramGuideListAdapter;
import com.egeniq.androidtvprogramguide.ProgramGuideManager;
import com.egeniq.androidtvprogramguide.R;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts the [ProgramGuideListAdapter] list to the body of the program guide table.
 */
public class ProgramGuideRowAdapter
        extends RecyclerView.Adapter<ProgramGuideRowAdapter.ProgramRowViewHolder>
        implements ProgramGuideManager.Listener {
    private static final String TAG = ProgramGuideRowAdapter.class.getName();
    private final Context context;
    private final ProgramGuideHolder<?> programGuideHolder;
    private final ProgramGuideManager<?> programManager;
    private final List<RecyclerView.Adapter<?>> programListAdapters = new ArrayList<>();
    private final RecyclerView.RecycledViewPool recycledViewPool;

    public ProgramGuideRowAdapter(Context _context, ProgramGuideHolder<?> _programGuideHolder) {
        super();
        context = _context;
        programGuideHolder = _programGuideHolder;
        programManager = programGuideHolder.getProgramGuideManager();
        recycledViewPool = new RecyclerView.RecycledViewPool();
        recycledViewPool.setMaxRecycledViews(R.layout.programguide_item_row,
                context.getResources().getInteger(R.integer.programguide_max_recycled_view_pool_table_item));
        update();
    }

    @SuppressLint({"NotifyDataSetChanged"})
    public void update() {
        programListAdapters.clear();
        final int channelCount = programManager.getChannelCount();
        for (int i = 0; i < channelCount; i++) {
            final ProgramGuideListAdapter<?> listAdapter = new ProgramGuideListAdapter<>(context.getResources(), programGuideHolder, i);
            programListAdapters.add(listAdapter);
        }
        Log.i(TAG, "Updating program guide with " + channelCount + " channels.");
        notifyDataSetChanged();
    }

    public Integer updateProgram(ProgramGuideSchedule<?> program) {
        // Find the match in the row adapters
        for(int index = 0; index < programListAdapters.size(); index++) {
            if (((ProgramGuideListAdapter<?>)programListAdapters.get(index)).updateProgram(program)) {
                return index;
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return programListAdapters.size();
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.programguide_item_row;
    }

    @Override
    public void onBindViewHolder(ProgramRowViewHolder holder, int position) {
        holder.onBind(position, programManager, programListAdapters, programGuideHolder);
    }

    @NonNull
    @Override
    public ProgramRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        final ProgramGuideRowGridView gridView = itemView.findViewById(R.id.row);
        gridView.setRecycledViewPool(recycledViewPool);
        return new ProgramRowViewHolder(itemView);
    }

    @Override
    public void onTimeRangeUpdated() {
        // Do nothing
    }

    @Override
    public void onSchedulesUpdated() {
        // Do nothing
    }

    public static class ProgramRowViewHolder extends RecyclerView.ViewHolder {
        private final ProgramGuideRowGridView rowGridView;
        private final TextView channelNameView;
        private final TextView channelNumberView;
        private final ImageView channelLogoView;

        public ProgramRowViewHolder(View itemView) {
            super(itemView);
            ViewGroup container = (ViewGroup) itemView;
            rowGridView = container.findViewById(R.id.row);
            channelNameView = container.findViewById(R.id.programguide_channel_name);
            channelNumberView = container.findViewById(R.id.programguide_channel_number);
            channelLogoView = container.findViewById(R.id.programguide_channel_logo);
            ViewGroup channelContainer = container.findViewById(R.id.programguide_channel_container);
            channelContainer.getViewTreeObserver().addOnGlobalFocusChangeListener((v1, v2) ->
                    channelContainer.setActivated(rowGridView.hasFocus()));
        }

        public void onBind(int position, ProgramGuideManager<?> programManager,
                           List<RecyclerView.Adapter<?>> programListAdapters,
                           ProgramGuideHolder<?> programGuideHolder) {
            onBindChannel(programManager.getChannel(position));
            rowGridView.swapAdapter(programListAdapters.get(position), true);
            rowGridView.setProgramGuideFragment(programGuideHolder);
            rowGridView.setChannel(programManager.getChannel(position));
            rowGridView.resetScroll(programGuideHolder.getTimelineRowScrollOffset());
        }

        private void onBindChannel(ProgramGuideChannel channel) {
            if (channel == null) {
                channelNameView.setVisibility(View.GONE);
                channelNumberView.setVisibility(View.GONE);
                channelLogoView.setVisibility(View.GONE);
                return;
            }
            final String imageUrl = channel.getImageUrl();
            if (imageUrl == null) {
                channelLogoView.setVisibility(View.GONE);
            } else {
                Glide.with(channelLogoView).load(imageUrl).fitCenter().into(channelLogoView);
                channelLogoView.setVisibility(View.VISIBLE);
            }

            channelNameView.setText(channel.getName());
            channelNameView.setVisibility(View.VISIBLE);

            channelNumberView.setText("" + channel.getNumber());
            channelNumberView.setVisibility(View.VISIBLE);
        }

        public void updateLayout() {
            rowGridView.post(rowGridView::updateChildVisibleArea);
        }
    }
}
