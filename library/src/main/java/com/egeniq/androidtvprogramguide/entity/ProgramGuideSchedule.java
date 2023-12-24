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

package com.egeniq.androidtvprogramguide.entity;

import androidx.annotation.NonNull;

import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil;

import org.threeten.bp.Instant;

/**
 * This class represents a programme in the EPG.
 * The program you associate with it can be your own class where you put the relevant values.
 * The ID should be unique across all schedules used in this app.
 * The start and end time are defined in UTC milliseconds. Overlapping times (within one channel) are not allowed
 * and will be corrected by the manager.
 * Is clickable defines if the user can click on this schedule, and so will trigger onScheduleClicked(schedule).
 * The displayTitle property is the string which is visible to the user in the EPG.
 */
public class ProgramGuideSchedule<T> {
    public final long id;
    public final String chid;
    public final long startsAtMillis;
    public final long endsAtMillis;
    public final OriginalTimes originalTimes;
    public final boolean isClickable;
    public final String displayTitle;
    public final T program;

    /**
     * Used internally. We make some fixes and adjustments to the times in the program manager,
     * but for consistency we keep the original times here as well.
     */
    public static class OriginalTimes {
        public final long startsAtMillis;
        public final long endsAtMillis;

        public OriginalTimes(long _startsAtMillis, long _endsAtMillis) {
            startsAtMillis = _startsAtMillis;
            endsAtMillis = _endsAtMillis;
        }

        @NonNull
        public String toString() {
            return "OriginalTimes(startsAtMillis=" + startsAtMillis + ", endsAtMillis=" + endsAtMillis + ")";
        }
    }

    private static final long GAP_ID = -1L;

    public static <T> ProgramGuideSchedule<T> createGap(String _chid, long from, long to) {
        return new ProgramGuideSchedule<>(GAP_ID, _chid, from, to, new OriginalTimes(from, to),
                false, null, null);
    }

    public static <T> ProgramGuideSchedule<T> createScheduleWithProgram(long _id, String _chid, Instant startsAt, Instant endsAt,
                                                             boolean _isClickable, String _displayTitle, T _program) {
        return new ProgramGuideSchedule<>(_id, _chid, startsAt.toEpochMilli(), endsAt.toEpochMilli(),
                new OriginalTimes(startsAt.toEpochMilli(), endsAt.toEpochMilli()), _isClickable, _displayTitle, _program);
    }

    public final int width;
    public final boolean isGap;

    public ProgramGuideSchedule(long _id, String _chid, long _startsAtMillis, long _endsAtMillis,
                                OriginalTimes _originalTimes, boolean _isClickable,
                                String _displayTitle, T _program) {
        id = _id;
        chid = _chid;
        startsAtMillis = _startsAtMillis;
        endsAtMillis = _endsAtMillis;
        originalTimes = _originalTimes;
        isClickable = _isClickable;
        displayTitle = _displayTitle;
        program = _program;
        width = ProgramGuideUtil.convertMillisToPixel(startsAtMillis, endsAtMillis);
        isGap = program == null;
    }

    public ProgramGuideSchedule<T> copy(Long _id, String _chid, Long startsAt, Long endsAt, OriginalTimes _originalTimes,
                                        Boolean _isClickable, String _displayTitle, T _program) {
        return new ProgramGuideSchedule<>(_id==null?id: _id, _chid==null?chid:_chid, startsAt==null?startsAtMillis:startsAt,
                endsAt==null?endsAtMillis: endsAt, _originalTimes==null?originalTimes: _originalTimes,
                _isClickable==null?isClickable: _isClickable, _displayTitle==null?displayTitle: _displayTitle,
                _program==null?program: _program);
    }

    public boolean isCurrentProgram() {
        final long now = System.currentTimeMillis();
        return ((now >= startsAtMillis) && (now <= endsAtMillis));
    }

    @NonNull
    public String toString() {
        return "ProgramGuideSchedule([@"+ Long.toHexString(hashCode()) + "]id=" + id + ", chid: " + chid +
                ", startsAtMillis=" + startsAtMillis + ", endsAtMillis=" + endsAtMillis + ", originalTimes=" + originalTimes +
                ", isClickable=" + isClickable + ", displayTitle=" + displayTitle + ", program=" + program + ")";
    }
}
