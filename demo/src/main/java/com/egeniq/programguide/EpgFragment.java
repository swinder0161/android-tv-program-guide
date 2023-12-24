package com.egeniq.programguide;

import android.annotation.SuppressLint;
import android.text.Spanned;
import android.text.SpannedString;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.egeniq.androidtvprogramguide.ProgramGuideFragment;
import com.egeniq.androidtvprogramguide.R;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel;
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule;

import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class EpgFragment extends ProgramGuideFragment<EpgFragment.SimpleProgram> {
    public EpgFragment() {
        super();
        setROLLING_WINDOW_HOURS(24);
    }
    // Feel free to change configuration values like this:
    //
    // @Override public boolean getDISPLAY_CURRENT_TIME_INDICATOR()
    // @Override public boolean getDISPLAY_SHOW_PROGRESS()

    private static final String TAG = EpgFragment.class.getName();

    public static class SimpleChannel implements ProgramGuideChannel {
        private final String id;
        private final Spanned name;
        private final String imageUrl;
        private final int number;

        @Override
        public String getId() {
            return id;
        }
        @Override
        public Spanned getName() {
            return name;
        }
        @Override
        public String getImageUrl() {
            return imageUrl;
        }

        @Override
        public int getNumber() {
            return number;
        }

        public SimpleChannel(String _id, Spanned _name, String _imageUrl, int _number) {
            super();
            id = _id;
            name = _name;
            imageUrl = _imageUrl;
            number = _number;
        }

        @NonNull
        @Override
        public String toString() {
            return "SimpleChannel(id=" + id + ", name=" + name + ", imageUrl=" + imageUrl + ")";
        }
    }

    public static final class SimpleProgram {
        private final String id;
        private final String description;
        private final String metadata;

        public SimpleProgram(String _id, String _description, String _metadata) {
            super();
            id = _id;
            description = _description;
            metadata = _metadata;
        }

        @NonNull
        @Override
        public String toString() {
            return "SimpleProgram(id=" + id + ", description=" + description + ", metadata=" + metadata + ")";
        }
    }

    @Override
    public void onScheduleClicked(ProgramGuideSchedule<SimpleProgram> programGuideSchedule) {
        final SimpleProgram innerSchedule = programGuideSchedule.program;
        if (innerSchedule == null) {
            // If this happens, then our data source gives partial info
            Log.w(TAG, "Unable to open schedule!");
            return;
        }
        if (programGuideSchedule.isCurrentProgram()) {
            Toast.makeText(getContext(), "Open live player", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Open detail page", Toast.LENGTH_LONG).show();
        }
        // Example of how a program can be updated. You could also change the underlying program.
        updateProgram(programGuideSchedule.copy(null, null, null, null, null, null,
                programGuideSchedule.displayTitle + " [clicked]", null));

    }

    @Override
    public void onScheduleSelected(ProgramGuideSchedule<SimpleProgram> pgs) {
        final View view = getView();
        final TextView titleView = view == null ? null : view.findViewById(R.id.programguide_detail_title);
        if(titleView != null) titleView.setText(pgs == null ? null : pgs.displayTitle);
        final TextView metadataView = view == null ? null : view.findViewById(R.id.programguide_detail_metadata);
        if(metadataView != null) metadataView.setText(pgs == null ? null : (pgs.program == null ? null : pgs.program.metadata));
        final TextView descriptionView = view == null ? null : view.findViewById(R.id.programguide_detail_description);
        if(descriptionView != null) descriptionView.setText(pgs == null ? null : (pgs.program == null ? null : pgs.program.description));
        final ImageView imageView = view == null ? null : view.findViewById(R.id.programguide_detail_image);
        if(imageView == null) return;
        if (pgs != null && pgs.displayTitle != null) {
            Glide.with(imageView)
                    .load("https://picsum.photos/462/240?random=" + pgs.displayTitle.hashCode())
                    .centerCrop()
                    .error(R.drawable.programguide_icon_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);
        } else {
            Glide.with(imageView).clear(imageView);
        }
    }

    @Override
    public boolean isTopMenuVisible() {
        return false;
    }

    @SuppressLint("CheckResult")
    @Override
    public void requestingProgramGuideFor(final LocalDate localDate) {
        // Faking an asynchronous loading here
        setState(State.Loading);

        final ZonedDateTime MIN_CHANNEL_START_TIME =
                localDate.atStartOfDay().withHour(2).truncatedTo(ChronoUnit.HOURS)
                        .atZone(getDISPLAY_TIMEZONE());
        final ZonedDateTime MAX_CHANNEL_START_TIME =
                localDate.atStartOfDay().withHour(8).truncatedTo(ChronoUnit.HOURS)
                        .atZone(getDISPLAY_TIMEZONE());

        final ZonedDateTime MIN_CHANNEL_END_TIME =
                localDate.atStartOfDay().withHour(21).truncatedTo(ChronoUnit.HOURS)
                        .atZone(getDISPLAY_TIMEZONE());
        final ZonedDateTime MAX_CHANNEL_END_TIME =
                localDate.plusDays(1).atStartOfDay().withHour(4).truncatedTo(ChronoUnit.HOURS)
                        .atZone(getDISPLAY_TIMEZONE());

        final long MIN_SHOW_LENGTH_SECONDS = TimeUnit.MINUTES.toSeconds(5);
        final long MAX_SHOW_LENGTH_SECONDS = TimeUnit.MINUTES.toSeconds(120);
        Single<Pair<List<ProgramGuideChannel>, Map<String, List<ProgramGuideSchedule<SimpleProgram>>>>> single =
                Single.fromCallable(() -> {
                    int i = 0;
                    final List<ProgramGuideChannel> channels = new ArrayList<>();
                    channels.add(new SimpleChannel(
                            "npo-1",
                            new SpannedString("NPO 1"),
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/NPO_1_logo_2014.svg/320px-NPO_1_logo_2014.svg.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "npo-2",
                            new SpannedString("NPO 2"),
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f3/NPO_2_logo_2014.svg/275px-NPO_2_logo_2014.svg.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "bbc-news",
                            new SpannedString("BBC NEWS"),
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/6/62/BBC_News_2019.svg/200px-BBC_News_2019.svg.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "zdf",
                            new SpannedString("ZDF"),
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/ZDF_logo.svg/200px-ZDF_logo.svg.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "jednotka",
                            new SpannedString("Jednotka"),
                            "https://upload.wikimedia.org/wikipedia/en/thumb/7/76/Jednotka.svg/255px-Jednotka.svg.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "tv-nova",
                            new SpannedString("TV nova"),
                            "https://upload.wikimedia.org/wikipedia/commons/2/2f/TV_Nova_logo_2017.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "tv-5-monde",
                            new SpannedString("TV5MONDE"),
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/4/42/TV5MONDE_logo.png/320px-TV5MONDE_logo.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "orf-2",
                            new SpannedString("ORF 2"),
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/ORF2_logo_n.svg/320px-ORF2_logo_n.svg.png",
                            i++
                    ));
                    channels.add(new SimpleChannel(
                            "tvp-1",
                            new SpannedString("TVP 1"),
                            "https://upload.wikimedia.org/wikipedia/commons/e/ec/Tvp1.png",
                            i
                    ));

                    final List<String> showNames = new ArrayList<>();
                    showNames.add("News");
                    showNames.add("Sherlock Holmes");
                    showNames.add("It's Always Sunny In Philadelphia");
                    showNames.add("Second World War Documentary");
                    showNames.add("World Cup Final Replay");
                    showNames.add("Game of Thrones");
                    showNames.add("NFL Sunday Night Football");
                    showNames.add("NCIS");
                    showNames.add("Seinfeld");
                    showNames.add("ER");
                    showNames.add("Who Wants To Be A Millionaire");
                    showNames.add("Our Planet");
                    showNames.add("Friends");
                    showNames.add("House of Cards");

                    final Map<String, List<ProgramGuideSchedule<SimpleProgram>>> channelMap = new LinkedHashMap<>();

                    for (ProgramGuideChannel channel : channels) {
                        final List<ProgramGuideSchedule<SimpleProgram>> scheduleList = new ArrayList<>();
                        ZonedDateTime nextTime = randomTimeBetween(MIN_CHANNEL_START_TIME, MAX_CHANNEL_START_TIME);
                        while (nextTime.isBefore(MIN_CHANNEL_END_TIME)) {
                            final ZonedDateTime endTime = ZonedDateTime.ofInstant(
                                    Instant.ofEpochSecond(
                                            nextTime.toEpochSecond() + Random.nextLong(
                                                    MIN_SHOW_LENGTH_SECONDS,
                                                    MAX_SHOW_LENGTH_SECONDS
                                            )
                                    ), ZoneOffset.UTC
                            );
                            final ProgramGuideSchedule<SimpleProgram> schedule = createSchedule(Random.nextInList(showNames), channel.getId(), nextTime, endTime);
                            scheduleList.add(schedule);
                            nextTime = endTime;
                        }
                        final ZonedDateTime endTime = (nextTime.isBefore(MAX_CHANNEL_END_TIME)) ? randomTimeBetween(
                                nextTime,
                                MAX_CHANNEL_END_TIME
                        ) : MAX_CHANNEL_END_TIME;
                        final ProgramGuideSchedule<SimpleProgram> finalSchedule = createSchedule(Random.nextInList(showNames), channel.getId(), nextTime, endTime);
                        scheduleList.add(finalSchedule);
                        channelMap.put(channel.getId(), scheduleList);
                    }
                    return new Pair<>(channels, channelMap);
                });
        single.delay(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(it -> {
                    setData(it.first, it.second, localDate);
                    if (it.first.isEmpty() || it.second.isEmpty()) {
                        setState(new State.Error("No channels loaded."));
                    } else {
                        setState(State.Content);
                    }}, it -> Log.e(TAG, "Unable to load example data!", it));
    }

    private ProgramGuideSchedule<SimpleProgram> createSchedule(String scheduleName, String chid, ZonedDateTime startTime, ZonedDateTime endTime) {
        final long id = Random.nextLong(100000L);
        final String metadata = DateTimeFormatter.ofPattern("'Starts at' HH:mm").format(startTime);
        return ProgramGuideSchedule.createScheduleWithProgram(
                id,
                chid,
                startTime.toInstant(),
                endTime.toInstant(),
                true,
                scheduleName,
                new SimpleProgram(
                        "" + id,
                        "This is an example description for the programme. This description is taken from the SimpleProgram class, so by using a different class, " +
                                "you could easily modify the demo to use your own class",
                        metadata
                ));
    }

    private ZonedDateTime randomTimeBetween(ZonedDateTime min, ZonedDateTime max) {
        final long randomEpoch = Random.nextLong(min.toEpochSecond(), max.toEpochSecond());
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(randomEpoch), ZoneOffset.UTC);
    }

    @Override
    public void requestRefresh() {
        // You can refresh other data here as well.
        requestingProgramGuideFor(getCurrentDate());
    }
}
