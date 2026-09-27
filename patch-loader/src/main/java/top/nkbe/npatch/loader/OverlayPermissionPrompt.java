package top.nkbe.npatch.loader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Optional one-time guidance; the system and user retain control of the permission. */
final class OverlayPermissionPrompt {
    private static final String TAG = "NPatch";
    private static boolean finished;

    private OverlayPermissionPrompt() {}

    static void install() {
        XposedHelpers.findAndHookMethod(Instrumentation.class, "callActivityOnResume", Activity.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (finished || param.hasThrowable()) return;
                        Activity activity = (Activity) param.args[0];
                        activity.getWindow().getDecorView().post(() -> maybeShow(activity));
                    }
                });
    }

    private static void maybeShow(Activity activity) {
        if (finished || activity.isFinishing() || activity.isDestroyed()
                || !activity.getWindow().getDecorView().isAttachedToWindow()) return;
        File marker = new File(activity.getNoBackupFilesDir(), "apkloom/overlay-prompted");
        boolean claimed = false;
        try {
            if (marker.exists()) { finished = true; return; }
            // Atomic across app processes; no permission prompt in services/providers.
            File directory = marker.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Cannot create overlay prompt directory");
            }
            if (!marker.createNewFile()) { finished = true; return; }
            claimed = true;
            finished = true;
            if (Settings.canDrawOverlays(activity)) return;
            boolean chinese = "zh".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
            new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(chinese ? "悬浮窗权限" : "Display over other apps")
                    .setMessage(chinese
                            ? "此应用打包时启用了悬浮窗权限申请。你可以在系统设置中允许此应用显示在其他应用上层，也可以暂不授权。"
                            : "Overlay permission was enabled when this app was packaged. You can allow it to display over other apps in system settings, or skip this step.")
                    .setPositiveButton(chinese ? "前往设置" : "Open settings", (dialog, which) -> openSettings(activity, chinese))
                    .setNegativeButton(chinese ? "暂不授权" : "Not now", null)
                    .show();
            Log.i(TAG, "Wrapper overlay permission prompt shown");
        } catch (Exception error) {
            // A missing settings UI or a disappearing Activity must never break launch.
            if (claimed && !marker.delete()) Log.w(TAG, "Cannot reset overlay prompt marker");
            finished = true;
            Log.w(TAG, "Cannot show overlay permission prompt", error);
        }
    }

    private static void openSettings(Activity activity, boolean chinese) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (ActivityNotFoundException | SecurityException error) {
                Log.w(TAG, "Overlay permission settings unavailable", error);
                Toast.makeText(activity, chinese ? "请在系统设置中为此应用开启悬浮窗权限。"
                        : "Allow display over other apps for this app in system settings.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
