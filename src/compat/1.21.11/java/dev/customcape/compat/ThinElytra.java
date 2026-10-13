package dev.customcape.compat;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;

/**
 * The elytra wing model, identical to vanilla (same parts, poses and UV layout) except for its depth.
 *
 * Vanilla draws each wing as a 10x20x2 box inflated by 1 on every side, i.e. 12x22x4: the wing is 4 model units
 * thick, which looks like a chunky slab from the side. Here the wing keeps its 12x22 size and outside-face position
 * but is only about 1 unit thick, the same thickness as a cape.
 */
public final class ThinElytra {
    private ThinElytra() {}

    /** Wing depth after deformation is 2 + 2 * (-0.45) = 1.1 units, spanning z = 1.95 .. 3.05 (vanilla: -1 .. 3). */
    private static final float GROW_XY = 1.0F;
    private static final float GROW_Z = -0.45F;
    private static final float BOX_Z = 1.5F;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation grow = new CubeDeformation(GROW_XY, GROW_XY, GROW_Z);
        root.addOrReplaceChild("left_wing",
            CubeListBuilder.create().texOffs(22, 0).addBox(-10.0F, 0.0F, BOX_Z, 10.0F, 20.0F, 2.0F, grow),
            PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.2617994F, 0.0F, -0.2617994F));
        root.addOrReplaceChild("right_wing",
            CubeListBuilder.create().texOffs(22, 0).mirror().addBox(0.0F, 0.0F, BOX_Z, 10.0F, 20.0F, 2.0F, grow),
            PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.2617994F, 0.0F, 0.2617994F));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
