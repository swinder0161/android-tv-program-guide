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

package com.egeniq.androidtvprogramguide.util;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ProgramGuideUtil {
    private static int WIDTH_PER_HOUR = 0;
    private static final int INVALID_INDEX = -1;
    
	public static ProgramGuideSchedule<?> lastClickedSchedule = null;

    /**
     * Sets the width in pixels that corresponds to an hour in program guide. Assume that this is
     * called from main thread only, so, no synchronization.
     */
    public static void setWidthPerHour(int widthPerHour) {
        WIDTH_PER_HOUR = widthPerHour;
    }

    public static int convertMillisToPixel(long millis) {
        return (int)(millis * (long)WIDTH_PER_HOUR / TimeUnit.HOURS.toMillis(1L));
    }

    public static int convertMillisToPixel(long startMillis, long endMillis) {
        // Convert to pixels first to avoid accumulation of rounding errors.
        return convertMillisToPixel(endMillis) - convertMillisToPixel(startMillis);
    }

    /** Gets the time in millis that corresponds to the given pixels in the program guide.  */
    public static long convertPixelToMillis(int pixel) {
        return (long)pixel * TimeUnit.HOURS.toMillis(1L) / (long)WIDTH_PER_HOUR;
    }

    /**
     * Return the view should be focused in the given program row according to the focus range.
     *
     * @param keepCurrentProgramFocused If `true`, focuses on the current program if possible,
     * else falls back the general logic.
     */
    public static View findNextFocusedProgram(View programRow, int focusRangeLeft,
                                              int focusRangeRight, boolean keepCurrentProgramFocused) {
        if(null == programRow) return null;
        final ArrayList<View> focusables = new ArrayList<>();
        findFocusables(programRow, focusables);

        if (lastClickedSchedule != null) {
            // Select the current program if possible.
            for(int i=0; i<focusables.size(); i++) {
                final View focusable = (View)focusables.get(i);
                if (focusable instanceof ProgramGuideItemView) {
                    final ProgramGuideSchedule<?> schedule = ((ProgramGuideItemView<?>)focusable).schedule;
                    if (schedule != null && schedule.id == lastClickedSchedule.id) {
                        lastClickedSchedule = null;
                        return focusable;
                    }
                }
            }
            lastClickedSchedule = null;
        }

        if (keepCurrentProgramFocused) {
            // Select the current program if possible.
            for(int i=0; i<focusables.size(); i++) {
                final View focusable = (View)focusables.get(i);
                if (focusable instanceof ProgramGuideItemView && isCurrentProgram((ProgramGuideItemView<?>)focusable)) {
                    return focusable;
                }
            }
        }

        // Find the largest focusable among fully overlapped focusables.
        int maxFullyOverlappedWidth = Integer.MIN_VALUE;
        int maxPartiallyOverlappedWidth = Integer.MIN_VALUE;
        int nextFocusIndex = INVALID_INDEX;

        for(int i = 0; i < focusables.size(); i++) {
            final View focusable = focusables.get(i);
            final Rect focusableRect = new Rect();
            focusable.getGlobalVisibleRect(focusableRect);
            if (focusableRect.left <= focusRangeLeft && focusRangeRight <= focusableRect.right) {
                // the old focused range is fully inside the focusable, return directly.
                return focusable;
            } else if (focusRangeLeft <= focusableRect.left && focusableRect.right <= focusRangeRight) {
                // the focusable is fully inside the old focused range, choose the widest one.
                final int width = focusableRect.width();
                if (width > maxFullyOverlappedWidth) {
                    nextFocusIndex = i;
                    maxFullyOverlappedWidth = width;
                }
            } else if (maxFullyOverlappedWidth == Integer.MIN_VALUE) {
                final int overlappedWidth = focusRangeLeft <= focusableRect.left ?
                        focusRangeRight - focusableRect.left : focusableRect.right - focusRangeLeft;
                if (overlappedWidth > maxPartiallyOverlappedWidth) {
                    nextFocusIndex = i;
                    maxPartiallyOverlappedWidth = overlappedWidth;
                }
            }
        }
        return nextFocusIndex != INVALID_INDEX ? focusables.get(nextFocusIndex) : null;
    }

    /**
     * Returns `true` if the program displayed in the give [ ] is a current program.
     */
    public static boolean isCurrentProgram(ProgramGuideItemView<?> view) {
        if (view != null && view.schedule != null) {
            return view.schedule.isCurrentProgram();
        }
        return false;
    }

    private static void findFocusables(View v, ArrayList<View> outFocusable) {
        if (v.isFocusable()) {
            outFocusable.add(v);
        }
        if (v instanceof ViewGroup) {
            for(int i = 0; i<((ViewGroup)v).getChildCount(); i++) {
                findFocusables(((ViewGroup)v).getChildAt(i), outFocusable);
            }
        }
    }

    /** Returns `true` if the given view is a descendant of the give container.  */
    public static boolean isDescendant(ViewGroup container, View view) {
        if (view == null) {
            return false;
        }
        ViewParent p = view.getParent();
        while (p != null) {
            if (p == container) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }

    /**
     * Floors time to the given `timeUnit`. For example, if time is 5:32:11 and timeUnit is
     * one hour (60 * 60 * 1000), then the output will be 5:00:00.
     */
    public static long floorTime(long timeMs, long timeUnit) {
        return timeMs - timeMs % timeUnit;
    }
}
