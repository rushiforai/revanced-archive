#pragma once

#import <Foundation/Foundation.h>
#import "RepoIndex.h"

NS_ASSUME_NONNULL_BEGIN

@interface UserRepo : NSObject
@property (nonatomic, copy) NSString *url;
@property (nonatomic, assign) BOOL enabled;
+ (instancetype)repoWithURL:(NSString *)url enabled:(BOOL)enabled;
@end

@interface RepoStore : NSObject

+ (instancetype)sharedStore;

- (NSArray<UserRepo *> *)ensureLoaded;
- (NSArray<UserRepo *> *)list;

- (nullable NSString *)setEntries:(NSArray<UserRepo *> *)entries;

- (NSURL *)cacheDirForURL:(NSString *)url;
- (NSURL *)cachedIndexFileForURL:(NSString *)url;
- (NSURL *)cachedETagFileForURL:(NSString *)url;

- (nullable RepoIndex *)cachedIndexForURL:(NSString *)url;

@end

NS_ASSUME_NONNULL_END
