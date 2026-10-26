#pragma once

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import "PluginManifest.h"

NS_ASSUME_NONNULL_BEGIN

@class PluginScope;

@protocol HostScope <NSObject>
@optional
@property (nonatomic, copy, readonly) NSString *modulePath;
@property (nonatomic, copy, readonly) NSString *storageRootPath;
@end

@protocol Plugin <NSObject>
@required
- (void)start:(PluginScope *)scope;
@optional
- (void)stop:(PluginScope *)scope;
- (void)stop;
@property (nonatomic, strong, readonly) PluginManifest *manifest;
@end

@interface PluginScope : NSObject <HostScope>

@property (nonatomic, strong, readonly) PluginManifest *manifest;
@property (nonatomic, copy, readonly) NSString *storageDir;
@property (nonatomic, copy, readonly) NSString *storagePath;
@property (nonatomic, assign, readonly, getter=isEnabled) BOOL enabled;
@property (nonatomic, assign, readonly, getter=isStartedLate) BOOL startedLate;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype) new NS_UNAVAILABLE;

- (instancetype)initWithManifest:(PluginManifest *)manifest;
- (void)setStoragePath:(NSString *)storagePath;
- (void)setStorageDir:(NSString *)storageDir;
- (void)setEnabled:(BOOL)enabled;
- (void)setStartedLate:(BOOL)startedLate;

- (void)requireReload;
- (void)stop;
- (void)disable;

@end

NS_ASSUME_NONNULL_END
