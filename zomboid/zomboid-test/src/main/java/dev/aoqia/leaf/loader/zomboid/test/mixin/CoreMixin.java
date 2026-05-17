package dev.aoqia.leaf.loader.zomboid.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import zombie.core.Core;
import zombie.iso.IsoPuddles;

import static dev.aoqia.leaf.loader.zomboid.test.TestEntrypoint.GAME_LOGGER;

@Mixin(Core.class)
public class CoreMixin {
	@Inject(method = "getVersion", at = @At("HEAD"), cancellable = true)
	private void getVersion(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue("43.0.0");
		cir.cancel();
	}

	@ModifyExpressionValue(
			method = "initShaders",
			at = @At(
					value = "INVOKE",
					target = "Lzombie/iso/IsoPuddles;getInstance()Lzombie/iso/IsoPuddles;"
			)
	)
	private IsoPuddles initShaders(IsoPuddles instance) {
		GAME_LOGGER.warn("IsoPuddles::initShaders instance effect name: {}",
				instance.Effect.getName());
		return instance;
	}
}