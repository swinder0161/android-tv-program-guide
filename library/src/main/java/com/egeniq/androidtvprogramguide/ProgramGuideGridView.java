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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.leanback.widget.VerticalGridView;

import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView;
import com.egeniq.androidtvprogramguide.util.OnRepeatedKeyInterceptListener;
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil;

import java.util.concurrent.TimeUnit;

public final class ProgramGuideGridView<T> extends VerticalGridView {
    private static final int INVALID_INDEX = -1;
    private static final long FOCUS_AREA_SIDE_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final String TAG = ProgramGuideGridView.class.getName();

    public interface ChildFocusListener {
        /**
         * Is called before focus is moved. Only children to `ProgramGrid` will be passed. See
         * `ProgramGuideGridView#setChildFocusListener(ChildFocusListener)`.
         */
        void onRequestChildFocus(View oldFocus, View newFocus);
    }

    public interface ScheduleSelectionListener<T> {
        // Can be null if nothing is selected
        void onSelectionChanged(ProgramGuideSchedule<T> schedule);
    }

    private ProgramGuideManager<?> programGuideManager;

    // New focus will be overlapped with [focusRangeLeft, focusRangeRight].
    private int focusRangeLeft = 0;
    private int focusRangeRight = 0;
    private int lastUpDownDirection = 0;
    private boolean internalKeepCurrentProgramFocused = false;
    private final Rect tempRect = new Rect();
    private View nextFocusByUpDown = null;
    private final int rowHeight;
    private final int selectionRow;
    private View lastFocusedView = null;
    private View correctScheduleView = null;

    private OnRepeatedKeyInterceptListener onRepeatedKeyInterceptListener;

    public ChildFocusListener childFocusListener = null;
    public ScheduleSelectionListener<T> scheduleSelectionListener = null;

    private boolean featureKeepCurrentProgramFocused = true;
    public void setFeatureKeepCurrentProgramFocused(boolean value) {
        featureKeepCurrentProgramFocused = value;
        internalKeepCurrentProgramFocused = internalKeepCurrentProgramFocused && value;
    }

    public boolean featureFocusWrapAround = true;
    private boolean featureNavigateWithChannelKeys = false;
    public int overlapStart = 0;

    private ProgramGuideManager.Listener programManagerListener = new ProgramGuideManager.Listener() {
        @Override
        public void onSchedulesUpdated() {
            // Do nothing
        }

        @Override
        public void onTimeRangeUpdated() {
            // When time range is changed, we clear the focus state.
            clearUpDownFocusState(null);
        }
    };

    private ViewTreeObserver.OnGlobalFocusChangeListener globalFocusChangeListener = (v, newFocus) -> {
        if (newFocus != nextFocusByUpDown) {
            // If focus is changed by other buttons than UP/DOWN buttons,
            // we clear the focus state.
            clearUpDownFocusState(newFocus);
        }
        nextFocusByUpDown = null;
        if (ProgramGuideUtil.isDescendant(ProgramGuideGridView.this, newFocus)) {
            lastFocusedView = newFocus;
            if (newFocus instanceof ProgramGuideItemView && (correctScheduleView == null || correctScheduleView == newFocus)) {
                if (scheduleSelectionListener != null) {
                    scheduleSelectionListener.onSelectionChanged(((ProgramGuideItemView<T>)newFocus).schedule);
                }
            }

            correctScheduleView = null;
        } else {
            if (scheduleSelectionListener != null) {
                scheduleSelectionListener.onSelectionChanged(null);
            }
        }
    };

    public ProgramGuideGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        clearUpDownFocusState(null);
        // Don't cache anything that is off screen. Normally it is good to prefetch and prepopulate
        // off screen views in order to reduce jank, however the program guide is capable to scroll
        // in all four directions so not only would we prefetch views in the scrolling direction
        // but also keep views in the perpendicular direction up to date.
        // E.g. when scrolling horizontally we would have to update rows above and below the current
        // view port even though they are not visible.
        setItemViewCacheSize(0);
        Resources res = context.getResources();
        rowHeight = res.getDimensionPixelSize(R.dimen.programguide_program_row_height);
        selectionRow = res.getInteger(R.integer.programguide_selection_row);
        onRepeatedKeyInterceptListener = new OnRepeatedKeyInterceptListener(this);
        setOnKeyInterceptListener(onRepeatedKeyInterceptListener);
    }

    public ProgramGuideGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Initializes the grid view. It must be called before the view is actually attached to a window.
     */
    public void initialize(ProgramGuideManager<?> programManager) {
        programGuideManager = programManager;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(globalFocusChangeListener);
        if (!isInEditMode()) {
            programGuideManager.listeners.add(this.programManagerListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalFocusChangeListener(globalFocusChangeListener);
        if (!isInEditMode()) {
            programGuideManager.listeners.remove(programManagerListener);
        }
        clearUpDownFocusState(null);
    }

    /** Returns the currently focused item's horizontal range.  */
    public Range<Integer> getFocusRange() {
        if (focusRangeLeft == Integer.MIN_VALUE && focusRangeRight == Integer.MAX_VALUE) {
            clearUpDownFocusState(null);
        }
        return new Range<>(focusRangeLeft, focusRangeRight);
    }

    private void updateUpDownFocusState(View focused, int direction) {
        lastUpDownDirection = direction;
        int rightMostFocusablePosition = getRightMostFocusablePosition();
        Rect focusedRect = tempRect;

        // In order to avoid from focusing small width item, we clip the position with
        // mostRightFocusablePosition.
        focused.getGlobalVisibleRect(focusedRect);
        focusRangeLeft = Math.min(focusRangeLeft, rightMostFocusablePosition);
        focusRangeRight = Math.min(focusRangeRight, rightMostFocusablePosition);
        focusedRect.left = Math.min(focusedRect.left, rightMostFocusablePosition);
        focusedRect.right = Math.min(focusedRect.right, rightMostFocusablePosition);

        if (focusedRect.left > focusRangeRight || focusedRect.right < focusRangeLeft) {
            Log.w(TAG, "The current focus is out of [focusRangeLeft, focusRangeRight]");
            focusRangeLeft = focusedRect.left;
            focusRangeRight = focusedRect.right;
            return;
        }
        focusRangeLeft = Math.max(focusRangeLeft, focusedRect.left);
        focusRangeRight = Math.min(focusRangeRight, focusedRect.right);
    }

    private void clearUpDownFocusState(View focus) {
        lastUpDownDirection = 0;
        if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            focusRangeLeft = overlapStart;
            focusRangeRight = getRightMostFocusablePosition();
        } else {
            focusRangeLeft = getLeftMostFocusablePosition();
            focusRangeRight = !getGlobalVisibleRect(tempRect) ?
                    Integer.MAX_VALUE : tempRect.width() - overlapStart;
        }

        nextFocusByUpDown = null;
        // If focus is not a program item, drop focus to the current program when back to the grid
        // Only used if the feature flag is enabled
        internalKeepCurrentProgramFocused = featureKeepCurrentProgramFocused &&
                (!(focus instanceof ProgramGuideItemView<?>) || ProgramGuideUtil.isCurrentProgram((ProgramGuideItemView<?>)focus));
    }

    private int getRightMostFocusablePosition() {
        return !getGlobalVisibleRect(tempRect) ?
                Integer.MAX_VALUE :
                tempRect.right - ProgramGuideUtil.convertMillisToPixel(FOCUS_AREA_SIDE_MARGIN_MILLIS);
    }

    private int getLeftMostFocusablePosition() {
        return !getGlobalVisibleRect(tempRect) ?
                Integer.MIN_VALUE :
                tempRect.left + ProgramGuideUtil.convertMillisToPixel(FOCUS_AREA_SIDE_MARGIN_MILLIS);
    }

    private View focusFind(View focused, int direction) {
        int focusedChildIndex = getFocusedChildIndex();
        if (focusedChildIndex == INVALID_INDEX) {
            Log.w(TAG, "No child view has focus");
            return null;
        }
        int nextChildIndex =
                (direction == View.FOCUS_UP) ? focusedChildIndex - 1 : focusedChildIndex + 1;
        if (nextChildIndex < 0 || nextChildIndex >= getChildCount()) {
            // Wraparound if reached head or end
            if (featureFocusWrapAround) {
                if (getSelectedPosition() == 0) {
                    if (getAdapter() != null) {
                        scrollToPosition(getAdapter().getItemCount() - 1);
                    }
                    return null;
                } else if (getAdapter() != null && getSelectedPosition() == getAdapter().getItemCount() - 1) {
                    scrollToPosition(0);
                    return null;
                }
                return focused;
            } else {
                return null;
            }
        }
        View nextFocusedProgram = ProgramGuideUtil.findNextFocusedProgram(
                getChildAt(nextChildIndex), focusRangeLeft, focusRangeRight,
                internalKeepCurrentProgramFocused);
        if (nextFocusedProgram != null) {
            nextFocusedProgram.getGlobalVisibleRect(tempRect);
            nextFocusByUpDown = nextFocusedProgram;
        } else {
            Log.w(TAG, "focusFind didn't find any proper focusable");
        }

        return nextFocusedProgram;
    }

    // Returned value is not the position of VerticalGridView. But it's the index of ViewGroup
    // among visible children.
    private int getFocusedChildIndex() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).hasFocus()) {
                return i;
            }
        }
        return INVALID_INDEX;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        nextFocusByUpDown = null;
        if (focused == null || focused != this && !ProgramGuideUtil.isDescendant(this, focused)) {
            return super.focusSearch(focused, direction);
        }
        if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
            updateUpDownFocusState(focused, direction);
            View nextFocus = focusFind(focused, direction);
            if (nextFocus != null) {
                return nextFocus;
            }
        }
        return super.focusSearch(focused, direction);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (childFocusListener != null) {
            childFocusListener.onRequestChildFocus(getFocusedChild(), child);
        }
        super.requestChildFocus(child, focused);
    }

    @Override
    public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (lastFocusedView != null && lastFocusedView.isShown()) {
            if (lastFocusedView.requestFocus()) {
                return true;
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    public void focusCurrentProgram() {
        internalKeepCurrentProgramFocused = true;
        requestFocus();
    }

    public boolean isKeepCurrentProgramFocused() {
        return internalKeepCurrentProgramFocused;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        // It is required to properly handle OnRepeatedKeyInterceptListener. If the focused
        // item's are at the almost end of screen, focus change to the next item doesn't work.
        // It restricts that a focus item's position cannot be too far from the desired position.
        View focusedView = findFocus();
        if (focusedView != null && onRepeatedKeyInterceptListener.isFocusAccelerated) {
            int[] location = new int[2];
            getLocationOnScreen(location);
            int[] focusedLocation = new int[2];
            focusedView.getLocationOnScreen(focusedLocation);
            int y = focusedLocation[1] - location[1];

            int minY = (selectionRow - 1) * rowHeight;
            if (y < minY) {
                scrollBy(0, y - minY);
            }

            int maxY = (selectionRow + 1) * rowHeight;
            if (y > maxY) {
                scrollBy(0, y - maxY);
            }
        }
    }

    /**
     * Intercept the channel up / down keys to navigate with them, if this feature is enabled.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (featureNavigateWithChannelKeys && event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            View focusedChild = getFocusedChild();
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                View v = focusFind(focusedChild, View.FOCUS_UP);
                if (v != null) {
                    v.requestFocus();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                View v = this.focusFind(focusedChild, View.FOCUS_DOWN);
                if (v != null) {
                    v.requestFocus();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public void markCorrectChild(View view) {
        correctScheduleView = view;
    }
}
