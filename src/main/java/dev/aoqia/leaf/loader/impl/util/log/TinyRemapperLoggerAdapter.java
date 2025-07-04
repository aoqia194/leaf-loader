/*
 * Copyright 2025 aoqia, FabricMC
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
package dev.aoqia.leaf.loader.impl.util.log;

import net.fabricmc.tinyremapper.api.TrLogger;

public class TinyRemapperLoggerAdapter implements TrLogger {
    private final LogCategory category;

    public TinyRemapperLoggerAdapter(LogCategory category) {
        this.category = category;
    }

    @Override
    public void log(Level level, String message) {
        switch (level) {
            case ERROR:
                Log.error(category, message);
                break;
            case WARN:
                Log.warn(category, message);
                break;
            case INFO:
                Log.info(category, message);
                break;
            case DEBUG:
                Log.debug(category, message);
                break;
        }
    }
}
