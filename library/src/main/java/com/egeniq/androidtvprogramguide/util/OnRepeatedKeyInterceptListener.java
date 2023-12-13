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

import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;

import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.VerticalGridView;

/**
 * Listener to make focus change faster over time.
 * */
public class OnRepeatedKeyInterceptListener implements BaseGridView.OnKeyInterceptListener {
    private final VerticalGridView verticalGridView;
    private final KeyInterceptHandler mHandler;
    public OnRepeatedKeyInterceptListener(VerticalGridView _verticalGridView) {
        super();
        verticalGridView = _verticalGridView;
        mHandler = new KeyInterceptHandler(this);
    }

    private static final int[] THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS = new int[]{2000, 5000};
    private static final int[] MAX_SKIPPED_VIEW_COUNT = new int[]{1, 4};
    private static final int MSG_MOVE_FOCUS = 1000;

    private int mDirection = 0;
    //TODO
    // private set
    public boolean isFocusAccelerated = false;
    private long mRepeatedKeyInterval = 0;

    @Override
    public boolean onInterceptKeyEvent(KeyEvent event) {
        mHandler.removeMessages(MSG_MOVE_FOCUS);
        if (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP && event.getKeyCode() != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false;
        }

        final long duration = event.getEventTime() - event.getDownTime();
        if (duration < (long)THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[0] || event.isCanceled()) {
            isFocusAccelerated = false;
            return false;
        }
        mDirection = (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) ? View.FOCUS_UP : View.FOCUS_DOWN;
        int skippedViewCount = MAX_SKIPPED_VIEW_COUNT[0];
        for (int i = 1; i < THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS.length; i++) {
            if (THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[i] < duration) {
                skippedViewCount = MAX_SKIPPED_VIEW_COUNT[i];
            } else {
                break;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mRepeatedKeyInterval = duration / (long)event.getRepeatCount();
            isFocusAccelerated = true;
        } else {
            // HACK: we move focus skippedViewCount times more even after ACTION_UP. Without this
            // hack, a focused view's position doesn't reach to the desired position
            // in ProgramGrid.
            isFocusAccelerated = false;
        }
        for (int i = 0; i < skippedViewCount; i++) {
            mHandler.sendEmptyMessageDelayed(MSG_MOVE_FOCUS,
                    mRepeatedKeyInterval * (long)i / (long)(skippedViewCount + 1));
        }
        return false;
    }

    public static class KeyInterceptHandler extends WeakHandler<OnRepeatedKeyInterceptListener> {
        public KeyInterceptHandler(OnRepeatedKeyInterceptListener listener) {
            super(Looper.getMainLooper(), listener);
        }

        public void handleMessage(Message msg, OnRepeatedKeyInterceptListener referent) {
            if (msg.what == MSG_MOVE_FOCUS) {
                final View focused = referent.verticalGridView.findFocus();
                if (focused != null) {
                    final View v = focused.focusSearch(referent.mDirection);
                    if (v != null && v != focused) {
                        v.requestFocus(referent.mDirection);
                    }
                }
            }
        }
    }
}
