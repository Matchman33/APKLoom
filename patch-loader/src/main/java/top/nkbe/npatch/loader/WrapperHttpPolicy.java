package top.nkbe.npatch.loader;

import android.security.NetworkSecurityPolicy;
import android.util.Log;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import top.nkbe.npatch.share.WrapperOptions;

/** Overrides only cleartext decisions; certificate validation and trust stores remain intact. */
final class WrapperHttpPolicy {
    private WrapperHttpPolicy() {}

    static void install(int policy) {
        if (policy == WrapperOptions.HTTP_ORIGINAL) return;
        if (policy != WrapperOptions.HTTP_ALLOW && policy != WrapperOptions.HTTP_BLOCK) {
            throw new IllegalArgumentException("Unknown wrapper HTTP policy: " + policy);
        }
        boolean allowed = policy == WrapperOptions.HTTP_ALLOW;
        // Cover global and per-host decisions, including applications that already
        // declare domain-specific networkSecurityConfig rules in the embedded APK.
        XposedHelpers.findAndHookMethod(NetworkSecurityPolicy.class, "isCleartextTrafficPermitted",
                XC_MethodReplacement.returnConstant(allowed));
        XposedHelpers.findAndHookMethod(NetworkSecurityPolicy.class, "isCleartextTrafficPermitted", String.class,
                XC_MethodReplacement.returnConstant(allowed));
        XposedHelpers.findAndHookMethod("android.security.net.config.NetworkSecurityConfig", null,
                "isCleartextTrafficPermitted", XC_MethodReplacement.returnConstant(allowed));
        Log.i("NPatch", "Wrapper HTTP policy: " + (allowed ? "allow" : "block"));
    }
}
