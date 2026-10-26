#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface LoaderConfig : NSObject
@property (nonatomic, assign) BOOL customLoadUrlEnabled;
@property (nonatomic, strong, nullable) NSURL *customLoadUrl;
- (void)load;
@end

@interface Updater : NSObject
+ (instancetype)sharedUpdater;

@property (nonatomic, strong) LoaderConfig *config;

- (void)initConfig;
/// The custom URL from loader.json, or nil when unset. Debug builds only.
- (nullable NSURL *)resolveDownloadURL;
- (void)resetLoaderConfig;
- (NSURL *)bundleFile;
- (NSURL *)etagFile;

- (BOOL)downloadScriptUserInitiated:(BOOL)userInitiated
                         showDialog:(BOOL)showDialog;

@end

NS_ASSUME_NONNULL_END
