package top.nkbe.npatch.share;

/** Per-build capabilities; false preserves existing wrapper behavior. */
public final class WrapperOptions {
    public static final WrapperOptions DEFAULT = new WrapperOptions(false);

    public final boolean requestOverlayPermission;

    public WrapperOptions(boolean requestOverlayPermission) {
        this.requestOverlayPermission = requestOverlayPermission;
    }
}
