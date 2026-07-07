package app.revanced.extension.dcinside;

// AUTO-GENERATED from android.jar (API 35) by Gen.java. Do not hand-edit.
// Delegates every overridable PackageManager instance method to the real instance;
// only getPackageInfo(...) is intercepted to inject the original signature.
@SuppressWarnings({"unchecked","deprecation","rawtypes"})
public final class SpoofPackageManager extends android.content.pm.PackageManager {
    private final android.content.pm.PackageManager real;
    public SpoofPackageManager(android.content.pm.PackageManager real) { this.real = real; }

    public android.content.pm.PackageManager.Property getProperty(java.lang.String a0, android.content.ComponentName a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getProperty(a0, a1);
    }
    public android.content.pm.PackageManager.Property getProperty(java.lang.String a0, java.lang.String a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getProperty(a0, a1);
    }
    public int checkPermission(java.lang.String a0, java.lang.String a1) {
        return real.checkPermission(a0, a1);
    }
    public android.content.pm.PackageInfo getPackageInfo(java.lang.String a0, android.content.pm.PackageManager.PackageInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return app.revanced.extension.dcinside.SignatureSpoof.maybeSpoof(real.getPackageInfo(a0, a1), (int) a1.getValue());
    }
    public android.content.pm.PackageInfo getPackageInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return app.revanced.extension.dcinside.SignatureSpoof.maybeSpoof(real.getPackageInfo(a0, a1), a1);
    }
    public android.content.pm.PackageInfo getPackageInfo(android.content.pm.VersionedPackage a0, android.content.pm.PackageManager.PackageInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return app.revanced.extension.dcinside.SignatureSpoof.maybeSpoof(real.getPackageInfo(a0, a1), (int) a1.getValue());
    }
    public android.content.pm.PackageInfo getPackageInfo(android.content.pm.VersionedPackage a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return app.revanced.extension.dcinside.SignatureSpoof.maybeSpoof(real.getPackageInfo(a0, a1), a1);
    }
    public java.lang.CharSequence getText(java.lang.String a0, int a1, android.content.pm.ApplicationInfo a2) {
        return real.getText(a0, a1, a2);
    }
    public java.util.List<android.content.pm.PermissionGroupInfo> getAllPermissionGroups(int a0) {
        return real.getAllPermissionGroups(a0);
    }
    public boolean setAutoRevokeWhitelisted(java.lang.String a0, boolean a1) {
        return real.setAutoRevokeWhitelisted(a0, a1);
    }
    public boolean isAutoRevokeWhitelisted(java.lang.String a0) {
        return real.isAutoRevokeWhitelisted(a0);
    }
    public boolean isAutoRevokeWhitelisted() {
        return real.isAutoRevokeWhitelisted();
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(android.content.Intent a0, android.content.pm.PackageManager.ResolveInfoFlags a1) {
        return real.queryIntentActivities(a0, a1);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(android.content.Intent a0, int a1) {
        return real.queryIntentActivities(a0, a1);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentActivityOptions(android.content.ComponentName a0, java.util.List<android.content.Intent> a1, android.content.Intent a2, android.content.pm.PackageManager.ResolveInfoFlags a3) {
        return real.queryIntentActivityOptions(a0, a1, a2, a3);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentActivityOptions(android.content.ComponentName a0, android.content.Intent[] a1, android.content.Intent a2, int a3) {
        return real.queryIntentActivityOptions(a0, a1, a2, a3);
    }
    public java.util.List<android.content.pm.PermissionInfo> queryPermissionsByGroup(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.queryPermissionsByGroup(a0, a1);
    }
    public android.content.pm.PermissionGroupInfo getPermissionGroupInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getPermissionGroupInfo(a0, a1);
    }
    public java.util.List<android.content.pm.ModuleInfo> getInstalledModules(int a0) {
        return real.getInstalledModules(a0);
    }
    public java.util.List<android.content.pm.ApplicationInfo> getInstalledApplications(int a0) {
        return real.getInstalledApplications(a0);
    }
    public java.util.List<android.content.pm.ApplicationInfo> getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags a0) {
        return real.getInstalledApplications(a0);
    }
    public byte[] getInstantAppCookie() {
        return real.getInstantAppCookie();
    }
    public void clearInstantAppCookie() {
        real.clearInstantAppCookie();
    }
    public void updateInstantAppCookie(byte[] a0) {
        real.updateInstantAppCookie(a0);
    }
    public android.content.pm.FeatureInfo[] getSystemAvailableFeatures() {
        return real.getSystemAvailableFeatures();
    }
    public android.content.Intent getLaunchIntentForPackage(java.lang.String a0) {
        return real.getLaunchIntentForPackage(a0);
    }
    public int getTargetSdkVersion(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getTargetSdkVersion(a0);
    }
    public java.util.List<android.content.pm.PackageInfo> getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags a0) {
        return real.getInstalledPackages(a0);
    }
    public java.util.List<android.content.pm.PackageInfo> getInstalledPackages(int a0) {
        return real.getInstalledPackages(a0);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryBroadcastReceivers(android.content.Intent a0, android.content.pm.PackageManager.ResolveInfoFlags a1) {
        return real.queryBroadcastReceivers(a0, a1);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryBroadcastReceivers(android.content.Intent a0, int a1) {
        return real.queryBroadcastReceivers(a0, a1);
    }
    public java.util.List<android.content.pm.InstrumentationInfo> queryInstrumentation(java.lang.String a0, int a1) {
        return real.queryInstrumentation(a0, a1);
    }
    public android.content.res.Resources getResourcesForActivity(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getResourcesForActivity(a0);
    }
    public android.content.res.Resources getResourcesForApplication(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getResourcesForApplication(a0);
    }
    public android.content.res.Resources getResourcesForApplication(android.content.pm.ApplicationInfo a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getResourcesForApplication(a0);
    }
    public android.content.res.Resources getResourcesForApplication(android.content.pm.ApplicationInfo a0, android.content.res.Configuration a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getResourcesForApplication(a0, a1);
    }
    public android.content.pm.ProviderInfo resolveContentProvider(java.lang.String a0, int a1) {
        return real.resolveContentProvider(a0, a1);
    }
    public android.content.pm.ProviderInfo resolveContentProvider(java.lang.String a0, android.content.pm.PackageManager.ComponentInfoFlags a1) {
        return real.resolveContentProvider(a0, a1);
    }
    public java.util.List<android.content.pm.ProviderInfo> queryContentProviders(java.lang.String a0, int a1, android.content.pm.PackageManager.ComponentInfoFlags a2) {
        return real.queryContentProviders(a0, a1, a2);
    }
    public java.util.List<android.content.pm.ProviderInfo> queryContentProviders(java.lang.String a0, int a1, int a2) {
        return real.queryContentProviders(a0, a1, a2);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentServices(android.content.Intent a0, int a1) {
        return real.queryIntentServices(a0, a1);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentServices(android.content.Intent a0, android.content.pm.PackageManager.ResolveInfoFlags a1) {
        return real.queryIntentServices(a0, a1);
    }
    public android.content.pm.InstrumentationInfo getInstrumentationInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getInstrumentationInfo(a0, a1);
    }
    public android.graphics.drawable.Drawable getDefaultActivityIcon() {
        return real.getDefaultActivityIcon();
    }
    public android.graphics.drawable.Drawable getApplicationBanner(android.content.pm.ApplicationInfo a0) {
        return real.getApplicationBanner(a0);
    }
    public android.graphics.drawable.Drawable getApplicationBanner(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getApplicationBanner(a0);
    }
    public java.lang.CharSequence getApplicationLabel(android.content.pm.ApplicationInfo a0) {
        return real.getApplicationLabel(a0);
    }
    public java.util.Set<java.lang.String> getWhitelistedRestrictedPermissions(java.lang.String a0, int a1) {
        return real.getWhitelistedRestrictedPermissions(a0, a1);
    }
    public boolean getSyntheticAppDetailsActivityEnabled(java.lang.String a0) {
        return real.getSyntheticAppDetailsActivityEnabled(a0);
    }
    public boolean removeWhitelistedRestrictedPermission(java.lang.String a0, java.lang.String a1, int a2) {
        return real.removeWhitelistedRestrictedPermission(a0, a1, a2);
    }
    public boolean isSafeMode() {
        return real.isSafeMode();
    }
    public android.content.res.XmlResourceParser getXml(java.lang.String a0, int a1, android.content.pm.ApplicationInfo a2) {
        return real.getXml(a0, a1, a2);
    }
    public int getApplicationEnabledSetting(java.lang.String a0) {
        return real.getApplicationEnabledSetting(a0);
    }
    public java.lang.CharSequence getBackgroundPermissionOptionLabel() {
        return real.getBackgroundPermissionOptionLabel();
    }
    public void setApplicationEnabledSetting(java.lang.String a0, int a1, int a2) {
        real.setApplicationEnabledSetting(a0, a1, a2);
    }
    public android.os.Bundle getSuspendedPackageAppExtras() {
        return real.getSuspendedPackageAppExtras();
    }
    public java.util.List<android.content.pm.PackageInfo> getPackagesHoldingPermissions(java.lang.String[] a0, int a1) {
        return real.getPackagesHoldingPermissions(a0, a1);
    }
    public java.util.List<android.content.pm.PackageInfo> getPackagesHoldingPermissions(java.lang.String[] a0, android.content.pm.PackageManager.PackageInfoFlags a1) {
        return real.getPackagesHoldingPermissions(a0, a1);
    }
    public java.lang.String[] canonicalToCurrentPackageNames(java.lang.String[] a0) {
        return real.canonicalToCurrentPackageNames(a0);
    }
    public void getPlatformPermissionsForGroup(java.lang.String a0, java.util.concurrent.Executor a1, java.util.function.Consumer<java.util.List<java.lang.String>> a2) {
        real.getPlatformPermissionsForGroup(a0, a1, a2);
    }
    public int getInstantAppCookieMaxBytes() {
        return real.getInstantAppCookieMaxBytes();
    }
    public void clearPackagePreferredActivities(java.lang.String a0) {
        real.clearPackagePreferredActivities(a0);
    }
    public java.lang.String[] currentToCanonicalPackageNames(java.lang.String[] a0) {
        return real.currentToCanonicalPackageNames(a0);
    }
    public boolean addWhitelistedRestrictedPermission(java.lang.String a0, java.lang.String a1, int a2) {
        return real.addWhitelistedRestrictedPermission(a0, a1, a2);
    }
    public android.content.Intent getLeanbackLaunchIntentForPackage(java.lang.String a0) {
        return real.getLeanbackLaunchIntentForPackage(a0);
    }
    public java.lang.String[] getSystemSharedLibraryNames() {
        return real.getSystemSharedLibraryNames();
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentContentProviders(android.content.Intent a0, int a1) {
        return real.queryIntentContentProviders(a0, a1);
    }
    public java.util.List<android.content.pm.ResolveInfo> queryIntentContentProviders(android.content.Intent a0, android.content.pm.PackageManager.ResolveInfoFlags a1) {
        return real.queryIntentContentProviders(a0, a1);
    }
    public void setComponentEnabledSettings(java.util.List<android.content.pm.PackageManager.ComponentEnabledSetting> a0) {
        real.setComponentEnabledSettings(a0);
    }
    public android.graphics.drawable.Drawable getUserBadgedDrawableForDensity(android.graphics.drawable.Drawable a0, android.os.UserHandle a1, android.graphics.Rect a2, int a3) {
        return real.getUserBadgedDrawableForDensity(a0, a1, a2, a3);
    }
    public android.content.IntentSender getLaunchIntentSenderForPackage(java.lang.String a0) {
        return real.getLaunchIntentSenderForPackage(a0);
    }
    public boolean isPermissionRevokedByPolicy(java.lang.String a0, java.lang.String a1) {
        return real.isPermissionRevokedByPolicy(a0, a1);
    }
    public void getGroupOfPlatformPermission(java.lang.String a0, java.util.concurrent.Executor a1, java.util.function.Consumer<java.lang.String> a2) {
        real.getGroupOfPlatformPermission(a0, a1, a2);
    }
    public android.content.pm.ResolveInfo resolveActivity(android.content.Intent a0, android.content.pm.PackageManager.ResolveInfoFlags a1) {
        return real.resolveActivity(a0, a1);
    }
    public android.content.pm.ResolveInfo resolveActivity(android.content.Intent a0, int a1) {
        return real.resolveActivity(a0, a1);
    }
    public android.graphics.drawable.Drawable getActivityIcon(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityIcon(a0);
    }
    public android.graphics.drawable.Drawable getActivityIcon(android.content.Intent a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityIcon(a0);
    }
    public android.graphics.drawable.Drawable getActivityLogo(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityLogo(a0);
    }
    public android.graphics.drawable.Drawable getActivityLogo(android.content.Intent a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityLogo(a0);
    }
    public int getPackageUid(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getPackageUid(a0, a1);
    }
    public int getPackageUid(java.lang.String a0, android.content.pm.PackageManager.PackageInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getPackageUid(a0, a1);
    }
    public boolean isAppArchivable(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.isAppArchivable(a0);
    }
    public boolean isPackageStopped(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.isPackageStopped(a0);
    }
    public boolean[] canPackageQuery(java.lang.String a0, java.lang.String[] a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.canPackageQuery(a0, a1);
    }
    public boolean canPackageQuery(java.lang.String a0, java.lang.String a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.canPackageQuery(a0, a1);
    }
    public boolean isInstantApp(java.lang.String a0) {
        return real.isInstantApp(a0);
    }
    public boolean isInstantApp() {
        return real.isInstantApp();
    }
    public android.graphics.drawable.Drawable getUserBadgedIcon(android.graphics.drawable.Drawable a0, android.os.UserHandle a1) {
        return real.getUserBadgedIcon(a0, a1);
    }
    public android.graphics.drawable.Drawable getApplicationIcon(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getApplicationIcon(a0);
    }
    public android.graphics.drawable.Drawable getApplicationIcon(android.content.pm.ApplicationInfo a0) {
        return real.getApplicationIcon(a0);
    }
    public android.graphics.drawable.Drawable getActivityBanner(android.content.Intent a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityBanner(a0);
    }
    public android.graphics.drawable.Drawable getActivityBanner(android.content.ComponentName a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityBanner(a0);
    }
    public java.lang.CharSequence getUserBadgedLabel(java.lang.CharSequence a0, android.os.UserHandle a1) {
        return real.getUserBadgedLabel(a0, a1);
    }
    public android.content.pm.ActivityInfo getReceiverInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getReceiverInfo(a0, a1);
    }
    public android.content.pm.ActivityInfo getReceiverInfo(android.content.ComponentName a0, android.content.pm.PackageManager.ComponentInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getReceiverInfo(a0, a1);
    }
    public boolean isPackageSuspended() {
        return real.isPackageSuspended();
    }
    public boolean isPackageSuspended(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.isPackageSuspended(a0);
    }
    public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityInfo(a0, a1);
    }
    public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName a0, android.content.pm.PackageManager.ComponentInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getActivityInfo(a0, a1);
    }
    public boolean addPermission(android.content.pm.PermissionInfo a0) {
        return real.addPermission(a0);
    }
    public boolean addPermissionAsync(android.content.pm.PermissionInfo a0) {
        return real.addPermissionAsync(a0);
    }
    public void removePermission(java.lang.String a0) {
        real.removePermission(a0);
    }
    public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName a0, android.content.pm.PackageManager.ComponentInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getServiceInfo(a0, a1);
    }
    public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getServiceInfo(a0, a1);
    }
    public android.content.pm.ModuleInfo getModuleInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getModuleInfo(a0, a1);
    }
    public java.lang.String[] getPackagesForUid(int a0) {
        return real.getPackagesForUid(a0);
    }
    public android.graphics.drawable.Drawable getApplicationLogo(android.content.pm.ApplicationInfo a0) {
        return real.getApplicationLogo(a0);
    }
    public android.graphics.drawable.Drawable getApplicationLogo(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getApplicationLogo(a0);
    }
    public void requestChecksums(java.lang.String a0, boolean a1, int a2, java.util.List<java.security.cert.Certificate> a3, android.content.pm.PackageManager.OnChecksumsReadyListener a4) throws java.security.cert.CertificateEncodingException, android.content.pm.PackageManager.NameNotFoundException {
        real.requestChecksums(a0, a1, a2, a3, a4);
    }
    public android.content.pm.ProviderInfo getProviderInfo(android.content.ComponentName a0, android.content.pm.PackageManager.ComponentInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getProviderInfo(a0, a1);
    }
    public android.content.pm.ProviderInfo getProviderInfo(android.content.ComponentName a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getProviderInfo(a0, a1);
    }
    public boolean isDeviceUpgrading() {
        return real.isDeviceUpgrading();
    }
    public android.graphics.drawable.Drawable getDrawable(java.lang.String a0, int a1, android.content.pm.ApplicationInfo a2) {
        return real.getDrawable(a0, a1, a2);
    }
    public android.content.pm.PermissionInfo getPermissionInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getPermissionInfo(a0, a1);
    }
    public android.content.pm.ResolveInfo resolveService(android.content.Intent a0, int a1) {
        return real.resolveService(a0, a1);
    }
    public android.content.pm.ResolveInfo resolveService(android.content.Intent a0, android.content.pm.PackageManager.ResolveInfoFlags a1) {
        return real.resolveService(a0, a1);
    }
    public android.content.pm.ChangedPackages getChangedPackages(int a0) {
        return real.getChangedPackages(a0);
    }
    public void setMimeGroup(java.lang.String a0, java.util.Set<java.lang.String> a1) {
        real.setMimeGroup(a0, a1);
    }
    public java.util.Set<java.lang.String> getMimeGroup(java.lang.String a0) {
        return real.getMimeGroup(a0);
    }
    public android.content.pm.ArchivedPackageInfo getArchivedPackage(java.lang.String a0) {
        return real.getArchivedPackage(a0);
    }
    public int checkSignatures(java.lang.String a0, java.lang.String a1) {
        return real.checkSignatures(a0, a1);
    }
    public int checkSignatures(int a0, int a1) {
        return real.checkSignatures(a0, a1);
    }
    public android.content.pm.ApplicationInfo getApplicationInfo(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getApplicationInfo(a0, a1);
    }
    public android.content.pm.ApplicationInfo getApplicationInfo(java.lang.String a0, android.content.pm.PackageManager.ApplicationInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getApplicationInfo(a0, a1);
    }
    public int[] getPackageGids(java.lang.String a0, android.content.pm.PackageManager.PackageInfoFlags a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getPackageGids(a0, a1);
    }
    public int[] getPackageGids(java.lang.String a0, int a1) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getPackageGids(a0, a1);
    }
    public int[] getPackageGids(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getPackageGids(a0);
    }
    public java.lang.String getNameForUid(int a0) {
        return real.getNameForUid(a0);
    }
    public java.util.List<android.content.pm.SharedLibraryInfo> getSharedLibraries(int a0) {
        return real.getSharedLibraries(a0);
    }
    public java.util.List<android.content.pm.SharedLibraryInfo> getSharedLibraries(android.content.pm.PackageManager.PackageInfoFlags a0) {
        return real.getSharedLibraries(a0);
    }
    public boolean hasSystemFeature(java.lang.String a0, int a1) {
        return real.hasSystemFeature(a0, a1);
    }
    public boolean hasSystemFeature(java.lang.String a0) {
        return real.hasSystemFeature(a0);
    }
    public void addPackageToPreferred(java.lang.String a0) {
        real.addPackageToPreferred(a0);
    }
    public java.util.List<android.content.pm.PackageManager.Property> queryReceiverProperty(java.lang.String a0) {
        return real.queryReceiverProperty(a0);
    }
    public void setApplicationCategoryHint(java.lang.String a0, int a1) {
        real.setApplicationCategoryHint(a0, a1);
    }
    public int getPreferredActivities(java.util.List<android.content.IntentFilter> a0, java.util.List<android.content.ComponentName> a1, java.lang.String a2) {
        return real.getPreferredActivities(a0, a1, a2);
    }
    public void setComponentEnabledSetting(android.content.ComponentName a0, int a1, int a2) {
        real.setComponentEnabledSetting(a0, a1, a2);
    }
    public void verifyPendingInstall(int a0, int a1) {
        real.verifyPendingInstall(a0, a1);
    }
    public void setInstallerPackageName(java.lang.String a0, java.lang.String a1) {
        real.setInstallerPackageName(a0, a1);
    }
    public boolean isDefaultApplicationIcon(android.graphics.drawable.Drawable a0) {
        return real.isDefaultApplicationIcon(a0);
    }
    public void addPreferredActivity(android.content.IntentFilter a0, int a1, android.content.ComponentName[] a2, android.content.ComponentName a3) {
        real.addPreferredActivity(a0, a1, a2, a3);
    }
    public boolean canRequestPackageInstalls() {
        return real.canRequestPackageInstalls();
    }
    public void extendVerificationTimeout(int a0, int a1, long a2) {
        real.extendVerificationTimeout(a0, a1, a2);
    }
    public <T> T parseAndroidManifest(java.io.File a0, java.util.function.Function<android.content.res.XmlResourceParser, T> a1) throws java.io.IOException {
        return real.parseAndroidManifest(a0, a1);
    }
    public int getComponentEnabledSetting(android.content.ComponentName a0) {
        return real.getComponentEnabledSetting(a0);
    }
    public java.util.List<android.content.pm.PackageManager.Property> queryProviderProperty(java.lang.String a0) {
        return real.queryProviderProperty(a0);
    }
    public java.util.List<android.content.pm.PackageManager.Property> queryApplicationProperty(java.lang.String a0) {
        return real.queryApplicationProperty(a0);
    }
    public android.content.pm.InstallSourceInfo getInstallSourceInfo(java.lang.String a0) throws android.content.pm.PackageManager.NameNotFoundException {
        return real.getInstallSourceInfo(a0);
    }
    public java.util.List<android.content.pm.PackageInfo> getPreferredPackages(int a0) {
        return real.getPreferredPackages(a0);
    }
    public android.content.pm.PackageInstaller getPackageInstaller() {
        return real.getPackageInstaller();
    }
    public java.util.List<android.content.pm.PackageManager.Property> queryServiceProperty(java.lang.String a0) {
        return real.queryServiceProperty(a0);
    }
    public java.lang.String getInstallerPackageName(java.lang.String a0) {
        return real.getInstallerPackageName(a0);
    }
    public void relinquishUpdateOwnership(java.lang.String a0) {
        real.relinquishUpdateOwnership(a0);
    }
    public boolean hasSigningCertificate(java.lang.String a0, byte[] a1, int a2) {
        return real.hasSigningCertificate(a0, a1, a2);
    }
    public boolean hasSigningCertificate(int a0, byte[] a1, int a2) {
        return real.hasSigningCertificate(a0, a1, a2);
    }
    public java.util.List<android.content.pm.PackageManager.Property> queryActivityProperty(java.lang.String a0) {
        return real.queryActivityProperty(a0);
    }
    public void removePackageFromPreferred(java.lang.String a0) {
        real.removePackageFromPreferred(a0);
    }
    public android.content.pm.PackageInfo getPackageArchiveInfo(java.lang.String a0, int a1) {
        return real.getPackageArchiveInfo(a0, a1);
    }
    public android.content.pm.PackageInfo getPackageArchiveInfo(java.lang.String a0, android.content.pm.PackageManager.PackageInfoFlags a1) {
        return real.getPackageArchiveInfo(a0, a1);
    }
}

