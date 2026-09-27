package top.nkbe.npatch.share;

/** Per-build capabilities; zero/false preserve existing wrapper behavior. */
public final class WrapperOptions {
    public static final int HTTP_ORIGINAL = 0;
    public static final int HTTP_ALLOW = 1;
    public static final int HTTP_BLOCK = 2;
    public static final WrapperOptions DEFAULT = new WrapperOptions(HTTP_ORIGINAL, false);

    public final int httpPolicy;
    public final boolean requestOverlayPermission;

    public WrapperOptions(int httpPolicy, boolean requestOverlayPermission) {
        if (httpPolicy < HTTP_ORIGINAL || httpPolicy > HTTP_BLOCK) {
            throw new IllegalArgumentException("Unknown HTTP policy: " + httpPolicy);
        }
        this.httpPolicy = httpPolicy;
        this.requestOverlayPermission = requestOverlayPermission;
    }
}
