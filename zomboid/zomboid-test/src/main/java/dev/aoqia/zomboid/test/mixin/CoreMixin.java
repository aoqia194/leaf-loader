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

package dev.aoqia.zomboid.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.Core;

@Mixin(Core.class)
public class CoreMixin {
    @Inject(method = "getDebug", at = @At("HEAD"), cancellable = true)
    private void getDebug(CallbackInfoReturnable<Boolean> cir) {
        System.out.println("CoreMixin -> Forcing getDebug() to FALSE.");
        cir.setReturnValue(false);
        cir.cancel();
    }
}
