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

package com.egeniq.androidtvprogramguide.item;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.egeniq.androidtvprogramguide.R;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil;


public class ProgramGuideItemView<T> extends FrameLayout {
    public ProgramGuideItemView(Context context) {
        this(context, null);
    }

    public ProgramGuideItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgramGuideItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ProgramGuideSchedule<T> schedule = null;
    private final int staticItemPadding =
            getResources().getDimensionPixelOffset(R.dimen.programguide_item_padding);
    private int itemTextWidth = 0;
    private int maxWidthForRipple = 0;
    private boolean preventParentRelayout = false;

    private final TextView titleView;
    private final ProgressBar progressView;

    {
        View.inflate(getContext(), R.layout.programguide_item_program, this);

        titleView = findViewById(R.id.title);
        progressView = findViewById(R.id.progress);
    }

    public void setValues(ProgramGuideSchedule<T> scheduleItem, long fromUtcMillis, long toUtcMillis,
                          String gapTitle, boolean displayProgress) {
        schedule = scheduleItem;
        final ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams != null) {
            final int spacing = getResources().getDimensionPixelSize(R.dimen.programguide_item_spacing);
            layoutParams.width = scheduleItem.width - 2 * spacing; // Here we subtract the spacing, otherwise the calculations will be wrong at other places
            // If the programme is very short, and the table width is also reduced, or the gap is enlarged,
            // there is an edge case that we could go into negative widths. This fixes that.
            if (layoutParams.width < 1) {
                layoutParams.width = 1;
            }
            setLayoutParams(layoutParams);
        }
        String title = schedule != null ? schedule.displayTitle : null;
        if (scheduleItem.isGap) {
            title = gapTitle;
            setBackgroundResource(R.drawable.programguide_gap_item_background);
            setClickable(false);
        } else {
            setBackgroundResource(R.drawable.programguide_item_program_background);
            setClickable(scheduleItem.isClickable);
        }

        title = (title == null || title.length()==0) ? getResources().getString(R.string.programguide_title_no_program) : title;
        updateText(title);
        initProgress(ProgramGuideUtil.convertMillisToPixel(
                scheduleItem.startsAtMillis, scheduleItem.endsAtMillis));
        if (displayProgress) {
            updateProgress(System.currentTimeMillis());
        } else {
            progressView.setVisibility(View.GONE);
        }

        titleView.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        itemTextWidth = titleView.getMeasuredWidth() - titleView.getPaddingLeft() - titleView.getPaddingRight();
        // Maximum width for us to use a ripple
        maxWidthForRipple = ProgramGuideUtil.convertMillisToPixel(fromUtcMillis, toUtcMillis);
    }

    private void updateText(String title) {
        titleView.setText(title);
    }

    private void initProgress(int width) {
        progressView.setMax(width);
    }

    public void updateProgress(long now) {
        if (schedule != null) {
            final boolean hasEnded = now > schedule.endsAtMillis;
            if (!schedule.isCurrentProgram()) {
                progressView.setVisibility(View.GONE);
            } else {
                progressView.setVisibility(View.VISIBLE);
                progressView.setProgress(ProgramGuideUtil.convertMillisToPixel(schedule.startsAtMillis, now));
            }

            this.setActivated(!hasEnded);
        }
    }

    /** Update programItemView to handle alignments of text. */
    public void updateVisibleArea() {
        final View parentView = (View)getParent();
        if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            layoutVisibleArea(parentView.getLeft() + parentView.getPaddingStart() - getLeft(),
                    getRight() - parentView.getRight());
        } else {
            layoutVisibleArea(parentView.getLeft() - getLeft(),
                    getRight() - parentView.getRight() + parentView.getPaddingStart());
        }
    }

    /**
     * Layout title and episode according to visible area.
     * <p>
     *
     * Here's the spec.
     * 1. Don't show text if it's shorter than 48dp.
     * 2. Try showing whole text in visible area by placing and wrapping text, but do not wrap text less than 30min.
     * 3. Episode title is visible only if title isn't multi-line.
     *
     * @param leftOffset Amount of pixels the view sticks out on the left side of the screen. If it is negative, it does not stick out.
     * @param rightOffset Amount of pixels the view sticks out on the right side of the screen. If it is negative, it does not stick out.
     */
    private void layoutVisibleArea(int leftOffset, int rightOffset) {
        final int width = schedule != null ? schedule.width : 0;
        int leftPadding = Math.max(0, leftOffset);
        int rightPadding = Math.max(0, rightOffset);
        final int minWidth = Math.min(width, itemTextWidth + 2 * staticItemPadding);
        if (leftPadding > 0 && width - leftPadding < minWidth) {
            leftPadding = Math.max(0, width - minWidth);
        }

        if (rightPadding > 0 && width - rightPadding < minWidth) {
            rightPadding = Math.max(0, width - minWidth);
        }

        if (getParent().getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            if (leftPadding + staticItemPadding != getPaddingStart() ||
                    rightPadding + staticItemPadding != getPaddingEnd()) {
                // The size of this view is kept, no need to tell parent.
                preventParentRelayout = true;
                titleView.setPaddingRelative(leftPadding + staticItemPadding, 0,
                        rightPadding + staticItemPadding, 0);
                preventParentRelayout = false;
            }
        } else {
            if (leftPadding + staticItemPadding != getPaddingEnd() ||
                    rightPadding + staticItemPadding != getPaddingStart()) {
                // In this case, we need to tell the parent to do a relayout, RTL is a bit more complicated, it seems.
                titleView.setPaddingRelative(rightPadding + staticItemPadding, 0,
                        leftPadding + staticItemPadding, 0);
            }
        }
    }

    public void requestLayout() {
        if (preventParentRelayout) {
            // Trivial layout, no need to tell parent.
            forceLayout();
        } else {
            super.requestLayout();
        }
    }

    public void clearValues() {
        setTag(null);
        schedule = null;
    }
}
