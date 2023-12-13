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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.Nullable;

public final class ProgramGuideTimelineRow extends ProgramGuideTimelineGridView {
    public ProgramGuideTimelineRow(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ProgramGuideTimelineRow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgramGuideTimelineRow(Context context) {
        this(context, null);
    }

    private static final float FADING_EDGE_STRENGTH_START = 1.0F;
    private int scrollPosition = 0;

    /** Returns the current scroll position  */
    public int getCurrentScrollOffset() {
        return Math.abs(scrollPosition);
    }

    public void resetScroll() {
        final RecyclerView.LayoutManager layoutManager = getLayoutManager();
        if (layoutManager != null) {
            layoutManager.scrollToPosition(0);
        }
        scrollPosition = 0;
    }

    /** Scrolls horizontally to the given position.  */
    public void scrollTo(int scrollOffset, boolean smoothScroll) {
        int dx = scrollOffset - getCurrentScrollOffset();
        if (smoothScroll) {
            if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
                smoothScrollBy(dx, 0);
            } else {
                smoothScrollBy(-dx, 0);
            }
        } else {
            if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
                scrollBy(dx, 0);
            } else {
                scrollBy(-dx, 0);
            }
        }
    }

    @Override
    public void onScrolled(int dx, int dy) {
        scrollPosition += dx;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        return FADING_EDGE_STRENGTH_START;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        return 0.0F;
    }

    // State saving part
    @Override
    public Parcelable onSaveInstanceState() {
        //begin boilerplate code that allows parent classes to save state
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        //end
        ss.scrollPosition = scrollPosition;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        //begin boilerplate code so parent classes can restore state
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        super.onRestoreInstanceState(((SavedState)state).getSuperState());
        //end
        scrollPosition = ((SavedState)state).scrollPosition;
    }

    public static class SavedState extends View.BaseSavedState {
        public int scrollPosition;

        public SavedState(@Nullable Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel source) {
            super(source);
            scrollPosition = source.readInt();
        }

        private SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            scrollPosition = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(scrollPosition);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = (Parcelable.Creator<SavedState>) new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            @Override
            public SavedState createFromParcel(Parcel source, ClassLoader loader) {
                return new SavedState(source, loader);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
