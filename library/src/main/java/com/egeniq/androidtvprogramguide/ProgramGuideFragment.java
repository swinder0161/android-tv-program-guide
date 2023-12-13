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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.ProgramGuideLeanbackExtensions;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView;
import com.egeniq.androidtvprogramguide.row.ProgramGuideRowAdapter;
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimeListAdapter;
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimelineRow;
import com.egeniq.androidtvprogramguide.util.FilterOption;
import com.egeniq.androidtvprogramguide.util.FixedLocalDateTime;
import com.egeniq.androidtvprogramguide.util.FixedZonedDateTime;
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil;

import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class ProgramGuideFragment<T> extends Fragment implements ProgramGuideManager.Listener,
        ProgramGuideGridView.ChildFocusListener, ProgramGuideGridView.ScheduleSelectionListener<T>, ProgramGuideHolder<T> {
    public ProgramGuideFragment() {
        super();
    }

    private static final long HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final long HALF_HOUR_IN_MILLIS = HOUR_IN_MILLIS / 2;

    // We keep the duration between mStartTime and the current time larger than this value.
    // We clip out the first program entry in ProgramManager, if it does not have enough width.
    // In order to prevent from clipping out the current program, this value need be larger than
    // or equal to ProgramManager.ENTRY_MIN_DURATION.
    private static final long MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME =
            ProgramGuideManager.ENTRY_MIN_DURATION;
    private static final long TIME_INDICATOR_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(5);
    private static final String TIME_OF_DAY_MORNING = "time_of_day_morning";
    private static final String TIME_OF_DAY_AFTERNOON = "time_of_day_afternoon";
    private static final String TIME_OF_DAY_EVENING = "time_of_day_evening";
    private static final int MORNING_STARTS_AT_HOUR = 6;
    private static final int MORNING_UNTIL_HOUR = 12;
    private static final int AFTERNOON_UNTIL_HOUR = 19;
    private static final String TAG = ProgramGuideFragment.class.getName();
    protected final DateTimeFormatter FILTER_DATE_FORMATTER  = DateTimeFormatter.ISO_LOCAL_DATE;
    // Config values, override in subclass if necessary
    private final Locale DISPLAY_LOCALE = new Locale("en", "US");
    protected final ZoneId DISPLAY_TIMEZONE = ZoneOffset.UTC;
    private final int SELECTABLE_DAYS_IN_PAST = 7;
    private final int SELECTABLE_DAYS_IN_FUTURE = 7;
    private final boolean USE_HUMAN_DATES = true;
    private final DateTimeFormatter DATE_WITH_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEE d MMM").withLocale(DISPLAY_LOCALE);
    public boolean getDISPLAY_CURRENT_TIME_INDICATOR() { return true; }
    @Override
    public boolean getDISPLAY_SHOW_PROGRESS() { return true; }
    private final Integer OVERRIDE_LAYOUT_ID = null;
    private int selectionRow = 0;
    private int rowHeight = 0;
    private boolean didScrollToBestProgramme = false;
    private int currentTimeIndicatorWidth = 0;
    private int timelineAdjustmentPixels = 0;
    private boolean isInitialScroll = true;
    private int currentlySelectedFilterIndex = SELECTABLE_DAYS_IN_PAST;
    private int currentlySelectedTimeOfDayFilterIndex = -1;
    private State currentState = State.Loading;

    private boolean created = false;

    @Override
    public ProgramGuideGridView<T> getProgramGuideGrid() {
        return getView() != null ? getView().findViewById(R.id.programguide_grid) : null;
    }

    private ProgramGuideTimelineRow getTimeRow() {
        return getView() != null ? getView().findViewById(R.id.programguide_time_row) : null;
    }

    private TextView getCurrentDateView() {
        return getView() != null ? getView().findViewById(R.id.programguide_current_date) : null;
    }

    private TextView getJumpToLive() {
        return getView() != null ? getView().findViewById(R.id.programguide_jump_to_live) : null;
    }

    private FrameLayout getCurrentTimeIndicator() {
        return getView() != null ? getView().findViewById(R.id.programguide_current_time_indicator) : null;
    }

    private View getTimeOfDayFilter() {
        return getView() != null ? getView().findViewById(R.id.programguide_time_of_day_filter) : null;
    }

    private View getDayFilter() {
        return getView() != null ? getView().findViewById(R.id.programguide_day_filter) : null;
    }

    private View getFocusCatcher() {
        return getView() != null ? getView().findViewById(R.id.programguide_focus_catcher) : null;
    }

    private ViewAnimator getContentAnimator() {
        return getView() != null ? getView().findViewById(R.id.programguide_content_animator) : null;
    }

    private TextView getErrorMessage() {
        return getView() != null ? getView().findViewById(R.id.programguide_error_message) : null;
    }

    private long timelineStartMillis = 0L;
    private final ProgramGuideManager<T> mProgramGuideManager = new ProgramGuideManager<>();
    @Override
    public ProgramGuideManager<T> getProgramGuideManager() { return mProgramGuideManager;}
    private int gridWidth = 0;
    private int widthPerHour = 0;
    private long viewportMillis = 0L;
    private RecyclerView.OnScrollListener focusEnabledScrollListener = null;
    private LocalDate currentDate = FixedLocalDateTime.now().toLocalDate();
    public LocalDate getCurrentDate() { return currentDate; }

    private final Handler progressUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            long curtime = System.currentTimeMillis();
            updateCurrentTimeIndicator(curtime);
            updateCurrentProgramProgress(curtime);
            progressUpdateHandler.postDelayed(this, TIME_INDICATOR_UPDATE_INTERVAL);
        }
    };

    public static class State {
        private State() {}
        public static State Loading = new State();
        public static State Content = new State();
        public static final class Error extends ProgramGuideFragment.State {
            public final String errorMessage;
            public Error(String _errorMessage) {
                super();
                errorMessage = _errorMessage;
            }
        }
    }

    /**
     * Used if you have a collapsible top menu.
     * Return false if you don't
     */
    public abstract boolean isTopMenuVisible();

    /**
     * The user has selected a date, and wants to see the program guide for the date supplied in the parameter.
     * When loading data, you can use the setState(State) method to toggle between the different views.
     */
    public abstract void requestingProgramGuideFor(LocalDate localDate);

    /**
     * Denotes that the fragment wants to refresh its data, now only used at initialization.
     * You should probably request the program guide for the current date at this point.
     */
    public abstract void requestRefresh();

    /**
     *  Called when the user has selected a schedule from the grid.
     *  When no schedule is selected (such as when navigating outside the grid), the parameter will be null.
     */
    public abstract void onScheduleSelected(ProgramGuideSchedule<T> programGuideSchedule);

    /**
     * Called when the user has clicked on a schedule.
     * The schedule parameter contains all the info you need for taking an action.
     */
    public abstract void onScheduleClicked(ProgramGuideSchedule<T> programGuideSchedule);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Overriding the layout ID is also possible, so that if your layout naming follows a specific
        // structure, you are not obligated to use the name we use in the library.
        final View view =
                inflater.inflate(OVERRIDE_LAYOUT_ID != null ? OVERRIDE_LAYOUT_ID : R.layout.programguide_fragment, container, false);
        setupFilters(view);
        setupComponents(view);
        return view;
    }

    /**
     * We call the day and daytime switchers filters here.
     * The selectable days and their displays can be changed by the config parameters.
     * Also you can change the display values by overriding the string resources.
     */
    private void setupFilters(View view) {
        // Day filter
        final ZonedDateTime now = FixedZonedDateTime.now().withZoneSameInstant(DISPLAY_TIMEZONE);
        final List<FilterOption> dayFilterOptions = new ArrayList<>();
        for (int dayIndex = -SELECTABLE_DAYS_IN_PAST; dayIndex < SELECTABLE_DAYS_IN_FUTURE; dayIndex++) {
            if (USE_HUMAN_DATES && dayIndex == -1) {
                dayFilterOptions.add(new FilterOption(
                        getString(R.string.programguide_day_yesterday),
                        FILTER_DATE_FORMATTER.format(now.plusDays(dayIndex)),
                        false));
            } else if (USE_HUMAN_DATES && dayIndex == 0) {
                dayFilterOptions.add(new FilterOption(
                        getString(R.string.programguide_day_today),
                        FILTER_DATE_FORMATTER.format(now.plusDays(dayIndex)),
                        true));
            } else if (USE_HUMAN_DATES && dayIndex == 1) {
                dayFilterOptions.add(new FilterOption(
                        getString(R.string.programguide_day_tomorrow),
                        FILTER_DATE_FORMATTER.format(now.plusDays(dayIndex)),
                        false));
            } else {
                dayFilterOptions.add(new FilterOption(
                        DATE_WITH_DAY_FORMATTER.format(now.plusDays(dayIndex)),
                        FILTER_DATE_FORMATTER.format(now.plusDays(dayIndex)),
                        false));
            }
        }
        final View dayFilter = view.findViewById(R.id.programguide_day_filter);
        ((TextView) dayFilter.findViewById(R.id.programguide_filter_title))
                .setText(dayFilterOptions.get(currentlySelectedFilterIndex).displayTitle);
        dayFilter.setOnClickListener(filterView -> {
            AlertDialog.Builder adb = new AlertDialog.Builder(filterView.getContext());
            adb.setTitle(R.string.programguide_day_selector_title);
            CharSequence[] choices = new CharSequence[dayFilterOptions.size()];
            for (int i = 0; i < dayFilterOptions.size(); i++) {
                choices[i] = dayFilterOptions.get(i).displayTitle;
            }
            adb.setSingleChoiceItems(choices, currentlySelectedFilterIndex, (dialogInterface, position) -> {
                currentlySelectedFilterIndex = position;
                dialogInterface.dismiss();

                ((TextView) dayFilter.findViewById(R.id.programguide_filter_title)).setText(
                        dayFilterOptions.get(currentlySelectedFilterIndex).displayTitle);
                didScrollToBestProgramme = false;
                setJumpToLiveButtonVisible(false);
                currentDate =
                        LocalDate.parse(dayFilterOptions.get(position).value, FILTER_DATE_FORMATTER);
                requestingProgramGuideFor(currentDate);
            }).show();
        });

        // Time of day filter
        final boolean isItMorning = now.getHour() < MORNING_UNTIL_HOUR;
        final boolean isItAfternoon = !isItMorning && now.getHour() < AFTERNOON_UNTIL_HOUR;
        final boolean isItEvening = !isItMorning && !isItAfternoon;
        final List<FilterOption> timeOfDayFilterOptions = new ArrayList<>();
        timeOfDayFilterOptions.add(new FilterOption(
                getString(R.string.programguide_part_of_day_morning),
                TIME_OF_DAY_MORNING,
                isItMorning));
        timeOfDayFilterOptions.add(new FilterOption(
                getString(R.string.programguide_part_of_day_afternoon),
                TIME_OF_DAY_AFTERNOON,
                isItAfternoon));
        timeOfDayFilterOptions.add(new FilterOption(
                getString(R.string.programguide_part_of_day_evening),
                TIME_OF_DAY_EVENING,
                isItEvening));

        if (currentlySelectedTimeOfDayFilterIndex == -1) {
            currentlySelectedTimeOfDayFilterIndex = isItMorning ? 0 : (isItAfternoon ? 1 : 2);
        }
        final View timeOfDayFilter = view.findViewById(R.id.programguide_time_of_day_filter);
        if (timeOfDayFilter != null) {
            ((TextView) timeOfDayFilter.findViewById(R.id.programguide_filter_title)).setText(
                    timeOfDayFilterOptions.get(currentlySelectedTimeOfDayFilterIndex).displayTitle);
            timeOfDayFilter.setOnClickListener(it -> {
                AlertDialog.Builder adb = new AlertDialog.Builder(it.getContext());
                adb.setTitle(R.string.programguide_day_time_selector_title);
                CharSequence[] choices = new CharSequence[timeOfDayFilterOptions.size()];
                for (int i = 0; i < timeOfDayFilterOptions.size(); i++) {
                    choices[i] = timeOfDayFilterOptions.get(i).displayTitle;
                }
                adb.setSingleChoiceItems(choices, currentlySelectedTimeOfDayFilterIndex, (dialogInterface, position) -> {
                    currentlySelectedTimeOfDayFilterIndex = position;
                    ((TextView) timeOfDayFilter.findViewById(R.id.programguide_filter_title)).setText(
                            timeOfDayFilterOptions.get(currentlySelectedTimeOfDayFilterIndex).displayTitle);
                    dialogInterface.dismiss();
                    autoScrollToBestProgramme(true, null);
                }).show();
            });
        }
    }

    /**
     * The 'jump to live' button visibility can be set here.
     * It should only be visible if the date is today, and the current scroll range does not show
     * the current timestamp.
     */
    private void setJumpToLiveButtonVisible(boolean visible) {
        if (null != getJumpToLive())
            getJumpToLive().setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the selected schedule internally.
     */
    private void setSelectedSchedule(ProgramGuideSchedule<T> schedule) {
        onScheduleSelected(schedule);
    }

    /**
     * When the row view is created, it needs to know the current scroll offset, so it stays in sync
     * with the other, already existing rows
     */
    @Override
    public int getTimelineRowScrollOffset() {
        return (null != getTimeRow()) ? getTimeRow().getCurrentScrollOffset() : 0;
    }

    /**
     * Sets up all the components to be used by the fragment.
     */
    @SuppressLint("RestrictedApi")
    private void setupComponents(View view) {
        selectionRow = getResources().getInteger(R.integer.programguide_selection_row);
        rowHeight = getResources().getDimensionPixelSize(R.dimen.programguide_program_row_height_with_empty_space);
        widthPerHour = getResources().getDimensionPixelSize(R.dimen.programguide_table_width_per_hour);
        ProgramGuideUtil.setWidthPerHour(widthPerHour);
        final int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        gridWidth = (displayWidth - getResources().getDimensionPixelSize(R.dimen.programguide_channel_column_width));
        final RecyclerView timeRow = view.findViewById(R.id.programguide_time_row);
        timeRow.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                onHorizontalScrolled(dx);
            }
        });
        if (!created) {
            viewportMillis = gridWidth * HOUR_IN_MILLIS / widthPerHour;
            timelineStartMillis = ProgramGuideUtil.floorTime(
                    System.currentTimeMillis() - MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME,
                    HALF_HOUR_IN_MILLIS);
            getProgramGuideManager().updateInitialTimeRange(
                    timelineStartMillis,
                    timelineStartMillis + viewportMillis);
        }
        ProgramGuideGridView<T> it = view.findViewById(R.id.programguide_grid);
        if (it != null) {
            it.initialize(getProgramGuideManager());
            // Set the feature flags
            ProgramGuideLeanbackExtensions.setFocusOutSideAllowed(it, false, false);
            ProgramGuideLeanbackExtensions.setFocusOutAllowed(it, true, false);
            it.setFeatureKeepCurrentProgramFocused(false);
            it.featureFocusWrapAround = false;

            it.overlapStart = it.getResources().getDimensionPixelOffset(R.dimen.programguide_channel_column_width);
            it.childFocusListener = this;
            it.scheduleSelectionListener = this;
            it.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ALIGNED);
            it.setWindowAlignmentOffset(selectionRow * rowHeight);
            it.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            it.setItemAlignmentOffset(0);
            it.setItemAlignmentOffsetPercent(BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);

            final ProgramGuideRowAdapter adapter = new ProgramGuideRowAdapter(it.getContext(), this);
            it.setAdapter(adapter);
        }
        getProgramGuideManager().listeners.add(this);
        if (getCurrentDateView() != null) {
            getCurrentDateView().setAlpha(0.0F);
        }
        final ProgramGuideTimeListAdapter timelineAdapter = new ProgramGuideTimeListAdapter(getResources(), DISPLAY_TIMEZONE);
        if (timelineStartMillis > 0L) {
            timelineAdapter.update(timelineStartMillis, timelineAdjustmentPixels);
        }
        timeRow.setAdapter(timelineAdapter);
        timeRow.getRecycledViewPool().setMaxRecycledViews(R.layout.programguide_item_time,
                getResources().getInteger(R.integer.programguide_max_recycled_view_pool_time_row_item));
        final View jumpToLive = view.findViewById(R.id.programguide_jump_to_live);
        jumpToLive.setOnClickListener(view1 -> autoScrollToBestProgramme(false, null));
    }

    /**
     * Called when the fragment view has been created. We initialize some of our views here.
     */
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if ((savedInstanceState == null && !created) || !(currentState == State.Content)) {
            created = true;
            // Only get data when fragment is created first time, not recreated from backstack.
            // Also when the content was not loaded yet before.
            requestRefresh();
        } else {
            setTopMarginVisibility(isTopMenuVisible());
            if(getTimeRow() != null) getTimeRow().setAlpha(1.0F);
            if(getCurrentDateView() != null) getCurrentDateView().setAlpha(1.0F);
            updateCurrentDateText();
            updateCurrentTimeIndicator(System.currentTimeMillis());
            updateTimeOfDayFilter();
            didScrollToBestProgramme = false;
            setState(State.Content);
        }
    }

    /**
     * The top margin visibility can be changed, this is useful if you have a menu which collapses and
     * expands on top.
     */
    private void setTopMarginVisibility(boolean visible) {
        final ConstraintSet constraint = new ConstraintSet();
        final View view = getView();
        if (view == null) return;
        final ConstraintLayout constraintRoot = view.findViewById(R.id.programguide_constraint_root);
        final View topMargin = view.findViewById(R.id.programguide_top_margin);
        final View menuVisibleMargin = view.findViewById(R.id.programguide_menu_visible_margin);
        if (constraintRoot == null || topMargin == null || menuVisibleMargin == null) return;

        constraint.clone(constraintRoot);

        if (visible) {
            constraint.clear(topMargin.getId(), ConstraintSet.TOP);
            constraint.connect(topMargin.getId(), ConstraintSet.TOP,
                    menuVisibleMargin.getId(), ConstraintSet.BOTTOM);
        } else {
            constraint.clear(topMargin.getId(), ConstraintSet.TOP);
            constraint.connect(topMargin.getId(), ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        }
        constraint.applyTo(constraintRoot);
    }

    /**
     * Called when the timerow has scrolled. We should scroll the grid the same way.
     */
    private void onHorizontalScrolled(int dx) {
        if (dx == 0) {
            return;
        }
        updateCurrentTimeIndicator(System.currentTimeMillis());
        int i = 0;
        final ProgramGuideGridView<T> grid = getProgramGuideGrid();
        int n = grid.getChildCount();
        while (i < n) {
            grid.getChildAt(i).findViewById(R.id.row).scrollBy(dx, 0);
            ++i;
        }
    }

    /**
     * Updates the vertical bar which displays the current time.
     * If the currently visible time range does not contain the live timestamp, it should be hidden.
     */
    protected final void updateCurrentTimeIndicator(long now) {
        final FrameLayout currentTimeIndicator = getCurrentTimeIndicator();
        // No content, of feature is disabled -> hide
        if (currentState != State.Content || !getDISPLAY_CURRENT_TIME_INDICATOR()) {
            if (currentTimeIndicator != null) currentTimeIndicator.setVisibility(View.GONE);
            return;
        }

        final int offset = ProgramGuideUtil.convertMillisToPixel(timelineStartMillis, now) -
                (getTimeRow() != null ? getTimeRow().getCurrentScrollOffset() : 0) - timelineAdjustmentPixels;
        if (offset < 0) {
            if (currentTimeIndicator != null) currentTimeIndicator.setVisibility(View.GONE);
            setJumpToLiveButtonVisible(currentState != State.Loading && (getProgramGuideManager().getStartTime() <= now && now <= getProgramGuideManager().getEndTime()));
        } else {
            if (currentTimeIndicatorWidth == 0) {
                if (currentTimeIndicator != null)
                    currentTimeIndicator.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                currentTimeIndicatorWidth = (currentTimeIndicator != null) ? currentTimeIndicator.getMeasuredWidth() : 0;
            }
            if (currentTimeIndicator != null) {
                if (currentTimeIndicator.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
                    currentTimeIndicator.setTranslationX(offset - currentTimeIndicatorWidth / 2F);
                } else {
                    currentTimeIndicator.setTranslationX(-offset - currentTimeIndicatorWidth / 2F);
                }
                currentTimeIndicator.setVisibility(View.VISIBLE);
            }
            setJumpToLiveButtonVisible(currentState != State.Loading && offset > gridWidth);
        }
    }

    /**
     * Update the progressbar in each visible program view.
     */
    private void updateCurrentProgramProgress(long now) {
        if (!getDISPLAY_SHOW_PROGRESS()) {
            return;
        }
        for(int i = 0; i < getProgramGuideGrid().getChildCount(); i++) {
            final View it = getProgramGuideGrid().getChildAt(i);
            if (it != null) {
                RecyclerView recycler = it.findViewById(R.id.row);
                if (recycler != null) {
                    for(int j = 0; j < recycler.getChildCount(); j++) {
                        View row = recycler.getChildAt(j);
                        if (row instanceof ProgramGuideItemView<?>) {
                            ((ProgramGuideItemView<?>)row).updateProgress(now);
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the fragment will be resumed.
     * Starts the progress updates for the programs.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (getDISPLAY_SHOW_PROGRESS()) {
            progressUpdateHandler.removeCallbacks(progressUpdateRunnable);
            progressUpdateHandler.post(progressUpdateRunnable);
        }
    }

    /**
     * Called when the fragment will be paused. We stop the progress updates in this case.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (getDISPLAY_SHOW_PROGRESS()) {
            progressUpdateHandler.removeCallbacks(progressUpdateRunnable);
        }
    }

    /**
     * Called when the fragment will be destroyed. Removes the listeners to avoid memory leaks.
     */
    @Override
    public void onDestroyView() {
        getProgramGuideManager().listeners.remove(this);
        getProgramGuideGrid().scheduleSelectionListener = null;
        getProgramGuideGrid().childFocusListener = null;
        super.onDestroyView();
    }

    /**
     * Via this method you can supply the data to be displayed to the fragment
     */
    @MainThread
    public final void setData(List<ProgramGuideChannel> newChannels,
                              Map<String, List<ProgramGuideSchedule<T>>> newChannelEntries,
                              LocalDate selectedDate) {
        getProgramGuideManager().setData(newChannels, newChannelEntries, selectedDate, DISPLAY_TIMEZONE);
    }

    @Override
    public void onTimeRangeUpdated() {
        final int scrollOffset =
                (int)(widthPerHour * getProgramGuideManager().getShiftedTime() / HOUR_IN_MILLIS);
        Log.v(TAG, "Scrolling program guide with " + scrollOffset + "px.");
        if ((getTimeRow() != null && getTimeRow().getLayoutManager() != null && getTimeRow().getLayoutManager().getChildCount() == 0) || isInitialScroll) {
            isInitialScroll = false;
            if (getTimeRow() != null) {
                getTimeRow().post(() -> {
                    if (getTimeRow() != null)
                        getTimeRow().scrollTo(scrollOffset, false);
                });
            }
        } else {
            if (!getProgramGuideGrid().hasFocus()) {
                // We will temporarily catch the focus, so that the program guide does not focus on all the views while it is scrolling.
                // This is better for performance, and also avoids a bug where the focused view would be out of scope.
                if (focusEnabledScrollListener != null) {
                    if (getTimeRow() != null)
                        getTimeRow().removeOnScrollListener(focusEnabledScrollListener);
                }
                if(getFocusCatcher() != null) {
                    getFocusCatcher().setVisibility(View.VISIBLE);
                    getFocusCatcher().requestFocus();
                }
                getProgramGuideGrid().setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                //TODO
                // willUseScrollListener
                final boolean[] willUseScrollListener = {false};
                Runnable idleScrollRunnable = () -> {
                    getProgramGuideGrid().setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
                    focusEnabledScrollListener = null;
                    getProgramGuideGrid().requestFocus();
                    if(getFocusCatcher() != null)
                        getFocusCatcher().setVisibility(View.GONE);
                    updateCurrentTimeIndicator(System.currentTimeMillis());
                };
                focusEnabledScrollListener = new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                        willUseScrollListener[0] =
                                true; // The listener has fired, so later it will also fire at the correct state.
                        if (getTimeRow() != null) getTimeRow().removeCallbacks(idleScrollRunnable);
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            if (getTimeRow() != null) getTimeRow().removeOnScrollListener(this);
                            idleScrollRunnable.run();
                        }
                    }
                };
                if (getTimeRow() != null) getTimeRow().addOnScrollListener(focusEnabledScrollListener);
                // Rarely the scroll listener does not fire. In this case we rely on a child attach listener
                RecyclerView.OnChildAttachStateChangeListener childAttachStateChangeListener =
                        new RecyclerView.OnChildAttachStateChangeListener() {
                    private boolean didPostCallback = false;
                    @Override
                    public void onChildViewDetachedFromWindow(@NonNull View view) {
                        // Unused.
                    }
                    //TODO
                    // getTimeRow().removeOnChildAttachStateChangeListener(this)
                    @Override
                    public void onChildViewAttachedToWindow(@NonNull View view) {
                        if (getTimeRow() != null)
                                getTimeRow().removeOnChildAttachStateChangeListener(this);

                        if (!willUseScrollListener[0] && !didPostCallback) {
                            Log.v(TAG, "Scroll listener will not fire, posting idle scroll runnable.");
                            if (getTimeRow() != null)
                                getTimeRow().postDelayed(idleScrollRunnable, 50L);
                            didPostCallback = true;
                        }
                    }
                };
                if (getTimeRow() != null)
                    getTimeRow().addOnChildAttachStateChangeListener(childAttachStateChangeListener);
            }
            if (getTimeRow() != null)
                getTimeRow().scrollTo(scrollOffset, true);
        }
        // Might just be a reset
        if (scrollOffset != 0) {
            updateTimeOfDayFilter();
            updateCurrentDateText();
        }
    }

    private void updateTimeOfDayFilter() {
        final int leftHour =
                Instant.ofEpochMilli(getProgramGuideManager().getFromUtcMillis()).atZone(DISPLAY_TIMEZONE).getHour();
        final int selectedItemPosition = (leftHour < MORNING_UNTIL_HOUR) ? 0 :
                ((leftHour < AFTERNOON_UNTIL_HOUR) ? 1 : 2);
        if (currentlySelectedTimeOfDayFilterIndex != selectedItemPosition) {
            currentlySelectedTimeOfDayFilterIndex = selectedItemPosition;
            final String displayText = getString(selectedItemPosition == 0 ? R.string.programguide_part_of_day_morning :
                    (selectedItemPosition == 1 ? R.string.programguide_part_of_day_afternoon : R.string.programguide_part_of_day_evening));
            if (getTimeOfDayFilter() != null) {
                TextView tv = getTimeOfDayFilter().findViewById(R.id.programguide_filter_title);
                if(tv != null) tv.setText(displayText);
            }
        }
    }

    private void updateCurrentDateText() {
        // The day might have changed
        final ZonedDateTime viewportStartTime =
                Instant.ofEpochMilli(getProgramGuideManager().getFromUtcMillis()).atZone(DISPLAY_TIMEZONE);
        String dateText = DATE_WITH_DAY_FORMATTER.format(viewportStartTime);
        if (dateText.endsWith(".")) {
            int sz = dateText.length();
            dateText = dateText.substring(0, sz-1);
        }
        if (getCurrentDateView() != null)
            getCurrentDateView().setText(dateText.toUpperCase(DISPLAY_LOCALE));
    }

    private void updateTimeline() {
        timelineStartMillis = ProgramGuideUtil.floorTime(
                getProgramGuideManager().getStartTime() - MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME,
                HALF_HOUR_IN_MILLIS);
        final long timelineDifference = getProgramGuideManager().getStartTime() - timelineStartMillis;
        timelineAdjustmentPixels = ProgramGuideUtil.convertMillisToPixel(timelineDifference);
        Log.i(TAG,
                "Adjusting timeline with " + timelineAdjustmentPixels + "px, for a difference of " + timelineDifference / 60_000f + " minutes.");
        final ProgramGuideTimelineRow timelineRow = getTimeRow();
        if (timelineRow != null) {
            if (timelineRow.getAdapter() != null) {
                ProgramGuideTimeListAdapter adapter = (ProgramGuideTimeListAdapter) timelineRow.getAdapter();
                adapter.update(timelineStartMillis, timelineAdjustmentPixels);
                for (int i = 0; i < getProgramGuideGrid().getChildCount(); i++) {
                    final View it = getProgramGuideGrid().getChildAt(i);
                    if (it != null) {
                        LinearLayoutManager linearLayoutManager = (LinearLayoutManager)((RecyclerView)it.findViewById(R.id.row)).getLayoutManager();
                        if(linearLayoutManager != null) linearLayoutManager.scrollToPosition(0);
                    }
                }
                timelineRow.resetScroll();
            }
        }
    }

    public void setTopMenuVisibility(boolean isVisible) {
        setTopMarginVisibility(isVisible);
    }

    /**
     * Changes the state, used for animated and handling visibility of some screen components.
     */
    public void setState(State state) {
        currentState = state;
        final float alpha;
        final ViewAnimator contentAnimator = getContentAnimator();
        if(state == State.Content) {
            alpha = 1F;
            if(contentAnimator != null)
                contentAnimator.setDisplayedChild(2);
        } else if(state instanceof State.Error) {
            final State.Error stateError = (State.Error) state;
            alpha = 0f;
            TextView errorMessage = getErrorMessage();
            if(errorMessage != null){
                if (stateError.errorMessage == null) {
                    errorMessage.setText(R.string.programguide_error_fetching_content);
                } else {
                    errorMessage.setText(stateError.errorMessage);
                }
            }
            if(contentAnimator != null)
                contentAnimator.setDisplayedChild(1);
        } else {
            alpha = 0F;
            if(contentAnimator != null)
                contentAnimator.setDisplayedChild(0);
        }
        final View[] views = new View[]{getCurrentDateView(), getTimeRow(), getCurrentTimeIndicator()};
        for(View it:views) {
            if (it == null) {
                continue;
            }
            it.animate().cancel();
            it.animate().alpha(alpha).setDuration(500);
        }
    }

    /**
     * The GridView calls this method on the fragment when the focused child changes.
     * This is important because we scroll the grid vertically if there are still
     * channels to be shown in the desired direction.
     */
    @Override
    public void onRequestChildFocus(View oldFocus, View newFocus) {
        if (oldFocus != null && newFocus != null) {
            final int selectionRowOffset = selectionRow * rowHeight;
            if (oldFocus.getTop() < newFocus.getTop()) {
                // Selection moves downwards
                // Adjust scroll offset to be at the bottom of the target row and to expand up. This
                // will set the scroll target to be one row height up from its current position.
                getProgramGuideGrid().setWindowAlignmentOffset(selectionRowOffset + rowHeight);
                getProgramGuideGrid().setItemAlignmentOffsetPercent(100.0F);
            } else if (oldFocus.getTop() > newFocus.getTop()) {
                // Selection moves upwards
                // Adjust scroll offset to be at the top of the target row and to expand down. This
                // will set the scroll target to be one row height down from its current position.
                getProgramGuideGrid().setWindowAlignmentOffset(selectionRowOffset);
                getProgramGuideGrid().setItemAlignmentOffsetPercent(0.0F);
            }
        }
    }

    /**
     * The gridview calls this method on the fragment when the focus changes on one of their child changes.
     */
    @Override
    public void onSelectionChanged(ProgramGuideSchedule<T> schedule) {
        setSelectedSchedule(schedule);
    }

    /**
     * This method is called from the ProgramGuideListAdapter, when the OnClickListener is triggered.
     */
    @Override
    public void onScheduleClickedInternal(ProgramGuideSchedule<T> schedule) {
        ProgramGuideUtil.lastClickedSchedule = schedule;
        onScheduleClicked(schedule);
    }

    /**
     * Called by the manager if the channels and programs are ready to be displayed.
     */
    @Override
    public void onSchedulesUpdated() {
        if (getProgramGuideGrid().getAdapter() != null)
            ((ProgramGuideRowAdapter)getProgramGuideGrid().getAdapter()).update();
        updateTimeline();

        progressUpdateHandler.removeCallbacks(progressUpdateRunnable);
        progressUpdateHandler.post(progressUpdateRunnable);

        if (!didScrollToBestProgramme) {
            didScrollToBestProgramme = true;
            isInitialScroll = true;
            autoScrollToBestProgramme(false, null);
        }

        final ProgramGuideTimelineRow timeRow = getTimeRow();
        if (timeRow != null) {
            final ViewPropertyAnimator animate = timeRow.animate();
            if (animate != null) {
                animate.alpha(1f).setDuration(500L);
            }
        }
    }

    /**
     * After laying out all the views inside the grid, we want to scroll
     * to the most relevant programme to the user. This function takes care of that.
     *
     * @param useTimeOfDayFilter If the time of day filter was used to do the scroll. In this case
     * the scroll will be done to a hardcoded time, instead of the current live programme.
     * @param specificChannelId The specific channel ID to scroll. Will be the first channel in
     * the list of not specified.
     */
    private void autoScrollToBestProgramme(boolean useTimeOfDayFilter, String specificChannelId) {
        final long nowMillis = Instant.now().toEpochMilli();
        // If the current time is within the managed frame, jump to it.
        if (!useTimeOfDayFilter && getProgramGuideManager().getStartTime() <= nowMillis && nowMillis <= getProgramGuideManager().getEndTime()) {
            final ProgramGuideSchedule<T> currentProgram = getProgramGuideManager().getCurrentProgram(specificChannelId);
            if (currentProgram == null) {
                Log.w(TAG, "Can't scroll to current program because schedule not found.");
            } else {
                Log.i(TAG, "Scrolling to " + currentProgram.displayTitle + ", started at " + currentProgram.startsAtMillis);
                if (!getProgramGuideManager().jumpTo(currentProgram.startsAtMillis)) {
                    getProgramGuideGrid().focusCurrentProgram();
                }
            }
        } else {
            // The day is not today.
            // Go to the selected time of day.
            final ZonedDateTime timelineDate =
                    Instant.ofEpochMilli((getProgramGuideManager().getStartTime() + getProgramGuideManager().getEndTime()) / 2)
                            .atZone(DISPLAY_TIMEZONE);
            final int scrollToHour = (currentlySelectedTimeOfDayFilterIndex == 0) ?
                    MORNING_STARTS_AT_HOUR :
                    ((currentlySelectedTimeOfDayFilterIndex == 1) ? MORNING_UNTIL_HOUR :
                            AFTERNOON_UNTIL_HOUR);

            final long scrollToMillis =
                    timelineDate.withHour(scrollToHour).truncatedTo(ChronoUnit.HOURS)
                            .toEpochSecond() * 1000;
            if (getProgramGuideManager().jumpTo(scrollToMillis)) {
                getProgramGuideGrid().requestFocus();
            }
        }
    }

    /**
     * Scrolls to a channel with a specific ID vertically and horizontally. Highlights the current
     * program with focus after the scroll.
     *
     * @param channelId The channel ID to scroll to.
     */
    public void scrollToChannelWithId(String channelId) {
        final Integer index = getProgramGuideManager().getChannelIndex(channelId);
        if (index != null) {
            getProgramGuideGrid().smoothScrollToPosition(index);
            autoScrollToBestProgramme(false, channelId);
        }
    }

    /**
     * Updates the program everywhere, including the schedule grid.
     * This method requires that a program with the same ID exists (otherwise nothing will happen).
     * <p>
     * If there are multiple programs with the same ID, only the first one will be updated (you should have unique IDs!).
     *
     */
    public void updateProgram(ProgramGuideSchedule<T> program) {
        final ProgramGuideSchedule<T> replacement = getProgramGuideManager().updateProgram(program);
        if (replacement != null) {
            // Now find it in the grid, and update that single
            if (getProgramGuideGrid().getAdapter() == null) {
                Log.w(TAG, "Program not updated, adapter not found or has incorrect type.");
                return;
            }
            final Integer index = ((ProgramGuideRowAdapter)getProgramGuideGrid().getAdapter()).updateProgram(program);
            if (index == null) {
                Log.w(TAG, "Program not updated, item not found in adapter.");
                return;
            }
            final RecyclerView.ViewHolder viewHolder = getProgramGuideGrid().findViewHolderForAdapterPosition(index);
            if (viewHolder == null) {
                Log.i(TAG, "Program layout was not updated, because view holder for it was not found - item is probably outside of visible area");
                return;
            }
            ((ProgramGuideRowAdapter.ProgramRowViewHolder)viewHolder).updateLayout();
        } else {
            Log.w(TAG, "Program not updated, no match found.");
        }
    }
}