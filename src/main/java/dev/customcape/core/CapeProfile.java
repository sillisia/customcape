package dev.customcape.core;

/** One saved design. Stored as config/customcape/profiles/<name>.json */
public class CapeProfile {
    public String name = "Default";
    /** Show my custom cape (back-worn) design. */
    public boolean capeEnabled = true;
    /** Show my custom elytra design. When false the elytra uses the normal Minecraft texture. */
    public boolean elytraEnabled = true;
    /** Legacy single toggle from older profile files. Only read, migrated in normalize(), then cleared. */
    public Boolean enabled = null;
    /** Send this profile to other players that also have the mod. */
    public boolean share = true;
    public SurfaceSpec cape = new SurfaceSpec();
    /** When true the elytra reuses the cape design (re-cropped to the elytra's proportions). */
    public boolean elytraSameAsCape = true;
    public SurfaceSpec elytra = new SurfaceSpec();

    public CapeProfile() {}
    public CapeProfile(String name) { this.name = name; }

    public CapeProfile normalize() {
        if (enabled != null) { capeEnabled = enabled; elytraEnabled = enabled; enabled = null; }
        if (name == null || name.isBlank()) name = "Default";
        if (cape == null) cape = new SurfaceSpec();
        if (elytra == null) elytra = new SurfaceSpec();
        cape.normalize();
        elytra.normalize();
        return this;
    }

    /** True if at least one of cape/elytra is customised (i.e. there is something to show or share). */
    public boolean anyEnabled() { return capeEnabled || elytraEnabled; }

    public SurfaceSpec surface(boolean forElytra) { return (forElytra && !elytraSameAsCape) ? elytra : cape; }
}
