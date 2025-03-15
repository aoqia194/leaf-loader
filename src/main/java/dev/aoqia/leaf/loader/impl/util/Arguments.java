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
    // Arguments that present like solo args but are actually value args. Special handling required.
    private final static List<String> valueArgNames = Collections.unmodifiableList(
        Arrays.asList("-pzexeconfig", "-pzexelog"));
    // Solo args are like -arg1 -arg2 -arg3
    private final List<String> soloArgs;
    // Value args are like -arg1=somevalue -arg2=C:\folder\file.png
    private final Map<String, String> valueArgs;

    public Arguments() {
        soloArgs = new ArrayList<>();
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
        soloArgs.add(value);
    }

    public boolean contains(String value) {
        return soloArgs.contains(value);
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

            // Value arg if = is present, otherwise solo arg.
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
            } else if (valueArgNames.contains(arg)) {
                // Special handling for value args that look like solo args where i+1 is the value.
                final String value = i + 1 > args.size() ? null : args.get(i + 1);
                if (value == null || value.startsWith("-")) {
                    // Silently ignore? I don't know if this is the best choice but ok!
                    // throw new RuntimeException(
                    //     "Invalid game arguments provided. Found vararg with no value.");
                    continue;
                }

                valueArgs.put(arg.substring(1), value);
            } else {
                soloArgs.add(arg);
            }
        }
    }

    public String[] toArray() {
        String[] newArgs = new String[soloArgs.size() * 2 + valueArgs.size()];
        int i = 0;

        for (String s : soloArgs) {
            newArgs[i++] = "-" + s;
        }

        for (String s : valueArgs.keySet()) {
            newArgs[i++] = "-" + s + "=" + valueArgs.get(s);
        }

        return newArgs;
    }

    public String remove(String s) {
        return valueArgs.remove(s);
    }
}
