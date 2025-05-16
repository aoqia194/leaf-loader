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
package dev.aoqia.leaf.zomboid.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import zombie.core.Core;
import zombie.iso.IsoPuddles;

import static dev.aoqia.leaf.zomboid.test.TestEntrypoint.LOGGER;

@Mixin(Core.class)
public class CoreMixin {
    @Inject(method = "getVersion", at = @At("RETURN"), cancellable = true)
    private void getVersion(CallbackInfoReturnable<String> cir) {
        LOGGER.println("Setting game version internally to 43.0.0!");
        cir.setReturnValue("43.0.0");
    }

    @ModifyExpressionValue(
        method = "initShaders",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/IsoPuddles;getInstance()Lzombie/iso/IsoPuddles;"
        )
    )
    private IsoPuddles initShaders(IsoPuddles instance) {
        LOGGER.debugln("initShaders IsoPuddles instance effect name: " + instance.Effect.name);
        return instance;
    }
}
