package dev.customcape.client;

import com.google.gson.Gson;
import dev.customcape.compat.Compat;
import dev.customcape.compat.CropWidget;
import dev.customcape.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The Custom Cape settings screen, opened from Mod Menu.
 *
 * Layout: tabs along the top (General / Cape / Elytra / Profiles), a scrollable list of option rows on the
 * left (label on the left, control on the right, a small reset button after every row), and a panel on the
 * right that describes the option you are pointing at, shows a live preview, and holds Search / Reset /
 * Undo / Done. Plain vanilla widgets only (no config library). Every change is applied to your in-game
 * cape immediately and saved to config/customcape/profiles/.
 */
public class EditorScreen extends Screen {
    private static final Gson GSON = new Gson();

    private static final Identifier SPR_BTN = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier SPR_BTN_HI = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier SPR_BTN_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier SPR_HANDLE = Identifier.withDefaultNamespace("widget/slider_handle");
    private static final Identifier SPR_HANDLE_HI = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");

    private static final int HEAD_H = 24, GAP = 4, ROW_H = 20, RESET_W = 22;
    private static final int WHITE = 0xFFFFFFFF, GREY = 0xFFA0A0A0, DIM = 0xFF606060, GOLD = 0xFFFFFF55;

    private static final String[] TAB_NAMES = {"General", "Cape", "Elytra", "Profiles"};
    private static final String[] TAB_TEXT = {
        "Turn your custom cape and elytra on or off, and choose whether other players can see them.",
        "Design the cape worn on your back. The back side is what others see from behind; the front side is the inside.",
        "Design your elytra wings, or reuse your cape design. If the custom elytra is off, the normal Minecraft texture is used.",
        "Switch between saved designs, or create, rename, duplicate and delete them."};

    private final Screen parent;

    // ---- state that survives a window resize
    private int tab = 0, scroll = 0, saveTicks = 0, statusTicks = 0, undoTicks = 0;
    private String query = "", status = "", snapshot = "";
    private long deleteArmedAt = 0;
    private final Set<String> collapsed = new HashSet<>(List.of("cape.front", "elytra.front"));
    private final Map<String, SrcTex> srcTex = new HashMap<>();
    private final Compat.Tex[] prev = new Compat.Tex[4]; // cape back/front, elytra back/front

    // ---- layout (computed in init)
    private int rw, listRight, listTop, listBottom, cx, cw, searchY;
    private TabNavigationBar tabBar;
    private EditBox searchBox;
    private Button resetAll, undoBtn;

    // ---- rows
    private final List<Row> rows = new ArrayList<>();
    private boolean rowsDirty = true;
    private int contentH = 0;
    private Row lastHover;

    private static final class SrcTex {
        Compat.Tex tex;
        String file;
    }

    public EditorScreen(Screen parent) {
        super(Component.literal("Custom Cape"));
        this.parent = parent;
    }

    // ================================================================ helpers

    private static CapeProfile P() { return LocalState.profile(); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void edited() {
        LocalState.changed();
        refreshPreview();
    }

    private void say(String s) { status = s; statusTicks = 80; }

    private void takeSnapshot() { snapshot = GSON.toJson(P()); }

    private static String pretty(String id) { return id.replace('_', ' '); }

    // ================================================================ init / layout

    @Override
    protected void init() {
        rw = clamp(width / 3, 170, 300);
        listRight = width - rw;
        listTop = HEAD_H + 6;
        listBottom = height - 6;
        cx = 8;
        cw = listRight - 8 - 12;
        if (snapshot.isEmpty()) takeSnapshot();

        // ---- tab bar (vanilla widget; tabs have no children - we switch our own row list)
        Tab[] tabs = new Tab[TAB_NAMES.length];
        for (int i = 0; i < tabs.length; i++) tabs[i] = new SimpleTab(TAB_NAMES[i]);
        TabManager manager = new TabManager(this::addRenderableWidget, this::removeWidget, t -> {
            for (int i = 0; i < tabs.length; i++)
                if (tabs[i] == t && i != tab) { tab = i; scroll = 0; rowsDirty = true; lastHover = null; }
        }, t -> {});
        tabBar = TabNavigationBar.builder(manager, width).addTabs(tabs).build();
        addRenderableWidget(tabBar);
        tabBar.selectTab(tab, false);
        tabBar.setWidth(width);
        tabBar.arrangeElements();

        // ---- right panel: search, reset / undo, done
        int rpx = width - rw + 8, rpw = rw - 16;
        int doneY = height - 6 - 20;
        int ruY = doneY - 4 - 20;
        searchY = ruY - 6 - 18;
        searchBox = new EditBox(font, rpx, searchY, rpw, 18, Component.literal("Search"));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search options..."));
        searchBox.setValue(query);
        searchBox.setResponder(t -> { query = t.trim().toLowerCase(Locale.ROOT); scroll = 0; rowsDirty = true; });
        addRenderableWidget(searchBox);

        int half = (rpw - 4) / 2;
        resetAll = Button.builder(Component.literal("Reset"), b -> resetVisible()).bounds(rpx, ruY, half, 20).build();
        undoBtn = Button.builder(Component.literal("Undo"), b -> undo()).bounds(rpx + half + 4, ruY, rpw - half - 4, 20).build();
        addRenderableWidget(resetAll);
        addRenderableWidget(undoBtn);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(rpx, doneY, rpw, 20).build());

        rows.clear(); // widgets were already cleared by the framework
        rebuildRows();
        refreshPreview();
    }

    private static final class SimpleTab implements Tab {
        private final Component title;
        SimpleTab(String t) { title = Component.literal(t); }
        @Override public Component getTabTitle() { return title; }
        @Override public Component getTabExtraNarration() { return Component.empty(); }
        @Override public void visitChildren(Consumer<AbstractWidget> c) {}
        @Override public void doLayout(ScreenRectangle r) {}
    }

    // ================================================================ row list management

    private void clearRowWidgets() {
        for (Row r : rows) {
            for (AbstractWidget w : r.extras) removeWidget(w);
            if (r.resetBtn != null) removeWidget(r.resetBtn);
            removeWidget(r);
        }
        rows.clear();
    }

    private void rebuildRows() {
        rowsDirty = false;
        clearRowWidgets();
        switch (tab) {
            case 0 -> buildGeneral();
            case 1 -> buildCape();
            case 2 -> buildElytra();
            default -> buildProfiles();
        }
        if (!query.isEmpty()) {
            rows.removeIf(r -> r instanceof HeadingRow || r instanceof InfoRow
                || !(r.name + " " + r.tags).toLowerCase(Locale.ROOT).contains(query));
        }
        int y = 0;
        for (Row r : rows) {
            r.baseY = y;
            r.setWidth(r.full ? cw : cw - RESET_W - GAP);
            y += r.getHeight() + GAP;
        }
        contentH = y;
        scroll = clamp(scroll, 0, maxScroll());
        for (Row r : rows) {
            for (AbstractWidget w : r.extras) addWidget(w);
            if (r.resetBtn != null) addWidget(r.resetBtn);
            addWidget(r);
        }
        applyScroll();
        if (lastHover == null || !rows.contains(lastHover)) lastHover = null;
    }

    private int maxScroll() { return Math.max(0, contentH - (listBottom - listTop)); }

    private void applyScroll() {
        for (Row r : rows) {
            int ry = listTop + r.baseY - scroll;
            r.setX(cx);
            r.setY(ry);
            r.layoutExtras();
            if (r.resetBtn != null) {
                r.resetBtn.setX(cx + cw - RESET_W);
                r.resetBtn.setY(ry);
                r.resetBtn.setHeight(r.getHeight());
            }
        }
    }

    private <T extends Row> T add(T r) { rows.add(r); return r; }

    // ================================================================ tabs

    private void buildGeneral() {
        add(new HeadingRow("General", null, null));
        add(toggleRow("Custom Cape Enabled",
            "Show your custom cape on your back. When off, your normal Minecraft cape (if you have one) is shown instead. The elytra is not affected.",
            () -> P().capeEnabled, v -> P().capeEnabled = v, true));
        add(toggleRow("Custom Elytra Enabled",
            "Show your custom design on your elytra. When off, the normal Minecraft elytra texture is shown. The cape is not affected.",
            () -> P().elytraEnabled, v -> P().elytraEnabled = v, true));
        add(toggleRow("Share With Other Players",
            "Upload your design so other players who have this mod can see it.",
            () -> P().share, v -> P().share = v, true));

        // The "Sync Server URL" option is intentionally not shown in the UI for now. The value is still read
        // from config/customcape/settings.json ("serverUrl") by WebSync, so all sync functionality is unchanged.

        add(new ActionRow("Open Images Folder", "Opens the folder where your cape images are stored. You can also drag a picture onto this window.",
            () -> Compat.openFolder(LocalState.store().imagesDir())));
        add(new InfoRow("Active profile: " + P().name + "  (change it on the Profiles tab)"));
    }

    private void buildCape() {
        add(new HeadingRow("Cape", null, null));
        add(toggleRow("Custom Cape Enabled",
            "Show your custom cape on your back. When off, your normal Minecraft cape (if you have one) is shown instead.",
            () -> P().capeEnabled, v -> P().capeEnabled = v, true));
        faceSection("Back (Outside)", "cape.back", P().cape.back, false, false);
        faceSection("Front (Inside)", "cape.front", P().cape.front, true, false);
    }

    private void buildElytra() {
        add(new HeadingRow("Elytra", null, null));
        add(toggleRow("Custom Elytra Enabled",
            "Show your custom design on your elytra. When off, the normal Minecraft elytra texture is shown.",
            () -> P().elytraEnabled, v -> P().elytraEnabled = v, true));
        Row same = toggleRow("Use Cape Design",
            "Re-use your cape design on the elytra (re-cropped to the wing proportions). Turn off to design the elytra separately.",
            () -> P().elytraSameAsCape, v -> P().elytraSameAsCape = v, true);
        same.rebuildAfter = true;
        add(same);
        if (P().elytraSameAsCape) {
            add(new InfoRow("The elytra is using your cape design. Turn off \"Use Cape Design\" to edit it separately."));
        } else {
            faceSection("Back (Outside)", "elytra.back", P().elytra.back, false, true);
            faceSection("Front (Inside)", "elytra.front", P().elytra.front, true, true);
        }
    }

    private void buildProfiles() {
        add(new HeadingRow("Profiles", null, null));
        CapeProfile p = P();
        EditBox nb = new ClipEdit(font, 0, 0, 100, 16, Component.literal("Profile name"));
        nb.setMaxLength(32);
        nb.setValue(p.name);
        Btn rename = new Btn(() -> "Rename", d -> {
            say(LocalState.rename(nb.getValue()) ? "Renamed." : "That name is already used.");
            takeSnapshot();
            rowsDirty = true;
        });
        add(new TextRow("Profile Name", "Name of the active profile. Press Rename to apply it.", nb, rename));

        for (String n : LocalState.store().names()) {
            boolean sel = n.equals(p.name);
            add(new ActionRow((sel ? "> " : "") + n, sel ? "This is your active profile." : "Switch to this profile.", () -> {
                if (sel) return;
                LocalState.select(n);
                takeSnapshot();
                deleteArmedAt = 0;
                rowsDirty = true;
                refreshPreview();
            })).selected = sel;
        }
        add(new ActionRow("New Profile", "Create a fresh profile and switch to it.", () -> {
            LocalState.create("New cape");
            takeSnapshot(); rowsDirty = true; refreshPreview();
        }));
        add(new ActionRow("Duplicate Profile", "Make a copy of the active profile and switch to the copy.", () -> {
            LocalState.duplicate();
            takeSnapshot(); rowsDirty = true; refreshPreview();
        }));
        boolean armed = System.currentTimeMillis() - deleteArmedAt < 3000;
        add(new ActionRow(armed ? "Click Again To Delete" : "Delete Profile", "Permanently delete the active profile. You cannot delete your last profile.", () -> {
            if (LocalState.store().names().size() <= 1) { say("You can't delete your only profile."); return; }
            if (System.currentTimeMillis() - deleteArmedAt < 3000) {
                LocalState.deleteActive();
                deleteArmedAt = 0;
                takeSnapshot(); refreshPreview();
            } else deleteArmedAt = System.currentTimeMillis();
            rowsDirty = true;
        }));
    }

    // ================================================================ face sections

    private void resetFace(FaceSpec f, boolean front) {
        FaceSpec d = front ? new SurfaceSpec().front : new SurfaceSpec().back;
        f.type = d.type; f.gradient = d.gradient; f.banner = d.banner; f.image = d.image;
    }

    private void faceSection(String title, String key, FaceSpec f, boolean front, boolean elytra) {
        int start = rows.size();
        FaceSpec def = front ? new SurfaceSpec().front : new SurfaceSpec().back;
        boolean open = !query.isEmpty() || !collapsed.contains(key);
        add(new HeadingRow(title, key, () -> resetFace(f, front)));
        if (open) {
            FaceSpec.Type[] types = FaceSpec.Type.values();
            Row type = cycleRow("Design Type", "How this side is drawn: a colour gradient, a banner pattern, or your own picture.",
                () -> switch (f.type) { case GRADIENT -> "Gradient"; case BANNER -> "Banner"; default -> "Image"; },
                d -> f.type = types[Math.floorMod(f.type.ordinal() + d, types.length)],
                () -> f.type == def.type, () -> f.type = def.type, null);
            type.rebuildAfter = true;
            add(type);
            switch (f.type) {
                case GRADIENT -> gradientRows(f);
                case BANNER -> bannerRows(f);
                case IMAGE -> imageRows(f, key, elytra);
            }
        }
        for (int i = start; i < rows.size(); i++) rows.get(i).tags = title + (elytra ? " elytra" : " cape");
    }

    // ---------------------------------------------------------------- gradient

    private void gradientRows(FaceSpec f) {
        GradientSpec gr = f.gradient;
        for (int i = 0; i < gr.stops.size(); i++) {
            GradientSpec.Stop s = gr.stops.get(i);
            add(new StopRow("Colour " + (i + 1), s, () -> {
                if (gr.stops.size() > 1) { gr.stops.remove(s); edited(); rowsDirty = true; }
            }));
        }
        add(new ActionRow("+ Add Colour Stop", "Add another colour to the gradient.", () -> {
            GradientSpec.Stop last = gr.stops.get(gr.stops.size() - 1);
            gr.stops.add(new GradientSpec.Stop(1f, last.rgb));
            edited();
            rowsDirty = true;
        }));
        GradientSpec d = new GradientSpec();
        add(toggleRow("Radial Gradient", "Spread the colours outward from the start point instead of along a line.",
            () -> gr.radial, v -> gr.radial = v, false));
        Function<Float, String> pct = v -> Math.round(v * 100) + "%";
        add(sliderRow("Start X", "Where the gradient begins, left to right.", 0, 1, () -> gr.startX, v -> gr.startX = v, d.startX, pct));
        add(sliderRow("Start Y", "Where the gradient begins, top to bottom.", 0, 1, () -> gr.startY, v -> gr.startY = v, d.startY, pct));
        add(sliderRow("End X", "Where the gradient ends, left to right.", 0, 1, () -> gr.endX, v -> gr.endX = v, d.endX, pct));
        add(sliderRow("End Y", "Where the gradient ends, top to bottom.", 0, 1, () -> gr.endY, v -> gr.endY = v, d.endY, pct));
    }

    // ---------------------------------------------------------------- banner

    private void bannerRows(FaceSpec f) {
        BannerSpec b = f.banner;
        Dye defBase = new BannerSpec().base;
        add(cycleRow("Base Colour", "The dye colour the banner starts as. Click to cycle, right-click to go back.",
            () -> b.base.pretty(), d -> b.base = b.base.shift(d), () -> b.base == defBase, () -> b.base = defBase, () -> b.base.rgb));
        for (int i = 0; i < b.layers.size(); i++) add(new LayerRow(i, b));
        add(new ActionRow("+ Add Pattern Layer", "Add a banner pattern on top. Layers draw bottom to top, like a loom.", () -> {
            b.layers.add(new BannerSpec.Layer("stripe_center", Dye.WHITE));
            edited();
            rowsDirty = true;
        }));
    }

    // ---------------------------------------------------------------- image

    private void imageRows(FaceSpec f, String key, boolean elytra) {
        ImageSpec im = f.image;
        ProfileStore store = LocalState.store();
        Row imgRow = cycleRow("Image File", "Pick a picture from your images folder. Click to cycle, right-click to go back. Drag a file onto this window to import it.",
            () -> im.file == null ? "(none)" : im.file,
            d -> {
                List<String> files = store.listImages();
                if (files.isEmpty()) return;
                int idx = files.indexOf(im.file);
                im.file = files.get(Math.floorMod(idx < 0 && d > 0 ? 0 : idx + d, files.size()));
                im.cx = im.cy = 0.5f;
                im.zoom = 1f;
                rowsDirty = true;
            },
            () -> im.file == null, () -> im.file = null, null);
        imgRow.rebuildAfter = true;
        add(imgRow);

        Raster src = im.file == null ? null : store.loadImage(im.file);
        if (src == null) {
            add(new InfoRow(store.listImages().isEmpty()
                ? "No images yet. Drag a PNG or JPG onto this window, or put one in the images folder."
                : "Use the Image File option above to pick a picture."));
            return;
        }
        SrcTex st = srcTex.computeIfAbsent(key, k -> new SrcTex());
        if (!im.file.equals(st.file)) {
            Compat.release(st.tex);
            int m = Math.max(src.w, src.h);
            Raster pv = m > 512 ? src.resize(Math.max(1, src.w * 512 / m), Math.max(1, src.h * 512 / m)) : src;
            st.tex = Compat.createTexture("src", pv);
            st.file = im.file;
        }
        float aspect = 10f / (elytra ? 20f : 16f);
        int cwid = Math.min(220, cw - RESET_W - GAP - 12);
        add(new CropRow(new ClipCrop(cwid, 100, im, st.tex, src.w, src.h, aspect)));
        add(sliderRow("Zoom", "Zoom into the picture. You can also scroll over the crop box.", 1, 16, () -> im.zoom, v -> im.zoom = v, 1f, v -> String.format("%.1fx", v)));
        add(toggleRow("Flip Horizontally", "Mirror the picture left to right.", () -> im.flipH, v -> im.flipH = v, false));
    }

    // ================================================================ row factories

    private ToggleRow toggleRow(String name, String desc, BooleanSupplier get, Consumer<Boolean> set, boolean def) {
        ToggleRow r = new ToggleRow(name, desc, get, v -> { set.accept(v); edited(); });
        r.withReset(() -> get.getAsBoolean() == def, () -> set.accept(def));
        return r;
    }

    private CycleRow cycleRow(String name, String desc, Supplier<String> value, IntConsumer step,
                              BooleanSupplier atDefault, Runnable reset, IntSupplier swatch) {
        CycleRow r = new CycleRow(name, desc, value, d -> { step.accept(d); edited(); }, swatch);
        r.withReset(atDefault, reset);
        return r;
    }

    private SliderRow sliderRow(String name, String desc, float min, float max, Supplier<Float> get, Consumer<Float> set,
                                float def, Function<Float, String> fmt) {
        SliderRow r = new SliderRow(name, desc, min, max, get, v -> { set.accept(v); edited(); }, fmt);
        r.withReset(() -> Math.abs(get.get() - def) < 1e-4f, () -> set.accept(def));
        return r;
    }

    // ================================================================ reset / undo

    private void resetVisible() {
        int n = 0;
        for (Row r : rows) if (r.resetBtn != null) { r.resetBtn.reset.run(); n++; }
        edited();
        rowsDirty = true;
        say(n == 0 ? "Nothing to reset here." : "Reset " + n + " option" + (n == 1 ? "" : "s") + " to default.");
    }

    private void undo() {
        CapeProfile snap = GSON.fromJson(snapshot, CapeProfile.class);
        if (snap == null) return;
        LocalState.restore(snap);
        refreshPreview();
        rowsDirty = true;
        say("Reverted to how this profile was when you opened the screen.");
    }

    // ================================================================ widgets

    /** Edit box that ignores the mouse when scrolled out of the list area. */
    private class ClipEdit extends EditBox {
        ClipEdit(net.minecraft.client.gui.Font f, int x, int y, int w, int h, Component msg) { super(f, x, y, w, h, msg); }
        @Override public boolean isMouseOver(double mx, double my) { return my >= listTop && my < listBottom && super.isMouseOver(mx, my); }
    }

    private class ClipCrop extends CropWidget {
        ClipCrop(int w, int h, ImageSpec im, Compat.Tex tex, int sw, int sh, float aspect) {
            super(0, 0, w, h, im, tex, sw, sh, aspect, EditorScreen.this::edited);
        }
        @Override public boolean isMouseOver(double mx, double my) { return my >= listTop && my < listBottom && super.isMouseOver(mx, my); }
    }

    private void box(GuiGraphics g, AbstractWidget w, boolean dark) {
        Identifier spr = dark ? SPR_BTN_OFF : (w.isHoveredOrFocused() ? SPR_BTN_HI : SPR_BTN);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, spr, w.getX(), w.getY(), w.getWidth(), w.getHeight());
    }

    private void swatch(GuiGraphics g, int x, int y, int rgb) {
        g.fill(x - 1, y - 1, x + 13, y + 13, WHITE);
        g.fill(x, y, x + 12, y + 12, 0xFF000000 | rgb);
    }

    /** Small button used inside rows. Left-click steps +1 (or runs), right-click steps -1. */
    private class Btn extends AbstractWidget {
        private final Supplier<String> label;
        private final IntConsumer step;
        Btn(Supplier<String> label, IntConsumer step) {
            super(0, 0, 40, 16, Component.empty());
            this.label = label;
            this.step = step;
        }
        @Override public boolean isMouseOver(double mx, double my) { return my >= listTop && my < listBottom && super.isMouseOver(mx, my); }
        @Override protected boolean isValidClickButton(MouseButtonInfo info) { return info.button() == 0 || info.button() == 1; }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) { step.accept(e.button() == 1 ? -1 : 1); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, false);
            String s = label.get();
            int maxW = getWidth() - 6;
            if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, maxW, false);
            g.drawString(font, s, getX() + (getWidth() - font.width(s)) / 2, getY() + (getHeight() - 8) / 2, WHITE);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput o) {}
    }

    /** The little reset square after each option. */
    private class ResetBtn extends AbstractWidget {
        final BooleanSupplier atDefault;
        final Runnable reset;
        ResetBtn(BooleanSupplier atDefault, Runnable reset) {
            super(0, 0, RESET_W, ROW_H, Component.literal("Reset"));
            this.atDefault = atDefault;
            this.reset = reset;
        }
        @Override public boolean isMouseOver(double mx, double my) { return my >= listTop && my < listBottom && super.isMouseOver(mx, my); }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) { reset.run(); edited(); rowsDirty = true; }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean def = atDefault != null && atDefault.getAsBoolean();
            g.blitSprite(RenderPipelines.GUI_TEXTURED, (!def && isHoveredOrFocused()) ? SPR_BTN_HI : SPR_BTN_OFF, getX(), getY(), getWidth(), getHeight());
            String s = "\u21BB";
            g.drawString(font, s, getX() + (getWidth() - font.width(s)) / 2, getY() + (getHeight() - 8) / 2, def ? DIM : WHITE);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput o) {}
    }

    // ---------------------------------------------------------------- rows

    private abstract class Row extends AbstractWidget {
        final String name, desc;
        String tags = "";
        final List<AbstractWidget> extras = new ArrayList<>();
        ResetBtn resetBtn;
        int baseY;
        boolean full;           // spans the reset column too (headings, info text)
        boolean rebuildAfter;   // clicking changes which rows exist

        Row(String name, String desc, int h) {
            super(0, 0, 100, h, Component.literal(name));
            this.name = name;
            this.desc = desc == null ? "" : desc;
        }
        void withReset(BooleanSupplier atDefault, Runnable reset) { resetBtn = new ResetBtn(atDefault, reset); }
        void layoutExtras() {}
        void finished() { if (rebuildAfter) rowsDirty = true; }
        @Override public boolean isMouseOver(double mx, double my) { return my >= listTop && my < listBottom && super.isMouseOver(mx, my); }
        @Override protected void updateWidgetNarration(NarrationElementOutput o) {}
        void label(GuiGraphics g, int color) {
            g.drawString(font, name, getX() + 6, getY() + (getHeight() - 8) / 2, color);
        }
        int midY() { return getY() + (getHeight() - 8) / 2; }
    }

    private class HeadingRow extends Row {
        private final String key;
        HeadingRow(String title, String key, Runnable resetFace) {
            super(title, null, ROW_H);
            this.key = key;
            this.full = key == null;
            if (key != null) withReset(() -> false, resetFace);
        }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) {
            if (key == null) return;
            if (!collapsed.remove(key)) collapsed.add(key);
            rowsDirty = true;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Component t = Component.literal(name).withStyle(ChatFormatting.BOLD);
            if (key != null && isHoveredOrFocused()) g.fill(getX(), getY(), getRight(), getBottom(), 0x30FFFFFF);
            g.drawCenteredString(font, t, getX() + getWidth() / 2, midY(), WHITE);
            boolean open = !collapsed.contains(key) || !query.isEmpty();
            g.drawString(font, key == null ? "\u25BC" : (open ? "\u25BC" : "\u25B6"), getX() + 6, midY(), key == null ? GREY : WHITE);
        }
    }

    private class InfoRow extends Row {
        private final List<FormattedCharSequence> lines;
        InfoRow(String text) {
            super("", null, 10);
            this.full = true;
            lines = font.split(FormattedText.of(text), cw - 16);
            setHeight(Math.max(1, lines.size()) * 10 + 2);
        }
        @Override public boolean isMouseOver(double mx, double my) { return false; }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int y = getY() + 1;
            for (FormattedCharSequence l : lines) { g.drawString(font, l, getX() + 8, y, GREY); y += 10; }
        }
    }

    private class ToggleRow extends Row {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        ToggleRow(String name, String desc, BooleanSupplier get, Consumer<Boolean> set) {
            super(name, desc, ROW_H);
            this.get = get;
            this.set = set;
        }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) { set.accept(!get.getAsBoolean()); finished(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, false);
            label(g, WHITE);
            int bx = getRight() - 18, by = getY() + (getHeight() - 14) / 2;
            g.fill(bx, by, bx + 14, by + 14, 0xFFE0E0E0);
            g.fill(bx + 1, by + 1, bx + 13, by + 13, 0xFF202020);
            if (get.getAsBoolean()) g.fill(bx + 3, by + 3, bx + 11, by + 11, WHITE);
        }
    }

    private class CycleRow extends Row {
        private final Supplier<String> value;
        private final IntConsumer step;
        private final IntSupplier swatch;
        CycleRow(String name, String desc, Supplier<String> value, IntConsumer step, IntSupplier swatch) {
            super(name, desc, ROW_H);
            this.value = value;
            this.step = step;
            this.swatch = swatch;
        }
        @Override protected boolean isValidClickButton(MouseButtonInfo info) { return info.button() == 0 || info.button() == 1; }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) { step.accept(e.button() == 1 ? -1 : 1); finished(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, false);
            label(g, WHITE);
            String v = value.get();
            int maxW = getWidth() / 2 - 20;
            if (font.width(v) > maxW) v = font.plainSubstrByWidth(v, maxW, false);
            int right = getRight() - 8;
            g.drawString(font, v, right - font.width(v), midY(), GOLD);
            if (swatch != null) swatch(g, right - font.width(v) - 18, getY() + (getHeight() - 12) / 2, swatch.getAsInt());
        }
    }

    private class ActionRow extends Row {
        private final Runnable run;
        boolean selected;
        ActionRow(String name, String desc, Runnable run) {
            super(name, desc, ROW_H);
            this.run = run;
        }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) { run.run(); finished(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, false);
            String s = name;
            if (font.width(s) > getWidth() - 12) s = font.plainSubstrByWidth(s, getWidth() - 12, false);
            g.drawString(font, s, getX() + (getWidth() - font.width(s)) / 2, midY(), selected ? GOLD : WHITE);
        }
    }

    private class SliderRow extends Row {
        private final float min, max;
        private final Supplier<Float> get;
        private final Consumer<Float> set;
        private final Function<Float, String> fmt;
        SliderRow(String name, String desc, float min, float max, Supplier<Float> get, Consumer<Float> set, Function<Float, String> fmt) {
            super(name, desc, ROW_H);
            this.min = min; this.max = max; this.get = get; this.set = set; this.fmt = fmt;
        }
        private void setFrom(double mx) {
            double t = (mx - (getX() + 4)) / (double) (getWidth() - 8);
            t = Math.max(0, Math.min(1, t));
            set.accept(min + (float) t * (max - min));
        }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) { setFrom(e.x()); }
        @Override protected void onDrag(MouseButtonEvent e, double dx, double dy) { setFrom(e.x()); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, true);
            float v = get.get();
            float t = (v - min) / (max - min);
            int hx = getX() + Math.round(t * (getWidth() - 8));
            g.blitSprite(RenderPipelines.GUI_TEXTURED, isHoveredOrFocused() ? SPR_HANDLE_HI : SPR_HANDLE, hx, getY(), 8, getHeight());
            label(g, isHoveredOrFocused() ? WHITE : GREY);
            String s = fmt.apply(v);
            g.drawString(font, s, getRight() - 12 - font.width(s), midY(), isHoveredOrFocused() ? WHITE : GREY);
        }
    }

    private class TextRow extends Row {
        private final EditBox box;
        private final Btn trailing;
        TextRow(String name, String desc, EditBox box, Btn trailing) {
            super(name, desc, ROW_H);
            this.box = box;
            this.trailing = trailing;
            extras.add(box);
            if (trailing != null) extras.add(trailing);
        }
        @Override public void onClick(MouseButtonEvent e, boolean dbl) { setFocused(false); box.setFocused(true); EditorScreen.this.setFocused(box); }
        @Override void layoutExtras() {
            int right = getRight() - 4;
            if (trailing != null) {
                trailing.setWidth(54);
                trailing.setPosition(right - 54, getY() + 2);
                right -= 58;
            }
            int bw = Math.max(60, Math.min(box.getWidth(), getWidth() / 2));
            box.setWidth(bw);
            box.setHeight(16);
            box.setPosition(right - bw, getY() + 2);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, true);
            label(g, GREY);
        }
    }

    private class StopRow extends Row {
        private final GradientSpec.Stop stop;
        private final EditBox hex, pos;
        private final Btn del;
        StopRow(String name, GradientSpec.Stop s, Runnable remove) {
            super(name, "A colour in the gradient: its hex colour (RRGGBB) and where it sits along the gradient line, in percent.", ROW_H);
            this.stop = s;
            hex = new ClipEdit(font, 0, 0, 54, 16, Component.literal("Colour"));
            hex.setMaxLength(6);
            hex.setHint(Component.literal("RRGGBB"));
            hex.setValue(String.format("%06X", s.rgb));
            hex.setResponder(t -> {
                if (t.length() == 6) try { s.rgb = Integer.parseInt(t, 16); edited(); } catch (NumberFormatException ignored) {}
            });
            pos = new ClipEdit(font, 0, 0, 34, 16, Component.literal("Position"));
            pos.setMaxLength(3);
            pos.setHint(Component.literal("%"));
            pos.setValue(String.valueOf(Math.round(s.pos * 100)));
            pos.setResponder(t -> {
                try { s.pos = clamp(Integer.parseInt(t.trim()), 0, 100) / 100f; edited(); } catch (NumberFormatException ignored) {}
            });
            del = new Btn(() -> "x", d -> remove.run());
            extras.add(hex); extras.add(pos); extras.add(del);
        }
        @Override void layoutExtras() {
            int y = getY() + 2, right = getRight() - 4;
            del.setWidth(16); del.setPosition(right - 16, y);
            pos.setWidth(34); pos.setPosition(right - 16 - 2 - 34, y);
            hex.setWidth(54); hex.setPosition(right - 16 - 2 - 34 - 2 - 54, y);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, true);
            label(g, WHITE);
            swatch(g, hex.getX() - 16, getY() + (getHeight() - 12) / 2, stop.rgb);
        }
    }

    private class LayerRow extends Row {
        private final Btn pattern, color, del;
        LayerRow(int index, BannerSpec b) {
            super("Layer " + (index + 1), "A banner pattern layer: pattern and dye colour. Click to cycle, right-click to go back. Layers draw bottom to top.", ROW_H);
            BannerSpec.Layer l = b.layers.get(index);
            pattern = new Btn(() -> pretty(l.pattern), d -> {
                List<String> all = BannerSpec.PATTERNS;
                l.pattern = all.get(Math.floorMod(Math.max(0, all.indexOf(l.pattern)) + d, all.size()));
                edited();
            });
            color = new Btn(() -> l.color.pretty(), d -> { l.color = l.color.shift(d); edited(); });
            del = new Btn(() -> "x", d -> { b.layers.remove(l); edited(); rowsDirty = true; });
            extras.add(pattern); extras.add(color); extras.add(del);
        }
        @Override void layoutExtras() {
            int y = getY() + 2, right = getRight() - 4;
            del.setWidth(16); del.setPosition(right - 16, y);
            int avail = Math.max(60, getWidth() - 56 - 16 - 8);
            int cwid = Math.max(40, avail * 2 / 5), pwid = avail - cwid - 2;
            color.setWidth(cwid); color.setPosition(right - 16 - 2 - cwid, y);
            pattern.setWidth(pwid); pattern.setPosition(right - 16 - 2 - cwid - 2 - pwid, y);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            box(g, this, true);
            label(g, WHITE);
        }
    }

    private class CropRow extends Row {
        private final CropWidget crop;
        CropRow(CropWidget crop) {
            super("Crop", "Choose which part of the picture is used. Drag to move the box, scroll to zoom.", 108);
            this.crop = crop;
            extras.add(crop);
        }
        @Override public boolean isMouseOver(double mx, double my) { return false; }
        @Override void layoutExtras() {
            crop.setPosition(getX() + (getWidth() - crop.getWidth()) / 2, getY() + 4);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {}
    }

    // ================================================================ preview / render

    private void refreshPreview() {
        CapeProfile p = P();
        for (int i = 0; i < 4; i++) {
            Raster r = LocalState.face(p, i >= 2, (i & 1) == 1, 4);
            if (prev[i] == null) prev[i] = Compat.createTexture("prev_" + i, r); else Compat.update(prev[i], r);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, HEAD_H, listRight, height, 0x38000000);
        g.fill(listRight, HEAD_H, width, height, 0x90000000);
        g.fill(listRight - 1, HEAD_H, listRight, height, 0xFF000000);
        g.fill(listRight, HEAD_H, listRight + 1, height, 0xFF3A3A3A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (rowsDirty) rebuildRows();
        super.render(g, mouseX, mouseY, partialTick);

        // ---- option list (clipped so rows slide under the tab bar)
        g.enableScissor(0, listTop, listRight, listBottom);
        Row hover = null;
        for (Row r : rows) {
            if (r.getBottom() < listTop || r.getY() > listBottom) continue;
            r.render(g, mouseX, mouseY, partialTick);
            for (AbstractWidget w : r.extras) w.render(g, mouseX, mouseY, partialTick);
            if (r.resetBtn != null) r.resetBtn.render(g, mouseX, mouseY, partialTick);
            boolean over = r.isMouseOver(mouseX, mouseY) || (r.resetBtn != null && r.resetBtn.isMouseOver(mouseX, mouseY));
            for (AbstractWidget w : r.extras) over |= w.isMouseOver(mouseX, mouseY);
            if (over && !(r instanceof InfoRow)) hover = r;
        }
        g.disableScissor();
        if (hover != null) lastHover = hover;

        if (rows.isEmpty()) g.drawCenteredString(font, "No options match your search.", listRight / 2, listTop + 20, GREY);

        // scrollbar
        int viewH = listBottom - listTop;
        if (contentH > viewH) {
            int bx = listRight - 7;
            int th = Math.max(16, viewH * viewH / contentH);
            int ty = listTop + (int) ((long) (viewH - th) * scroll / Math.max(1, maxScroll()));
            g.fill(bx, listTop, bx + 3, listBottom, 0x60000000);
            g.fill(bx, ty, bx + 3, ty + th, 0xFFA0A0A0);
        }

        renderSidePanel(g);
    }

    private void renderSidePanel(GuiGraphics g) {
        int px = listRight + 8, pw = rw - 16, y = HEAD_H + 8;
        String title = lastHover != null ? lastHover.name : TAB_NAMES[tab];
        String text = lastHover != null && !lastHover.desc.isEmpty() ? lastHover.desc : TAB_TEXT[tab];
        if (lastHover instanceof HeadingRow h && h.desc.isEmpty()) text = "Click to expand or collapse this section. The reset button restores the whole section to defaults.";
        g.drawString(font, Component.literal(title).withStyle(ChatFormatting.BOLD), px, y, WHITE);
        y += 14;
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), pw);
        int maxLines = Math.max(1, (searchY - 8 - y - 70) / 10);
        for (int i = 0; i < lines.size() && i < maxLines; i++) { g.drawString(font, lines.get(i), px, y, 0xFFE0E0E0); y += 10; }
        y += 6;

        if (statusTicks > 0 && !status.isEmpty()) {
            for (FormattedCharSequence l : font.split(FormattedText.of(status), pw)) { g.drawString(font, l, px, y, GOLD); y += 10; }
            y += 4;
        }

        // ---- live preview of the four faces
        int avail = searchY - 10 - y - 24;
        if (avail < 40) return;
        CapeProfile p = P();
        int pwid = Math.min(36, (pw - 12) / 4);
        pwid = Math.min(pwid, avail / 2);
        int gap = (pw - 4 * pwid) / 3;
        int top = y + 12;
        g.drawString(font, "Cape", px, y, GREY);
        g.drawString(font, "Elytra", px + 2 * (pwid + gap), y, GREY);
        for (int i = 0; i < 4; i++) {
            boolean el = i >= 2;
            int x = px + i * (pwid + gap) + (i >= 2 ? 0 : 0);
            int h = el ? pwid * 2 : pwid * 16 / 10;
            Compat.blit(g, prev[i], x, top, pwid, h);
            boolean on = el ? p.elytraEnabled : p.capeEnabled;
            if (!on) {
                g.fill(x, top, x + pwid, top + h, 0xB0000000);
                g.drawCenteredString(font, "Off", x + pwid / 2, top + h / 2 - 4, GREY);
            }
        }
        g.drawString(font, "Back  /  Front  (front = inside)", px, top + pwid * 2 + 4, DIM);
    }

    // ================================================================ input / lifecycle

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (super.mouseScrolled(mx, my, sx, sy)) return true;
        if (mx < listRight && my >= listTop && my < listBottom) {
            scroll = clamp(scroll - (int) Math.round(sy * 20), 0, maxScroll());
            applyScroll();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (++saveTicks >= 20) { saveTicks = 0; LocalState.flush(); }
        if (statusTicks > 0) statusTicks--;
        if (++undoTicks >= 5 && undoBtn != null) {
            undoTicks = 0;
            undoBtn.active = !GSON.toJson(P()).equals(snapshot);
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        int n = 0;
        for (Path src : paths) {
            String fn = src.getFileName().toString();
            String l = fn.toLowerCase();
            if (!(l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".gif") || l.endsWith(".bmp"))) continue;
            try {
                Files.copy(src, LocalState.store().imagesDir().resolve(fn.replaceAll("[^A-Za-z0-9 _.-]", "_")), StandardCopyOption.REPLACE_EXISTING);
                n++;
            } catch (Exception ignored) {}
        }
        say(n == 0 ? "No images imported." : "Imported " + n + " image(s). Pick them with the Image File option.");
        rowsDirty = true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        LocalState.flush();
        LocalState.publish();
        for (int i = 0; i < 4; i++) { Compat.release(prev[i]); prev[i] = null; }
        for (SrcTex st : srcTex.values()) Compat.release(st.tex);
        srcTex.clear();
    }
}
