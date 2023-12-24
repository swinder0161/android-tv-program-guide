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
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.TemporalAccessor;

public final class FixedZonedDateTime {
    /**
     * Get now as [ZonedDateTime].
     * <p>
     * Fixes a crash on some Sony TV's caused by invalid [ZoneId]
     * (which can happen on any function if it's using [ZoneId.systemDefault]).
     */
    public static ZonedDateTime now() {
        ZonedDateTime ret;
        try {
            ret = ZonedDateTime.now();
        } catch (DateTimeException ex) {
            ex.printStackTrace();
            long now = System.currentTimeMillis();
            Instant instant = Instant.ofEpochMilli(now);
            ret = ZonedDateTime.from((TemporalAccessor)instant);
        }
        return ret;
    }
}
