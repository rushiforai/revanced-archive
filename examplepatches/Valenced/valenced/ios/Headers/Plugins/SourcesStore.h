#pragma once

#import <Foundation/Foundation.h>
#import "RepoStore.h"

NS_ASSUME_NONNULL_BEGIN

@interface PluginSource : NSObject
@property (nonatomic, copy, nullable) NSString *repo;
@property (nonatomic, copy) NSString *channel;
+ (instancetype)sourceWithRepo:(nullable NSString *)repo channel:(nullable NSString *)channel;
@end

@interface SourcesStore : NSObject

+ (instancetype)sharedStore;

- (NSDictionary<NSString *, PluginSource *> *)ensureLoaded;
- (NSDictionary<NSString *, PluginSource *> *)all;
- (nullable PluginSource *)get:(NSString *)pluginId;

- (void)set:(NSString *)pluginId source:(PluginSource *)source;

- (void)remove:(NSString *)pluginId;

- (void)forgetRepos:(NSArray<NSString *> *)repoUrls;

NSDictionary *SourcesStoreAll(void);
void SourcesStoreForgetRepos(NSArray<NSString *> *repoUrls);
void SourcesStoreRemove(NSString *pluginId);
void SourcesStoreSet(NSString *pluginId, NSString * _Nullable repo, NSString * _Nullable channel);

@end

NS_ASSUME_NONNULL_END
