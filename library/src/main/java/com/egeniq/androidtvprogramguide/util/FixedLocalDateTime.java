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

import org.threeten.bp.DateTimeException;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;

import java.util.TimeZone;


public final class FixedLocalDateTime {
    /**
     * Get now as [LocalDateTime].
     * Fixes a crash on some Sony TV's caused by invalid [ZoneId]
     * (which can happen on any function if it's using [ZoneId.systemDefault]).
     */
    public static LocalDateTime now() {
        LocalDateTime ret;
        try {
            ret = LocalDateTime.now();
        } catch (DateTimeException e) {
            long now = System.currentTimeMillis();
            Instant instant = Instant.ofEpochMilli(now);
            int offset = TimeZone.getDefault().getOffset(now);
            ZoneId zoneId = ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(offset / 1000));
            ret = LocalDateTime.ofInstant(instant, zoneId);
        }
        return ret;
    }
}