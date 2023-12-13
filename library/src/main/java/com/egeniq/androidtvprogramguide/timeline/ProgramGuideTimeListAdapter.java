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

package com.egeniq.androidtvprogramguide.timeline;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.egeniq.androidtvprogramguide.R;
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.concurrent.TimeUnit;

/**
 * Adapts the time range from ProgramManager to the timeline header row of the program guide
 * table.
 */
public class ProgramGuideTimeListAdapter
        extends RecyclerView.Adapter<ProgramGuideTimeListAdapter.TimeViewHolder> {
    private static final long TIME_UNIT_MS = TimeUnit.MINUTES.toMillis(30);
    private static int rowHeaderOverlapping = 0;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    // Nearest half hour at or before the start time.
    private long startUtcMs = 0;
    private int timelineAdjustmentPixels = 0;
    private final ZoneId displayTimezone;

    public ProgramGuideTimeListAdapter(Resources res, ZoneId _displayTimezone) {
        super();
        displayTimezone = _displayTimezone;
        if (rowHeaderOverlapping == 0) {
            rowHeaderOverlapping =
                    Math.abs(res.getDimensionPixelOffset(R.dimen.programguide_time_row_negative_margin));
        }
    }
    @SuppressLint({"NotifyDataSetChanged"})
    public void update(long startTimeMs, int timelineAdjustmentPx) {
        startUtcMs = startTimeMs;
        timelineAdjustmentPixels = timelineAdjustmentPx;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.programguide_item_time;
    }

    @Override
    public void onBindViewHolder(TimeViewHolder holder, int position) {
        final long startTime = startUtcMs + (long)position * TIME_UNIT_MS;
        final long endTime = startTime + TIME_UNIT_MS;

        final View itemView = holder.itemView;
        final ZonedDateTime timeDate = Instant.ofEpochMilli(startTime).atZone(displayTimezone);
        final String timeString = TIME_FORMATTER.format(timeDate);
        ((TextView)itemView).setText((CharSequence)timeString);

        final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams)((TextView)itemView).getLayoutParams();
        lp.width = ProgramGuideUtil.convertMillisToPixel(startTime, endTime);
        if (position == 0) {
            // Adjust width for the first entry to make the item starts from the fading edge.
            lp.setMarginStart(rowHeaderOverlapping - lp.width / 2 - timelineAdjustmentPixels);
        } else {
            lp.setMarginStart(0);
        }
        ((TextView)itemView).setLayoutParams((ViewGroup.LayoutParams)lp);
    }

    @NonNull
    @Override
    public TimeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new TimeViewHolder(itemView);
    }

    public static final class TimeViewHolder extends RecyclerView.ViewHolder {
        public TimeViewHolder(View itemView) {
            super(itemView);
        }
    }
}
