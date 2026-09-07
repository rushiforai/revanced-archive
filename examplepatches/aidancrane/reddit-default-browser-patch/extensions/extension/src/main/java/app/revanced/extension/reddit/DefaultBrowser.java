// SPDX-FileCopyrightText: 2026 Aidan
// SPDX-License-Identifier: MIT OR GPL-3.0-only

package app.revanced.extension.reddit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

/** Runtime hook used by the Reddit patch. */
public final class DefaultBrowser {
    private DefaultBrowser() {}

    /**
     * Opens the URI with Android's configured default browser. No browser package is hard-coded.
     * Returns false if Android cannot identify or launch a default browser, allowing Reddit's
     * original in-app-browser path to remain as a safe fallback.
     */
    public static boolean open(Activity activity, Uri uri) {
        return open((Context) activity, uri);
    }

    /** Hook for Reddit's full-bleed-player outbound-link route. */
    public static boolean open(Context context, String url) {
        if (url == null) return false;
        return open(context, Uri.parse(url));
    }

    private static boolean open(Context context, Uri uri) {
        if (context == null || uri == null) return false;

        try {
            PackageManager packageManager = context.getPackageManager();
            Intent browserProbe = new Intent(Intent.ACTION_VIEW, Uri.parse("http://"));
            browserProbe.addCategory(Intent.CATEGORY_BROWSABLE);
            ResolveInfo defaultHandler = packageManager.resolveActivity(
                    browserProbe,
                    PackageManager.MATCH_DEFAULT_ONLY
            );

            if (defaultHandler == null || defaultHandler.activityInfo == null) return false;
            String browserPackage = defaultHandler.activityInfo.packageName;
            if (browserPackage == null
                    || browserPackage.equals("android")
                    || browserPackage.equals(context.getPackageName())) return false;

            Intent external = new Intent(Intent.ACTION_VIEW, uri);
            external.addCategory(Intent.CATEGORY_BROWSABLE);
            external.setPackage(browserPackage);
            if (!(context instanceof Activity)) {
                external.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(external);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
