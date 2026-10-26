#import "Plugins/Plugin.h"
#import "Plugins/PluginLoader.h"
#import "Logger.h"
#import <objc/runtime.h>

@interface PluginScope ()
@property (nonatomic, strong, readwrite) PluginManifest *manifest;
@property (nonatomic, copy, readwrite) NSString *storagePath;
@property (nonatomic, copy, readwrite) NSString *storageDir;
@property (nonatomic, assign, readwrite, getter=isEnabled) BOOL enabled;
@property (nonatomic, assign, readwrite, getter=isStartedLate) BOOL startedLate;
@end

@implementation PluginScope

- (instancetype)initWithManifest:(PluginManifest *)manifest {
    self = [super init];
    if (self) {
        _manifest = manifest;
    }
    return self;
}

- (UIApplication *)appContext {
    return [UIApplication sharedApplication];
}

- (UIViewController *)appActivity {
    UIWindow *window = nil;
    for (UIScene *scene in [UIApplication sharedApplication].connectedScenes) {
        if (scene.activationState == UISceneActivationStateForegroundActive) {
            window = ((UIWindowScene *)scene).windows.firstObject;
            if (window) break;
        }
    }
    if (!window) window = [[UIApplication sharedApplication] windows].firstObject;

    UIViewController *top = window.rootViewController;
    while (top.presentedViewController) top = top.presentedViewController;
    return top;
}

- (void)requireReload {
    [[PluginLoader sharedLoader] addPluginFlags:PluginFlagPendingReload forPlugin:self.manifest.id];
}

- (void)stop {
    [[PluginLoader sharedLoader] stopPlugin:self.manifest.id];
}

- (void)setStoragePath:(NSString *)storagePath {
    _storagePath = [storagePath copy];
    _storageDir = [storagePath copy];
}
- (void)setStorageDir:(NSString *)storageDir {
    _storageDir = [storageDir copy];
    _storagePath = [storageDir copy];
}
- (void)setStartedLate:(BOOL)startedLate {
    _startedLate = startedLate;
}

- (void)disable {
    [[PluginLoader sharedLoader] disablePlugin:self.manifest.id];
}
@end
