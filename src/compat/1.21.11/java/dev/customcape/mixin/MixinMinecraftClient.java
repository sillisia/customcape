package dev.customcape.mixin;

import dev.customcape.client.LocalState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftClient {
    private boolean customcape$initializedTextures = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void customcape$initializeTextures(CallbackInfo ci) {
        if (!customcape$initializedTextures) {
            customcape$initializedTextures = true;
            LocalState.rebuildTextures();
        }
    }
}
