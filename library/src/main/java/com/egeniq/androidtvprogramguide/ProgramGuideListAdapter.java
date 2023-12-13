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

package com.egeniq.androidtvprogramguide;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView;

public final class ProgramGuideListAdapter<T>
        extends RecyclerView.Adapter<ProgramGuideListAdapter.ProgramItemViewHolder<T>> implements ProgramGuideManager.Listener {
    private final ProgramGuideHolder<T> programGuideFragment;
    private final int channelIndex;
    private final ProgramGuideManager<T> programGuideManager;
    private final String noInfoProgramTitle;
    private String channelId = "";

    public ProgramGuideListAdapter(Resources res, ProgramGuideHolder<T> _programGuideFragment, int _channelIndex) {
        super();
        programGuideFragment = _programGuideFragment;
        channelIndex = _channelIndex;
        setHasStableIds(true);
        programGuideManager = programGuideFragment.getProgramGuideManager();
        noInfoProgramTitle = res.getString(R.string.programguide_title_no_program);
        onSchedulesUpdated();
    }

    @Override
    public void onTimeRangeUpdated() {
        // Do nothing
    }

    @Override
    @SuppressLint({"NotifyDataSetChanged"})
    public void onSchedulesUpdated() {
        final ProgramGuideChannel channel = programGuideManager.getChannel(channelIndex);
        if (channel != null) {
            channelId = channel.getId();
            notifyDataSetChanged();
        }
    }

    public boolean updateProgram(ProgramGuideSchedule<?> program) {
        for (int position = 0; position < getItemCount(); position++) {
            ProgramGuideSchedule<T> pgs = programGuideManager.getScheduleForChannelIdAndIndex(channelId, position);
            if (pgs != null && pgs.id == program.id) {
                notifyItemChanged(position);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getItemCount() {
        return programGuideManager.getSchedulesCount(channelId);
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.programguide_item_program_container;
    }

    @Override
    public long getItemId(int position) {
        ProgramGuideSchedule<T> pgs = programGuideManager.getScheduleForChannelIdAndIndex(channelId, position);
        return pgs == null ? 0 : pgs.id;
    }

    @Override
    public void onBindViewHolder(ProgramItemViewHolder<T> holder, int position) {
        final ProgramGuideSchedule<T> programGuideSchedule =
                programGuideManager.getScheduleForChannelIdAndIndex(channelId, position);
        holder.onBind(programGuideSchedule, programGuideFragment, noInfoProgramTitle);
    }

    @Override
    public void onViewRecycled(ProgramItemViewHolder holder) {
        holder.onUnbind();
    }

    @NonNull
    @Override
    public ProgramItemViewHolder<T> onCreateViewHolder(ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new ProgramItemViewHolder<>(itemView);
    }

    public static class ProgramItemViewHolder<R> extends RecyclerView.ViewHolder {
        private ProgramGuideItemView<R> programGuideItemView = null;

        public ProgramItemViewHolder(View itemView) {
            super(itemView);
            // Make all child view clip to the outline
            itemView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            itemView.setClipToOutline(true);
        }

        @SuppressWarnings("unchecked")
        public void onBind(ProgramGuideSchedule<R> schedule, ProgramGuideHolder<R> programGuideHolder, String gapTitle) {
            if (itemView == null) return;
            final ProgramGuideManager<R> programManager = programGuideHolder.getProgramGuideManager();
            programGuideItemView = (ProgramGuideItemView<R>) itemView;

            programGuideItemView.setOnClickListener(it ->
                    programGuideHolder.onScheduleClickedInternal(schedule));

            programGuideItemView.setValues(schedule, programManager.getFromUtcMillis(),
                    programManager.getToUtcMillis(), gapTitle, programGuideHolder.getDISPLAY_SHOW_PROGRESS());
        }

        public void onUnbind() {
            if (programGuideItemView == null) return;
            programGuideItemView.setOnClickListener(null);
            programGuideItemView.clearValues();
        }
    }
}
