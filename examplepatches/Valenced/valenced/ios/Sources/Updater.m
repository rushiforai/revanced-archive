#import "Updater.h"
#import "Constants.h"
#import "Utils.h"
#import "Logger.h"

// Release builds only run the shipped bundle, so the updater exists in debug builds only.
#ifdef DEBUG

static const NSTimeInterval UpdaterTimeout = 10.0;
static const NSTimeInterval UpdaterTimeoutCached = 5.0;

@implementation LoaderConfig

- (instancetype)init {
    self = [super init];
    if (self) {
        self.customLoadUrlEnabled = NO;
        self.customLoadUrl        = nil;
    }
    return self;
}

- (void)load {
    NSFileManager *fm = [NSFileManager defaultManager];
    NSURL *configURL = [getValencedDirectory() URLByAppendingPathComponent:CONFIG_PATH];
    if (![fm fileExistsAtPath:configURL.path]) return;

    NSData *data = [NSData dataWithContentsOfURL:configURL];
    NSDictionary *json = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
    if (![json isKindOfClass:[NSDictionary class]]) return;

    NSDictionary *customLoadUrl = json[@"customLoadUrl"];
    if (![customLoadUrl isKindOfClass:[NSDictionary class]]) return;
    self.customLoadUrlEnabled = [customLoadUrl[@"enabled"] boolValue];
    NSString *urlString = customLoadUrl[@"url"];
    if ([urlString isKindOfClass:[NSString class]]) self.customLoadUrl = [NSURL URLWithString:urlString];
}

@end

@implementation Updater

+ (instancetype)sharedUpdater {
    static Updater *updater;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{ updater = [[Updater alloc] init]; });
    return updater;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        self.config = [[LoaderConfig alloc] init];
    }
    return self;
}

- (NSURL *)bundleFile {
    return [[getValencedDirectory() URLByAppendingPathComponent:CACHE_DIR]
            URLByAppendingPathComponent:MAIN_SCRIPT_FILE];
}

- (NSURL *)etagFile {
    return [[getValencedDirectory() URLByAppendingPathComponent:CACHE_DIR]
            URLByAppendingPathComponent:ETAG_PATH];
}

- (NSURL *)configFile {
    return [getValencedDirectory() URLByAppendingPathComponent:CONFIG_PATH];
}

- (void)initConfig {
    NSFileManager *fm = [NSFileManager defaultManager];
    [fm createDirectoryAtURL:[getValencedDirectory() URLByAppendingPathComponent:PRELOADS_DIR]
      withIntermediateDirectories:YES attributes:nil error:nil];
    [fm createDirectoryAtURL:[getValencedDirectory() URLByAppendingPathComponent:CACHE_DIR]
      withIntermediateDirectories:YES attributes:nil error:nil];

    NSURL *configURL = [self configFile];
    if ([fm fileExistsAtPath:configURL.path]) {
        NSData *data = [NSData dataWithContentsOfURL:configURL];
        NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
        if ([json isKindOfClass:[NSDictionary class]]) {
            LoaderConfig *cfg = [Updater loaderConfigFromJSON:json];
            if (cfg) self.config = cfg;
            return;
        }
    }
    self.config = [[LoaderConfig alloc] init];
}

+ (LoaderConfig *)loaderConfigFromJSON:(NSDictionary *)json {
    LoaderConfig *cfg = [[LoaderConfig alloc] init];
    NSDictionary *customLoadUrl = json[@"customLoadUrl"];
    if ([customLoadUrl isKindOfClass:[NSDictionary class]]) {
        cfg.customLoadUrlEnabled = [customLoadUrl[@"enabled"] boolValue];
        NSString *urlString = customLoadUrl[@"url"];
        if ([urlString isKindOfClass:[NSString class]]) {
            cfg.customLoadUrl = [NSURL URLWithString:urlString];
        }
    }
    return cfg;
}

- (NSURL *)resolveDownloadURL {
    if (self.config.customLoadUrlEnabled && self.config.customLoadUrl) {
        return self.config.customLoadUrl;
    }
    return nil;
}

- (void)resetLoaderConfig {
    NSURL *configURL = [self configFile];
    [[NSFileManager defaultManager] removeItemAtURL:configURL error:nil];
}

- (BOOL)downloadScriptUserInitiated:(BOOL)userInitiated
                         showDialog:(BOOL)showDialog {
    NSURL *url = [self resolveDownloadURL];
    if (!url) return NO;
    ValencedLog(@"[Updater] Fetching JS bundle from: %@", url.absoluteString);

    NSURL *bundleFile = [self bundleFile];
    NSURL *etagFile = [self etagFile];
    NSFileManager *fm = [NSFileManager defaultManager];

    BOOL hasCache = [fm fileExistsAtPath:bundleFile.path];
    __block NSString *etag = nil;
    if (hasCache) {
        dispatchSyncFs(^{
            etag = [NSString stringWithContentsOfURL:etagFile encoding:NSUTF8StringEncoding error:nil];
        });
    }
    NSTimeInterval timeout = userInitiated ? 0.0 : (hasCache ? UpdaterTimeoutCached : UpdaterTimeout);

    NSMutableURLRequest *req = [NSMutableURLRequest requestWithURL:url
                                                        cachePolicy:NSURLRequestReloadIgnoringLocalAndRemoteCacheData
                                                    timeoutInterval:timeout > 0 ? timeout : 15.0];
    if (etag) [req setValue:etag forHTTPHeaderField:@"If-None-Match"];

    __block NSData *responseData = nil;
    __block NSHTTPURLResponse *httpResponse = nil;
    __block NSError *responseError = nil;
    dispatch_group_t group = dispatch_group_create();
    dispatch_group_enter(group);
    NSURLSession *session = [NSURLSession sessionWithConfiguration:[NSURLSessionConfiguration defaultSessionConfiguration]];
    [[session dataTaskWithRequest:req completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        responseData = data;
        if ([response isKindOfClass:[NSHTTPURLResponse class]]) httpResponse = (NSHTTPURLResponse *)response;
        responseError = error;
        dispatch_group_leave(group);
    }] resume];

    dispatch_time_t deadline = timeout > 0
        ? dispatch_time(DISPATCH_TIME_NOW, (int64_t)(timeout * NSEC_PER_SEC))
        : DISPATCH_TIME_FOREVER;
    long waitResult = dispatch_group_wait(group, deadline);
    [session finishTasksAndInvalidate];

    if (waitResult != 0) {
        ValencedLog(@"[Updater] Download timed out after %.1fs", timeout);
        if (showDialog) [self showErrorDialog:@"Request timed out"];
        return NO;
    }

    if (responseError) {
        ValencedLog(@"[Updater] Failed to download script: %@", responseError.localizedDescription);
        if (showDialog) [self showErrorDialog:responseError.localizedDescription ?: @"Unknown network error"];
        return NO;
    }

    if (httpResponse.statusCode == 200 && responseData.length > 0) {
        dispatchSyncFs(^{
            [responseData writeToURL:bundleFile atomically:YES];
            NSString *newEtag = [httpResponse valueForHTTPHeaderField:@"Etag"]
                               ?: [httpResponse valueForHTTPHeaderField:@"ETag"];
            if (newEtag)
                [newEtag writeToURL:etagFile atomically:YES encoding:NSUTF8StringEncoding error:nil];
            else
                [fm removeItemAtURL:etagFile error:nil];
        });
        ValencedLog(@"[Updater] Bundle updated (%lu bytes)", (unsigned long)responseData.length);
        if (showDialog) {
            if (userInitiated) [self showSuccessDialog]; else [self showUpdateDialog];
        }
        return YES;
    }
    if (httpResponse.statusCode == 304) {
        ValencedLog(@"[Updater] Server responded with 304, no changes");
        return YES;
    }
    ValencedLog(@"[Updater] Unexpected HTTP %ld", (long)httpResponse.statusCode);
    return hasCache;
}

- (void)presentAlert:(UIAlertController *)alert {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *host = nil;
        for (UIWindow *w in [UIApplication sharedApplication].windows) {
            if (w.isKeyWindow) { host = w.rootViewController; break; }
        }
        while (host.presentedViewController) host = host.presentedViewController;
        [host presentViewController:alert animated:YES completion:nil];
    });
}

- (void)showUpdateDialog {
    UIAlertController *alert =
        [UIAlertController alertControllerWithTitle:@"Valenced Update Downloaded"
                                            message:@"A reload is required for changes to take effect."
                                     preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"Reload" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        reloadApp(nil);
    }]];
    [alert addAction:[UIAlertAction actionWithTitle:@"Later" style:UIAlertActionStyleCancel handler:nil]];
    [self presentAlert:alert];
}

- (void)showSuccessDialog {
    UIAlertController *alert =
        [UIAlertController alertControllerWithTitle:@"Valenced Update Successful"
                                            message:@"A reload is required for changes to take effect."
                                     preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"Reload" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        reloadApp(nil);
    }]];
    [alert addAction:[UIAlertAction actionWithTitle:@"Later" style:UIAlertActionStyleCancel handler:nil]];
    [self presentAlert:alert];
}

- (void)showErrorDialog:(NSString *)error {
    NSString *message = [NSString stringWithFormat:
                         @"Unable to download the latest version of Valenced.\n"
                         @"This is usually caused by bad network connection.\n\n"
                         @"Error: %@", error ?: @"Unknown error"];
    UIAlertController *alert =
        [UIAlertController alertControllerWithTitle:@"Valenced Update Failed"
                                            message:message
                                     preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"Dismiss" style:UIAlertActionStyleCancel handler:nil]];
    [alert addAction:[UIAlertAction actionWithTitle:@"Retry Update" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
            [[Updater sharedUpdater] downloadScriptUserInitiated:YES showDialog:YES];
        });
    }]];
    [alert addAction:[UIAlertAction actionWithTitle:@"Recovery" style:UIAlertActionStyleDestructive handler:^(UIAlertAction *action) {
        showRecoveryAlert(nil);
    }]];
    [self presentAlert:alert];
}

@end

#endif
