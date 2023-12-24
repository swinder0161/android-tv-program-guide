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

import android.util.Log;

import androidx.annotation.MainThread;

import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;
import com.egeniq.androidtvprogramguide.util.FixedZonedDateTime;
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil;

import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import org.threeten.bp.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ProgramGuideManager<T> {
    /**
     * If the first entry's visible duration is shorter than this value, we should clip the entry out.
     * Note: If this value is larger than 1 min, it could cause mismatches between the entry's
     * position and detailed view's time range.
     */
    public static final long ENTRY_MIN_DURATION = TimeUnit.MINUTES.toMillis(2); // 2 min;
    private static final long MAX_UNACCOUNTED_TIME_BEFORE_GAP = TimeUnit.MINUTES.toMillis(15); // 15 min;
    private static final int DAY_STARTS_AT_HOUR = 5;
    private static final int DAY_ENDS_NEXT_DAY_AT_HOUR = 6;
    private static final String TAG = ProgramGuideManager.class.getName();

    private long startUtcMillis = 0;
    private long endUtcMillis = 0;
    private long fromUtcMillis = 0;
    private long toUtcMillis = 0;
    public final List<Listener> listeners = new ArrayList<>();
    public final List<ProgramGuideChannel> channels = new ArrayList<>();
    public int getChannelCount() {
        return channels.size();
    }
    private final Map<String, List<ProgramGuideSchedule<T>>> channelEntriesMap = new LinkedHashMap<>();
    public void addChannelEntries(String chid, List<ProgramGuideSchedule<T>> programs) {
        channelEntriesMap.put(chid, programs);
    }
    public List<ProgramGuideSchedule<T>> getChannelEntries(String chid) {
        return channelEntriesMap.get(chid);
    }
    private int ROLLING_WINDOW_HOURS = 0;
    public void setROLLING_WINDOW_HOURS(int hours) {
        ROLLING_WINDOW_HOURS = hours;
    }

    /** Returns the start time of currently managed time range, in UTC millisecond.  */
    public long getFromUtcMillis() {
        return fromUtcMillis;
    }

    /** Returns the end time of currently managed time range, in UTC millisecond.  */
    public long getToUtcMillis() {
        return toUtcMillis;
    }

    /** Update the initial time range to manage.
     * This is the time window where the scroll starts. */
    public void updateInitialTimeRange(long _startUtcMillis, long _endUtcMillis) {
        startUtcMillis = _startUtcMillis;
        if (_endUtcMillis > endUtcMillis) {
            endUtcMillis = _endUtcMillis;
        }

        setTimeRange(_startUtcMillis, _endUtcMillis);
    }

    private void updateChannelsTimeRange(LocalDate selectedDate, ZoneId timeZone) {
        final long viewPortWidth = toUtcMillis - fromUtcMillis;
        Long newStartMillis = null;
        Long newEndMillis = null;
        for (ProgramGuideChannel channel:channels) {
            final String channelId = channel.getId();
            final List<ProgramGuideSchedule<T>> entries = channelEntriesMap.get(channelId);
            final int size = entries != null ? entries.size() : 0;
            if (size == 0) {
                continue;
            }
            final ProgramGuideSchedule<T> lastEntry = entries.get(size-1);
            final ProgramGuideSchedule<T> firstEntry = entries.get(0);
            if (newStartMillis == null || newEndMillis == null) {
                newStartMillis = firstEntry.startsAtMillis;
                newEndMillis = lastEntry.endsAtMillis;
            }
            if (newStartMillis > firstEntry.startsAtMillis && firstEntry.startsAtMillis > 0L) {
                newStartMillis = firstEntry.startsAtMillis;
            }
            if (newEndMillis < lastEntry.endsAtMillis && lastEntry.endsAtMillis != Long.MAX_VALUE) {
                newEndMillis = lastEntry.endsAtMillis;
            }
        }

        startUtcMillis = newStartMillis != null ? newStartMillis : fromUtcMillis;
        endUtcMillis = newEndMillis != null ? newEndMillis : toUtcMillis;
        if (endUtcMillis > startUtcMillis) {
            for (ProgramGuideChannel channel:channels) {
                final String channelId = channel.getId();
                List<ProgramGuideSchedule<T>> entries = channelEntriesMap.get(channelId);
                if (null == entries) entries = new ArrayList<>();
                if (entries.isEmpty()) {
                    entries.add(ProgramGuideSchedule.createGap(channel.getId(), startUtcMillis, endUtcMillis));
                } else {
                    // Cut off items which don't belong in the desired timeframe
                    ZonedDateTime timelineStartsAt, timelineEndsAt;
                    if(ROLLING_WINDOW_HOURS <= 0) {
                        timelineStartsAt = selectedDate.atStartOfDay(timeZone).withHour(DAY_STARTS_AT_HOUR);
                        timelineEndsAt = timelineStartsAt.plusDays(1L).withHour(DAY_ENDS_NEXT_DAY_AT_HOUR);
                    } else {
                        timelineStartsAt = ZonedDateTime.now(timeZone).with(ChronoField.INSTANT_SECONDS, (ProgramGuideUtil.floorTime(System.currentTimeMillis() - ENTRY_MIN_DURATION,
                                TimeUnit.MINUTES.toMillis(30))) / 1000);
                        timelineEndsAt =
                                timelineStartsAt.plusHours(ROLLING_WINDOW_HOURS);
                    }

                    final long timelineStartsAtMillis = timelineStartsAt.toEpochSecond() * (long)1000;
                    final long timelineEndsAtMillis = timelineEndsAt.toEpochSecond() * (long)1000;

                    final ListIterator<ProgramGuideSchedule<T>> timelineIterator = entries.listIterator();
                    while (timelineIterator.hasNext()) {
                        final ProgramGuideSchedule<T> current = timelineIterator.next();
                        if (current.endsAtMillis < timelineStartsAtMillis || current.startsAtMillis > timelineEndsAtMillis) {
                            // Is before start or after end
                            timelineIterator.remove();
                        } else if (current.startsAtMillis < timelineStartsAtMillis && current.endsAtMillis < timelineEndsAtMillis) {
                            // Sticks out both sides
                            timelineIterator.set(
                                    current.copy(null, null, timelineStartsAtMillis, timelineEndsAtMillis,
                                            null, null, null, null));
                        } else if (current.startsAtMillis < timelineStartsAtMillis) {
                            // Sticks out left
                            timelineIterator.set(current.copy(null, null, timelineStartsAtMillis, null,
                                    null, null, null, null));
                        } else if (current.endsAtMillis > timelineEndsAtMillis) {
                            // Sticks out right
                            timelineIterator.set(current.copy(null, null, null, timelineEndsAtMillis,
                                    null, null, null, null));
                        }
                    }

                    if (startUtcMillis < timelineStartsAtMillis) {
                        startUtcMillis = timelineStartsAtMillis;
                    }
                    if (endUtcMillis > timelineEndsAtMillis) {
                        endUtcMillis = timelineEndsAtMillis;
                    }

                    // Pad the items on the right
                    final ProgramGuideSchedule<T> lastEntry = entries.size()==0?null:entries.get(entries.size()-1);
                    if (lastEntry == null || endUtcMillis > lastEntry.endsAtMillis) {
                        // We need to add a gap item to fill the place
                        entries.add(ProgramGuideSchedule.createGap(channel.getId(),
                                lastEntry != null ? lastEntry.endsAtMillis : startUtcMillis, endUtcMillis));
                    } else if (lastEntry.endsAtMillis == java.lang.Long.MAX_VALUE) {
                        entries.remove(entries.size() - 1);
                        entries.add(ProgramGuideSchedule.createGap(channel.getId(), lastEntry.startsAtMillis, endUtcMillis));
                    }
                    // Pad the items on the left
                    final ProgramGuideSchedule<T> firstEntry = entries.size() == 0 ? null : entries.get(0);
                    if (firstEntry == null || startUtcMillis < firstEntry.startsAtMillis) {
                        // We need to add a gap item to fill the place
                        entries.add(0, ProgramGuideSchedule.createGap(channel.getId(),
                                startUtcMillis, firstEntry != null ? firstEntry.startsAtMillis : endUtcMillis));
                    } else if (firstEntry.startsAtMillis <= 0) {
                        entries.remove(0);
                        entries.add(0, ProgramGuideSchedule.createGap(channel.getId(), startUtcMillis, firstEntry.endsAtMillis));
                    }
                    // Entries in the API not always follow each other. There are empty places which have not been accounted for, which offsets our calculations
                    // At this place, we adjust the ending times to be that of the next item. If the difference here is too big, we will insert a gap manually.
                    // The originalTimes property can be used to retrieve the original starting and ending times.
                    final ListIterator<ProgramGuideSchedule<T>> listIterator = entries.listIterator();
                    while(listIterator.hasNext()) {
                        final ProgramGuideSchedule<T> current = listIterator.next();
                        if (listIterator.hasNext()) {
                            final ProgramGuideSchedule<T> next = entries.get(listIterator.nextIndex());
                            final long timeDifference = next.startsAtMillis - current.endsAtMillis;
                            if (timeDifference < MAX_UNACCOUNTED_TIME_BEFORE_GAP) {
                                listIterator.set(current.copy(null, null, null, next.startsAtMillis,
                                        null, null, null, null));
                            } else {
                                listIterator.add(ProgramGuideSchedule.createGap(channel.getId(), current.endsAtMillis, next.startsAtMillis));
                            }
                        }
                    }
                    // Iterate the list again, this time to find a very short schedule. The schedules should be shifted to account for this.
                    final ListIterator<ProgramGuideSchedule<T>> shortIterator = entries.listIterator();
                    long millisToAddToNextStart = 0L;
                    while (shortIterator.hasNext()) {
                        final ProgramGuideSchedule<T> current = shortIterator.next();
                        final long currentDuration = current.endsAtMillis - (current.startsAtMillis + millisToAddToNextStart);
                        final boolean hasNext = shortIterator.hasNext();
                        if (!hasNext && (millisToAddToNextStart > 0 || currentDuration < ENTRY_MIN_DURATION)) {
                            Log.i(TAG, "The last schedule (" + current.program + ") has been extended because it was too short.");
                            final ProgramGuideSchedule<T> replacingSchedule = current.copy(null, null, current.startsAtMillis + millisToAddToNextStart,
                                    Math.max(current.startsAtMillis + ENTRY_MIN_DURATION, current.endsAtMillis),
                                    null, null, null, null);
                            shortIterator.set(replacingSchedule);
                        } else if (currentDuration < ENTRY_MIN_DURATION) {
                            Log.i(TAG, "The schedule (" + current.program + ") has been extended because it was too short.");
                            final ProgramGuideSchedule<T> replacingSchedule = current.copy(null, null, current.startsAtMillis + millisToAddToNextStart,
                                    current.startsAtMillis + millisToAddToNextStart + ENTRY_MIN_DURATION,
                                    null, null, null, null);
                            shortIterator.set(replacingSchedule);
                            millisToAddToNextStart = replacingSchedule.endsAtMillis - current.endsAtMillis;
                        } else if (millisToAddToNextStart > 0) {
                            Log.i(TAG, "The schedule (" + current.program + ") has been shortened because the previous schedule had to be extended.");
                            final ProgramGuideSchedule<T> replacingSchedule = current.copy(null, null, current.startsAtMillis + millisToAddToNextStart,
                                    null, null, null, null, null);
                            shortIterator.set(replacingSchedule);
                            millisToAddToNextStart = 0;
                        }
                    }
                }
                channelEntriesMap.put(channelId, entries);
            }
        }
        setTimeRange(startUtcMillis, startUtcMillis + viewPortWidth);
    }

    /**
     * Jumps to a specific position.
     * @param timeMillis The time in milliseconds to jump to.
     * @return True if the time was shifted. False if not change was triggered (time was the same as before).
     */
    public boolean jumpTo(long timeMillis) {
        final long timeShift = timeMillis - fromUtcMillis;
        shiftTime(timeShift);
        return timeShift != 0L;
    }

    /** Shifts the time range by the given time. Also makes the guide scroll the views.  */
    public void shiftTime(long timeMillisToScroll) {
        long _fromUtcMillis = fromUtcMillis + timeMillisToScroll;
        long _toUtcMillis = toUtcMillis + timeMillisToScroll;
        // We tried to scroll before the initial start time
        if (_fromUtcMillis < startUtcMillis) {
            _toUtcMillis += startUtcMillis - _fromUtcMillis;
            _fromUtcMillis = startUtcMillis;
        }
        // We tried to scroll over the initial end time
        if (_toUtcMillis > endUtcMillis) {
            _fromUtcMillis -= _toUtcMillis - endUtcMillis;
            _toUtcMillis = endUtcMillis;
        }
        setTimeRange(_fromUtcMillis, _toUtcMillis);
    }

    /** Returned the scrolled(shifted) time in milliseconds.  */
    public long getShiftedTime() {
        return fromUtcMillis - startUtcMillis;
    }

    /** Returns the start time set by [.updateInitialTimeRange].  */
    public long getStartTime() {
        return startUtcMillis;
    }

    public long getEndTime() {
        return endUtcMillis;
    }

    private void setTimeRange(long _fromUtcMillis, long _toUtcMillis) {
        if (fromUtcMillis != _fromUtcMillis || toUtcMillis != _toUtcMillis) {
            fromUtcMillis = _fromUtcMillis;
            toUtcMillis = _toUtcMillis;
            notifyTimeRangeUpdated();
        }
    }

    /**
     * Returns an entry as [ProgramGuideSchedule] for a given `channelId` and `index` of
     * entries within the currently managed time range. Returned [ProgramGuideSchedule] can be a dummy one
     * (e.g., whose channelId is INVALID_ID), when it corresponds to a gap between programs.
     */
    public ProgramGuideSchedule<T> getScheduleForChannelIdAndIndex(String channelId, int index) {
        List<ProgramGuideSchedule<T>> list = channelEntriesMap.get(channelId);
        return list == null ? null : list.get(index);
    }

    /** Returns the program index of the program at `time` or -1 if not found.  */
    public int getProgramIndexAtTime(String channelId, long time) {
        List<ProgramGuideSchedule<T>> list = channelEntriesMap.get(channelId);
        if (list != null) {
            for (ProgramGuideSchedule<T> entry : list) {
                if (entry.startsAtMillis <= time && time < entry.endsAtMillis) {
                    return list.indexOf(entry);
                }
            }
        }
        return -1;
    }

    /**
     * Returns the number of schedules, which lies within the currently managed time range, for a
     * given `channelId`.
     */
    public int getSchedulesCount(String channelId) {
        final List<ProgramGuideSchedule<T>> list = this.channelEntriesMap.get(channelId);
        return list != null ? list.size() : 0;
    }

    private void notifyTimeRangeUpdated() {
        for (Listener listener : listeners) {
            listener.onTimeRangeUpdated();
        }
    }

    private void notifySchedulesUpdated() {
        for (Listener listener : listeners) {
            listener.onSchedulesUpdated();
        }
    }

    /**
     * Returns a [ProgramGuideChannel] at a given `channelIndex` of the currently managed channels.
     * Returns `null` if such a channel is not found.
     */
    public ProgramGuideChannel getChannel(int channelIndex) {
        return (channelIndex < 0 || channelIndex >= channels.size()) ? null : channels.get(channelIndex);
    }

    public ProgramGuideSchedule<T> getCurrentProgram(String specificChannelId) {
        final ProgramGuideChannel firstChannel = channels.size()==0 ? null : channels.get(0);
        if (firstChannel == null) {
            return null;
        }
        final long now = FixedZonedDateTime.now().toEpochSecond() * (long)1000;
        ProgramGuideSchedule<T> bestMatch = null;
        final String channelId = specificChannelId != null ? specificChannelId : firstChannel.getId();

        for (ProgramGuideSchedule<T> schedule : channelEntriesMap.get(channelId)) {
            if (schedule.startsAtMillis < now) {
                bestMatch = schedule;
                if (schedule.endsAtMillis > now) {
                    return schedule;
                }
            }
        }
        return bestMatch;
    }

    public Integer getChannelIndex(String channelId) {
        for(int index=0; index < channels.size(); index++) {
            if(0 == channels.get(index).getId().compareTo(channelId)) {
                return index;
            }
        }
        return null;
    }

    @MainThread
    public void setData(List<ProgramGuideChannel> newChannels,
                        Map<String, List<ProgramGuideSchedule<T>>> newChannelEntries,
                        LocalDate selectedDate, ZoneId timeZone) {
        if(newChannels != null) {
            channels.clear();
            channels.addAll(newChannels);
        }
        if(newChannelEntries != null) {
            channelEntriesMap.clear();
            channelEntriesMap.putAll(newChannelEntries);
        }
        updateChannelsTimeRange(selectedDate, timeZone);
        notifySchedulesUpdated();
    }

    /**
     * Replaces a program in the entries based on the ID of the supplied program.
     * Since IDs should be unique, only the first match will be replaced.
     *
     * @param program The program with the new data.
     * @return The resulting program of the replacement. Null if no replacement happened
     */
    public ProgramGuideSchedule<T> updateProgram(ProgramGuideSchedule<T> program) {
        final Set<String> channelEntriesMapKeys = channelEntriesMap.keySet();
        ProgramGuideSchedule<T> replacement = null;
        for (String key : channelEntriesMapKeys) {
            final List<ProgramGuideSchedule<T>> list = channelEntriesMap.get(key);
            List<ProgramGuideSchedule<T>> mutatedList = null;
            if (list != null && replacement == null) {
                for (ProgramGuideSchedule<T> possibleMatch : list) {
                    if (possibleMatch.id == program.id && replacement == null) {
                        if (possibleMatch.originalTimes.startsAtMillis != program.originalTimes.startsAtMillis ||
                                possibleMatch.originalTimes.endsAtMillis != program.originalTimes.endsAtMillis) {
                            Log.w(TAG, "Different times found when updating program with ID: (" +
                                    program.id + "). Replacement will happen, but times will not be changed.");
                        }
                        if (mutatedList == null) {
                            mutatedList = new ArrayList<>(list);
                        }
                        final int index = list.indexOf(possibleMatch);
                        replacement = possibleMatch.copy(null, null, null, null, null,
                                program.isClickable, program.displayTitle, program.program);
                        mutatedList.set(index, replacement);
                    }
                }
            }
            if (mutatedList != null) {
                channelEntriesMap.put(key, mutatedList);
            }
        }
        return replacement;
    }

    public interface Listener {
        void onTimeRangeUpdated();
        void onSchedulesUpdated();
    }
}
