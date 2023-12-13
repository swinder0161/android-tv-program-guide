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

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Range;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.egeniq.androidtvprogramguide.ProgramGuideGridView;
import com.egeniq.androidtvprogramguide.ProgramGuideHolder;
import com.egeniq.androidtvprogramguide.ProgramGuideManager;
import com.egeniq.androidtvprogramguide.R;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView;
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimelineGridView;
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil;

import java.util.concurrent.TimeUnit;

public final class ProgramGuideRowGridView extends ProgramGuideTimelineGridView {
    private static final long ONE_HOUR_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final long HALF_HOUR_MILLIS = ONE_HOUR_MILLIS / 2;
    private boolean keepFocusToCurrentProgram = false;
    private ProgramGuideHolder<?> programGuideHolder;
    private ProgramGuideManager<?> programGuideManager;
    private ProgramGuideChannel channel = null;
    private final int minimumStickOutWidth;
    private final ViewTreeObserver.OnGlobalLayoutListener
            layoutListener;

    public ProgramGuideRowGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        minimumStickOutWidth = getResources().getDimensionPixelOffset(R.dimen.programguide_minimum_item_width_sticking_out_behind_channel_column);
        layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                updateChildVisibleArea();
            }
        };
    }

    public ProgramGuideRowGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgramGuideRowGridView(Context context) {
        this(context, null);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        final ProgramGuideItemView<?> itemView = (ProgramGuideItemView<?>)child;
        if (getLeft() <= itemView.getRight() && itemView.getLeft() <= getRight()) {
            itemView.updateVisibleArea();
        }
    }

    @Override
    public void onScrolled(int dx, int dy) {
        // Remove callback to prevent updateChildVisibleArea being called twice.
        getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        super.onScrolled(dx, dy);
        updateChildVisibleArea();
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private boolean isDirectionStart(int direction) {
        return getLayoutDirection() == View.LAYOUT_DIRECTION_LTR ?
                direction == View.FOCUS_LEFT : direction == View.FOCUS_RIGHT;
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private boolean isDirectionEnd(int direction) {
        return getLayoutDirection() == View.LAYOUT_DIRECTION_LTR ?
                direction == View.FOCUS_RIGHT : direction == View.FOCUS_LEFT;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        final ProgramGuideSchedule<?> focusedEntry = ((ProgramGuideItemView<?>)focused).schedule;
        if (focusedEntry == null) return super.focusSearch(focused, direction);
        final long fromMillis = programGuideManager.getFromUtcMillis();
        final long toMillis = programGuideManager.getToUtcMillis();

        if (isDirectionStart(direction) || direction == View.FOCUS_BACKWARD) {
            if (focusedEntry.startsAtMillis < fromMillis) {
                // The current entry starts outside of the view; Align or scroll to the left.
                scrollByTime(Math.max(-ONE_HOUR_MILLIS, focusedEntry.startsAtMillis - fromMillis));
                return focused;
            }
        } else if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
            if (focusedEntry.endsAtMillis > toMillis) {
                // The current entry ends outside of the view; Scroll to the right (or left, if RTL).
                scrollByTime(ONE_HOUR_MILLIS);
                return focused;
            }
        }

        final View target = super.focusSearch(focused, direction);
        if (!(target instanceof ProgramGuideItemView<?>)) {
            if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
                if (focusedEntry.endsAtMillis != toMillis) {
                    // The focused entry is the last entry; Align to the right edge.
                    scrollByTime(focusedEntry.endsAtMillis - toMillis);
                    return focused;
                }
            }
            return target;
        }
        final ProgramGuideSchedule<?> targetEntry = ((ProgramGuideItemView<?>) target).schedule;
        if (targetEntry == null) return target;
        if (isDirectionStart(direction) || direction == View.FOCUS_BACKWARD) {
            if (targetEntry.startsAtMillis < fromMillis && targetEntry.endsAtMillis < fromMillis + HALF_HOUR_MILLIS) {
                // The target entry starts outside the view; Align or scroll to the left (or right, on RTL).
                scrollByTime(Math.max(-ONE_HOUR_MILLIS, targetEntry.startsAtMillis - fromMillis));
            }
        } else if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
            if (targetEntry.startsAtMillis > fromMillis + ONE_HOUR_MILLIS + HALF_HOUR_MILLIS) {
                // The target entry starts outside the view; Align or scroll to the right (or left, on RTL).
                scrollByTime(Math.min(ONE_HOUR_MILLIS, targetEntry.startsAtMillis - fromMillis - ONE_HOUR_MILLIS));
            }
        }

        return target;
    }

    private void scrollByTime(long timeToScroll) {
        programGuideManager.shiftTime(timeToScroll);
    }

    @Override
    public void onChildDetachedFromWindow(View child) {
        if (child.hasFocus()) {
            // Focused view can be detached only if it's updated.
            ProgramGuideSchedule<?> entry = ((ProgramGuideItemView<?>)child).schedule;
            if (entry == null || entry.program == null) {
                // The focus is lost due to information loaded. Requests focus immediately.
                // (Because this entry is detached after real entries attached, we can't take
                // the below approach to resume focus on entry being attached.)
                post(this::requestFocus);
            } else if (entry.isCurrentProgram()) {
                // Current program is visible in the guide.
                // Updated entries including current program's will be attached again soon
                // so give focus back in onChildAttachedToWindow().
                keepFocusToCurrentProgram = true;
            }
        }
        super.onChildDetachedFromWindow(child);
    }

    @Override
    public void onChildAttachedToWindow(@NonNull View child) {
        super.onChildAttachedToWindow(child);
        if (keepFocusToCurrentProgram) {
            final ProgramGuideSchedule<?> entry = ((ProgramGuideItemView<?>)child).schedule;
            if (entry != null && entry.isCurrentProgram()) {
                keepFocusToCurrentProgram = false;
                post(this::requestFocus);
            }
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        // This part is required to intercept the default child focus behavior.
        // When focus is coming from the top, and there's an item hiding behind the left channel column, default focus behavior
        // is to select that one, because it is the closest to the previously focused element.
        // But because this item is behind the channel column, it is not visible to the user that it is selected
        // So we check for this occurence, and select the next item if possible.
        final boolean gridHasFocus = programGuideHolder.getProgramGuideGrid().hasFocus();
        if (child == null) {
            super.requestChildFocus(null, focused);
            return;
        }

        if (!gridHasFocus) {
            View it = findNextFocusableChild(child);
            if (it != null) {
                super.requestChildFocus(child, focused);
                it.requestFocus();
                // This skipping is required because in some weird way the global focus change listener gets the event
                // in the wrong order, so first the replacing item, then the old one.
                // By skipping the second one, only the (correct) replacing item will be notfied to the listeners
                programGuideHolder.getProgramGuideGrid().markCorrectChild(it);
                return;
            }
        }
        super.requestChildFocus(child, focused);
    }

    private View findNextFocusableChild(View child) {
        //Check if child is focusable and return
        final int leftEdge = child.getLeft();
        final int rightEdge = child.getLeft() + child.getWidth();
        final Integer viewPosition = getLayoutManager() == null ? null : getLayoutManager().getPosition(child);

        if (getLayoutDirection() == LAYOUT_DIRECTION_LTR && (leftEdge >= programGuideHolder.getProgramGuideGrid().getFocusRange().getLower() ||
                rightEdge >= programGuideHolder.getProgramGuideGrid().getFocusRange().getLower() + minimumStickOutWidth)) {
            return child;
        } else if (getLayoutDirection() == LAYOUT_DIRECTION_RTL && (rightEdge <= programGuideHolder.getProgramGuideGrid().getFocusRange().getUpper() ||
                leftEdge <= programGuideHolder.getProgramGuideGrid().getFocusRange().getUpper() - minimumStickOutWidth)) {
            // RTL mode
            return child;
        }

        //if not check if we have a next child and recursively test it again
        if (viewPosition != null && viewPosition >= 0 && viewPosition < (getLayoutManager() == null ? -1 : getLayoutManager().getItemCount())) {
            final View nextChild = getLayoutManager() == null ? null : getLayoutManager().findViewByPosition(viewPosition + 1);
            if (nextChild != null) {
                return findNextFocusableChild(nextChild);
            }
        }

        return null;
    }

    @Override
    public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        final ProgramGuideGridView<?> programGrid = programGuideHolder.getProgramGuideGrid();
        // Give focus according to the previous focused range
        final Range<Integer> focusRange = programGrid.getFocusRange();
        final View nextFocus = ProgramGuideUtil.findNextFocusedProgram(this, focusRange.getLower(),
                focusRange.getUpper(), programGrid.isKeepCurrentProgramFocused());

        if (nextFocus != null) {
            return nextFocus.requestFocus();
        }
        final boolean result = super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        if (!result) {
            // The default focus search logic of LeanbackLibrary is sometimes failed.
            // As a fallback solution, we request focus to the first focusable view.
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                if (child.isShown() && child.hasFocusable()) {
                    return child.requestFocus();
                }
            }
        }
        return result;
    }

    public void setChannel(ProgramGuideChannel channelToSet) {
        channel = channelToSet;
    }

    /** Sets the instance of [ProgramGuideHolder]  */
    public void setProgramGuideFragment(ProgramGuideHolder<?> fragment) {
        programGuideHolder = fragment;
        programGuideManager = programGuideHolder.getProgramGuideManager();
    }

    /** Resets the scroll with the initial offset `currentScrollOffset`.  */
    public void resetScroll(int scrollOffset) {
        final ProgramGuideChannel channel = this.channel;
        final long startTime =
                ProgramGuideUtil.convertPixelToMillis(scrollOffset) + programGuideManager.getStartTime();
        final int position = (channel == null) ? -1 : programGuideManager.getProgramIndexAtTime(channel.getId(), startTime);

        if (position < 0) {
            if(getLayoutManager() != null) getLayoutManager().scrollToPosition(0);
        } else if (channel != null && channel.getId() != null) {
            final String slug = channel.getId();
            final ProgramGuideSchedule<?> entry = programGuideManager.getScheduleForChannelIdAndIndex(slug, position);
            final int offset = ProgramGuideUtil.convertMillisToPixel(programGuideManager.getStartTime(), entry.startsAtMillis) - scrollOffset;
            if(getLayoutManager() != null) ((LinearLayoutManager)getLayoutManager()).scrollToPositionWithOffset(position, offset);
            // Workaround to b/31598505. When a program's duration is too long,
            // RecyclerView.onScrolled() will not be called after scrollToPositionWithOffset().
            // Therefore we have to update children's visible areas by ourselves in this case.
            // Since scrollToPositionWithOffset() will call requestLayout(), we can listen to this
            // behavior to ensure program items' visible areas are correctly updated after layouts
            // are adjusted, i.e., scrolling is over.
            getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        }
    }

    public void updateChildVisibleArea() {
        for (int i = 0; i < getChildCount(); i++) {
            final ProgramGuideItemView<?> child = (ProgramGuideItemView<?>)getChildAt(i);
            if (getLeft() < child.getRight() && child.getLeft() < getRight()) {
                child.updateVisibleArea();
            }
        }
    }
}
