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
package dev.aoqia.leaf.loader.impl.util;

import java.util.*;

import org.jetbrains.annotations.Nullable;

public final class Arguments {
    // set the game version for the builtin game mod/dependencies, bypassing auto-detection
    public static final String GAME_VERSION = SystemProperties.GAME_VERSION;
    // additional mods to load (path separator separated paths, @ prefix for meta-file with each
    // line referencing an
    // actual file)
    public static final String ADD_MODS = SystemProperties.ADD_MODS;

    // Single args are like -arg1 -arg2 -arg3
    private final List<String> singleArgs;
    // Value args are like -arg1=somevalue -arg2=C:\folder\file.png
    // Also contains args that have no delimiter like Steam's `+connect 127.0.0.1`
    private final Map<String, String> valueArgs;
    
    public Arguments() {
        singleArgs = new ArrayList<>();
        valueArgs = new LinkedHashMap<>();
    }

    public Map<String, String> getValueArgs() {
        return Collections.unmodifiableMap(valueArgs);
    }

    public boolean containsKey(String key) {
        return valueArgs.containsKey(key);
    }

    @Nullable
    public String get(String key) {
        return valueArgs.get(key);
    }

    public String getOrDefault(String key, String value) {
        return valueArgs.getOrDefault(key, value);
    }

    public void add(String value) {
        singleArgs.add(value);
    }

    public boolean contains(String value) {
        return singleArgs.contains(value);
    }

    public void put(String key, String value) {
        valueArgs.put(key, value);
    }

    public void putIfNotExists(String key, String value) {
        if (!containsKey(key)) {
            put(key, value);
        }
    }

    public void parse(String[] args) {
        parse(Arrays.asList(args));
    }

    public void parse(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            final String arg = args.get(i);

            // If it has an equals, it's a true value arg.
            // If it starts with a plus, it's a steam arg (fake value arg).
            // Otherwise, it's a normal arg.
            if (arg.contains("=")) {
                final String[] pair = arg.split("=", 1);
                if (pair.length <= 1) {
                    throw new RuntimeException("Argument contains '=' but couldn't split.");
                }

                // Set it to nothing if the value is nothing.
                if (pair[1] == null) {
                    pair[1] = "";
                }

                valueArgs.put(pair[0].substring(1), pair[1]);
            } else {
                // Special handling for value args that look like solo args where i+1 is the value.

                // Check the next value and if it's an arg, set the value to blank.
                String value = i + 1 >= args.size() ? null : args.get(i + 1);
                if (value == null || value.startsWith("-") || value.startsWith("+")) {
                    if (arg.startsWith("-")) {
                        singleArgs.add(arg);
                        continue;
                    }

                    value = "";
                }

                // If it's a steam arg using +, dont remove it.
                // This is to explicitly differentiate between Steam and normal value args.
                // It can also be the case for args starting with - that use a space delimiter,
                // but the only such args in this game currently are bootstrapper-specific
                valueArgs.put(arg.startsWith("+") ? arg : arg.substring(1), value);
            }
        }
    }

    public String[] toArray() {
        String[] newArgs = new String[singleArgs.size() + valueArgs.size()];
        int i = 0;

        for (String s : valueArgs.keySet()) {
            if (s.startsWith("+")) {
                newArgs[i++] = s;
                newArgs[i++] = valueArgs.get(s);
                continue;
            }

            newArgs[i++] = "-" + s + "=" + valueArgs.get(s);
        }

        for (String s : singleArgs) {
            newArgs[i++] = s;
        }

        return newArgs;
    }

    public String remove(String s) {
        return valueArgs.remove(s);
    }
}
