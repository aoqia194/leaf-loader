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

package net.aoqia.zomboid.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.Core;
import zombie.core.GameVersion;

@Mixin(value = Core.class, remap = false)
public class CoreMixin {
    @Inject(method = "getGameVersion", at = @At("HEAD"))
    private GameVersion getGameVersion(CallbackInfoReturnable<GameVersion> cir) {
        System.out.println("getGameVersion() called. Hello from CoreMixin!");

        GameVersion ver = cir.getReturnValue();
        return new GameVersion(ver.getMajor(), ver.getMinor(), "leaf");
    }
}
