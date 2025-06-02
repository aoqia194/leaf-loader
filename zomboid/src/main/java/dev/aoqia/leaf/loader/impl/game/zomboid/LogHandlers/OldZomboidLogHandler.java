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
package dev.aoqia.leaf.loader.impl.game.zomboid.LogHandlers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dev.aoqia.leaf.loader.impl.util.log.LogCategory;
import dev.aoqia.leaf.loader.impl.util.log.LogHandler;
import dev.aoqia.leaf.loader.impl.util.log.LogLevel;

public final class OldZomboidLogHandler implements LogHandler {
    private static final Class<?> DEBUG_TYPE;
    private static final Object DEBUG_TYPE_Leaf;
    private static final Class<?> LOG_SEVERITY;
    private static final Object LOG_SEVERITY_Trace;
    private static final Object LOG_SEVERITY_Debug;
    private static final Object LOG_SEVERITY_General;
    private static final Object LOG_SEVERITY_Warning;
    private static final Object LOG_SEVERITY_Error;
    private static final Class<?> DEBUG_LOG;
    private static final Method DEBUG_LOG_createDebugLogStream;
    private static final Method DEBUG_LOG_isLogEnabled;
    private static final Class<?> DEBUG_LOG_STREAM;
    private static final Method DEBUG_LOG_STREAM_trace;
    private static final Method DEBUG_LOG_STREAM_debugln;
    private static final Method DEBUG_LOG_STREAM_println;
    private static final Method DEBUG_LOG_STREAM_warn;
    private static final Method DEBUG_LOG_STREAM_error;
    private static final Method DEBUG_LOG_STREAM_printException;
    private static final Object LOGGER;

    static {
        try {
            LOG_SEVERITY = Class.forName("zombie.debug.LogSeverity");
            final Method logSeverityValueOf = LOG_SEVERITY.getMethod("valueOf", String.class);
            LOG_SEVERITY_Trace = logSeverityValueOf.invoke(null, "Trace");
            LOG_SEVERITY_Debug = logSeverityValueOf.invoke(null, "Debug");
            LOG_SEVERITY_General = logSeverityValueOf.invoke(null, "General");
            LOG_SEVERITY_Warning = logSeverityValueOf.invoke(null, "Warning");
            LOG_SEVERITY_Error = logSeverityValueOf.invoke(null, "Error");

            DEBUG_TYPE = Class.forName("zombie.debug.DebugType");
            DEBUG_TYPE_Leaf = DEBUG_TYPE.getMethod("valueOf", String.class).invoke(null, "Leaf");

            DEBUG_LOG_STREAM = Class.forName("zombie.debug.DebugLogStream");
            DEBUG_LOG_STREAM_trace = DEBUG_LOG_STREAM.getMethod("trace", String.class);
            DEBUG_LOG_STREAM_debugln = DEBUG_LOG_STREAM.getMethod("debugln", String.class);
            DEBUG_LOG_STREAM_println = DEBUG_LOG_STREAM.getMethod("println", String.class);
            DEBUG_LOG_STREAM_warn = DEBUG_LOG_STREAM.getMethod("warn", Object.class);
            DEBUG_LOG_STREAM_error = DEBUG_LOG_STREAM.getMethod("error", Object.class);
            DEBUG_LOG_STREAM_printException = DEBUG_LOG_STREAM.getMethod("printException",
                Throwable.class, String.class, LOG_SEVERITY);

            DEBUG_LOG = Class.forName("zombie.debug.DebugLog");
            DEBUG_LOG_createDebugLogStream = DEBUG_LOG.getDeclaredMethod("createDebugLogStream",
                DEBUG_TYPE);
            DEBUG_LOG_isLogEnabled = DEBUG_LOG.getMethod("isLogEnabled", LOG_SEVERITY, DEBUG_TYPE);

            DEBUG_LOG_createDebugLogStream.setAccessible(true);
            LOGGER = DEBUG_LOG_createDebugLogStream.invoke(null, DEBUG_TYPE_Leaf);
            DEBUG_LOG.getMethod("enableLog", DEBUG_TYPE, LOG_SEVERITY)
                .invoke(null, DEBUG_TYPE_Leaf, LOG_SEVERITY_Trace);
            DEBUG_LOG_createDebugLogStream.setAccessible(false);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc,
        boolean fromReplay, boolean wasSuppressed) {
        if (LOGGER == null) {
            return;
        }

        if (msg == null) {
            if (exc == null) {
                return;
            }

            msg = "Exception";
        }

        try {
            switch (level) {
                case ERROR:
                    if (exc == null) {
                        DEBUG_LOG_STREAM_error.invoke(LOGGER, msg);
                    } else {
                        DEBUG_LOG_STREAM_printException.invoke(LOGGER, exc, msg,
                            LOG_SEVERITY_Error);
                    }

                    break;
                case WARN:
                    if (exc == null) {
                        DEBUG_LOG_STREAM_warn.invoke(LOGGER, msg);
                    } else {
                        DEBUG_LOG_STREAM_printException.invoke(LOGGER, exc, msg,
                            LOG_SEVERITY_Warning);
                    }

                    break;
                case INFO:
                    if (exc == null) {
                        DEBUG_LOG_STREAM_println.invoke(LOGGER, msg);
                    } else {
                        DEBUG_LOG_STREAM_printException.invoke(LOGGER, exc, msg,
                            LOG_SEVERITY_General);
                    }

                    break;
                case DEBUG:
                    if (exc == null) {
                        DEBUG_LOG_STREAM_debugln.invoke(LOGGER, msg);
                    } else {
                        DEBUG_LOG_STREAM_printException.invoke(LOGGER, exc, msg,
                            LOG_SEVERITY_Debug);
                    }

                    break;
                case TRACE:
                    if (exc == null) {
                        DEBUG_LOG_STREAM_trace.invoke(LOGGER, msg);
                    } else {
                        DEBUG_LOG_STREAM_printException.invoke(LOGGER, exc, msg,
                            LOG_SEVERITY_Trace);
                    }

                    break;
                default:
                    throw new IllegalArgumentException("Unknown level: " + level);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(
                "Failed to invoke logging functions for log() in OldZomboidLogHandler!");
        }
    }

    @Override
    public boolean shouldLog(LogLevel level, LogCategory category) {
        if (LOGGER == null) {
            return true;
        }

        try {
            switch (level) {
                case ERROR:
                    return (boolean) DEBUG_LOG_isLogEnabled.invoke(null, LOG_SEVERITY_Error,
                        DEBUG_TYPE_Leaf);
                case WARN:
                    return (boolean) DEBUG_LOG_isLogEnabled.invoke(null, LOG_SEVERITY_Warning,
                        DEBUG_TYPE_Leaf);
                case INFO:
                    return (boolean) DEBUG_LOG_isLogEnabled.invoke(null, LOG_SEVERITY_General,
                        DEBUG_TYPE_Leaf);
                case DEBUG:
                    return (boolean) DEBUG_LOG_isLogEnabled.invoke(null, LOG_SEVERITY_Debug,
                        DEBUG_TYPE_Leaf);
                case TRACE:
                    return (boolean) DEBUG_LOG_isLogEnabled.invoke(null, LOG_SEVERITY_Trace,
                        DEBUG_TYPE_Leaf);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke isLogEnabled in shouldLog!");
        }

        throw new IllegalArgumentException("Unknown level: " + level);
    }

    @Override
    public void close() {}
}
