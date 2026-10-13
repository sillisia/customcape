package dev.customcape.mixin;

import com.google.common.collect.ImmutableMap;
import dev.customcape.compat.ThinElytra;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces the vanilla elytra wing model with a thin one (see {@link ThinElytra}). */
@Mixin(LayerDefinitions.class)
public abstract class MixinLayerDefinitions {
    // require = 0: if this ever stops matching, the elytra just falls back to vanilla instead of crashing the game.
    @Inject(method = "createRoots", at = @At("RETURN"), cancellable = true, require = 0)
    private static void customcape$thinElytra(CallbackInfoReturnable<Map<ModelLayerLocation, LayerDefinition>> cir) {
        Map<ModelLayerLocation, LayerDefinition> roots = new HashMap<>(cir.getReturnValue());
        roots.put(ModelLayers.ELYTRA, ThinElytra.createLayer());
        cir.setReturnValue(ImmutableMap.copyOf(roots));
    }
}
