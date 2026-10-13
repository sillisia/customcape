package dev.customcape.core;

/** The two independently designed sides. "Back" = outside (what others see from behind), "front" = inside. */
public class SurfaceSpec {
    public FaceSpec back = new FaceSpec();
    public FaceSpec front = new FaceSpec();

    public SurfaceSpec() {
        front.gradient.stops.get(0).rgb = 0x2B2B3A;
        front.gradient.stops.get(1).rgb = 0x14141C;
    }

    public void normalize() {
        if (back == null) back = new FaceSpec();
        if (front == null) front = new FaceSpec();
        back.normalize();
        front.normalize();
    }
}
