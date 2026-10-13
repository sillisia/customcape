package dev.customcape.compat;

import dev.customcape.core.ImageSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * "Profile picture" style cropper: the source image is shown fitted in a box with the crop rectangle
 * overlaid. Drag to move the crop, scroll to zoom. The rectangle always has the face's aspect ratio.
 */
public class CropWidget extends AbstractWidget {
    private final ImageSpec spec;
    private final Compat.Tex tex;
    private final int srcW, srcH;
    private final float aspect;
    private final Runnable onChange;

    public CropWidget(int x, int y, int w, int h, ImageSpec spec, Compat.Tex tex, int srcW, int srcH, float aspect, Runnable onChange) {
        super(x, y, w, h, Component.literal("Crop"));
        this.spec = spec; this.tex = tex; this.srcW = srcW; this.srcH = srcH; this.aspect = aspect; this.onChange = onChange;
    }

    private float fit() { return Math.min((float) getWidth() / srcW, (float) getHeight() / srcH); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float f = fit();
        int dw = Math.round(srcW * f), dh = Math.round(srcH * f);
        int ox = getX() + (getWidth() - dw) / 2, oy = getY() + (getHeight() - dh) / 2;
        g.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY() + getHeight() + 1, 0xFF202020);
        Compat.blit(g, tex, ox, oy, dw, dh);
        float[] c = spec.cropRect(srcW, srcH, aspect);
        int x1 = ox + Math.round(c[0] * f), y1 = oy + Math.round(c[1] * f);
        int x2 = ox + Math.round((c[0] + c[2]) * f), y2 = oy + Math.round((c[1] + c[3]) * f);
        // dim everything outside the crop
        g.fill(ox, oy, ox + dw, y1, 0x99000000);
        g.fill(ox, y2, ox + dw, oy + dh, 0x99000000);
        g.fill(ox, y1, x1, y2, 0x99000000);
        g.fill(x2, y1, ox + dw, y2, 0x99000000);
        int col = 0xFFFFFFFF;
        g.fill(x1, y1, x2, y1 + 1, col); g.fill(x1, y2 - 1, x2, y2, col);
        g.fill(x1, y1, x1 + 1, y2, col); g.fill(x2 - 1, y1, x2, y2, col);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        moveTo(event.x(), event.y());
    }

    @Override
    public void onDrag(MouseButtonEvent event, double dx, double dy) {
        moveTo(event.x(), event.y());
    }

    private void moveTo(double mx, double my) {
        float f = fit();
        int dw = Math.round(srcW * f), dh = Math.round(srcH * f);
        int ox = getX() + (getWidth() - dw) / 2, oy = getY() + (getHeight() - dh) / 2;
        spec.cx = (float) Math.max(0, Math.min(1, (mx - ox) / dw));
        spec.cy = (float) Math.max(0, Math.min(1, (my - oy) / dh));
        onChange.run();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        spec.zoom = (float) Math.max(1.0, Math.min(16.0, spec.zoom * (1.0 + 0.1 * Math.signum(scrollY))));
        onChange.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
