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

package dev.aoqia.loader.impl.game.zomboid;

import java.util.OptionalInt;

public final class ZomboidVersion {
    /**
     * The id from version.json, if available.
     */
    private final String id;
    private final OptionalInt classVersion;

    private ZomboidVersion(String id, OptionalInt classVersion) {
        this.id = id;
        this.classVersion = classVersion;
    }

    public String getId() {
        return id;
    }

    public OptionalInt getClassVersion() {
        return this.classVersion;
    }

    @Override
    public String toString() {
        return String.format("ZomboidVersion{id=%s, classVersion=%s}", id, classVersion);
    }

    public static final class Builder {
        private String id;
        private OptionalInt classVersion = OptionalInt.empty();

        public Builder setClassVersion(int classVersion) {
            this.classVersion = OptionalInt.of(classVersion);
            return this;
        }

        public Builder setFromFileName(String name) {
            // strip extension
            int pos = name.lastIndexOf('.');
            if (pos > 0) {
                name = name.substring(0, pos);
            }

            return setIdLookup(name);
        }

        public Builder setIdLookup(String version) {
            return setId(ZomboidVersionLookup.getRelease(version));
        }

        // Setters
        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public ZomboidVersion build() {
            return new ZomboidVersion(this.id, this.classVersion);
        }
    }
}
