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

package net.aoqia.loader.impl.game.zomboid;

public final class ZomboidVersion {
    /**
     * The game's build version, such as {@code 41.78.16}.
     *
     * <p>This is derived from the version.json's id and name fields if available, otherwise through other sources.
     */
    private final String buildVersion;

    private ZomboidVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getBuildVersion() {
        return this.buildVersion;
    }

    @Override
    public String toString() {
        return String.format("ZomboidVersion{version=%s}", buildVersion);
    }

    public static final class Builder {
        // The build number of the game.
        private String buildVersion;

        // Setters

        public Builder setBuildVersion(String buildVersion) {
            this.buildVersion = buildVersion;
            return this;
        }

        public ZomboidVersion build() {
            return new ZomboidVersion(this.buildVersion);
        }
    }
}
