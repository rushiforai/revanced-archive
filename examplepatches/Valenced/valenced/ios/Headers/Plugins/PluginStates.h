#pragma once

#import <Foundation/Foundation.h>

@class PluginManifest;

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, PluginFlag) {
    /// The plugin is enabled.
    PluginFlagEnabled = 1 << 0,
    /// The plugin was explicitly enabled by the user.
    PluginFlagRequiredByUser = 1 << 1,
    /// The plugin requires a reload.
    PluginFlagPendingReload = 1 << 2,
    /// The plugin was enabled mid session (super not cute)
    PluginFlagStartedLate = 1 << 3,
};

NSDictionary *PluginStatePayloadForFlags(NSInteger flags);

@interface PluginsStates : NSObject

- (BOOL)isPluginEnabled:(NSString *)pluginId;
- (BOOL)hasPlugin:(NSString *)pluginId;
- (BOOL)isPluginEnabledInSaved:(NSString *)pluginId;
- (BOOL)hasPluginInSaved:(NSString *)pluginId;

- (void)setPluginFlags:(NSString *)pluginId flags:(NSInteger)pluginFlags;
- (void)removePlugin:(NSString *)pluginId;

@end

@interface PluginStatesStore : NSObject

+ (instancetype)sharedStore;

@property (nonatomic, strong, readonly, nullable) PluginsStates *states;
@property (nonatomic, assign, readonly) BOOL defaultsOnly;

- (PluginsStates *)ensureLoaded;
- (void)requestDefaultsOnlyBoot;
- (void)writeNow;
- (void)updatePluginFlags:(PluginManifest *)manifest flags:(NSInteger)flags;
- (void)batchSave:(void (^)(void))block;
- (nullable NSNumber *)loadPluginFlags:(NSString *)pluginId;

@end

#if defined(__cplusplus)
extern "C" void RegisterPluginStatesMethods(void);
#else
void RegisterPluginStatesMethods(void);
#endif

NS_ASSUME_NONNULL_END
