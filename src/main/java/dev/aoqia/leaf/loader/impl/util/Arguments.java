/*
 * Copyright 2016 FabricMC
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

public final class Arguments {
    // set the game version for the builtin game mod/dependencies, bypassing auto-detection
    public static final String GAME_VERSION = SystemProperties.GAME_VERSION;
    // additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an
    // actual file)
    public static final String ADD_MODS = SystemProperties.ADD_MODS;

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
            String arg = args.get(i);

            if (arg.startsWith("-") && i < args.size() - 1) {
                String[] value = arg.split("=", 2);

                if (value[1] == null) {
                    // Give arguments that have no value an empty string.
                    value[1] = "";
                } else {
                    i += 1;
                }

                valueArgs.put(arg.substring(2), value[1]);
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
