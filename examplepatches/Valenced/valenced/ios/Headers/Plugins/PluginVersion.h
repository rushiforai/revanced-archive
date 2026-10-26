#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

static NSString * const kApiDependencyId     = @"valenced.api";
static NSString * const kDiscordDependencyId = @"discord";

@interface PluginVersion : NSObject <NSCopying>

@property (nonatomic, strong, readonly) NSArray<NSNumber *> *numbers;
@property (nonatomic, copy, readonly, nullable) NSString *label;

+ (instancetype)versionWithNumbers:(NSArray<NSNumber *> *)numbers label:(nullable NSString *)label;
+ (nullable PluginVersion *)versionFromString:(NSString *)value error:(NSString * _Nullable * _Nullable)error;

- (NSComparisonResult)compare:(PluginVersion *)other;
- (NSString *)stringValue;
- (PluginVersion *)bareVersion;

@end

typedef NS_ENUM(NSInteger, PluginVersionRangeOp) {
    PluginVersionRangeOpLT,
    PluginVersionRangeOpLTE,
    PluginVersionRangeOpEQ,
    PluginVersionRangeOpGTE,
    PluginVersionRangeOpGT
};

@interface PluginVersionRangeBound : NSObject
@property (nonatomic, assign) PluginVersionRangeOp op;
@property (nonatomic, strong) PluginVersion *version;
+ (instancetype)boundWithOp:(PluginVersionRangeOp)op version:(PluginVersion *)version;
@end

@interface PluginVersionRange : NSObject <NSCopying>
@property (nonatomic, strong, readonly) NSArray<PluginVersionRangeBound *> *bounds;
+ (instancetype)anyVersionRange;
+ (nullable PluginVersionRange *)versionRangeFromString:(NSString *)value error:(NSString * _Nullable * _Nullable)error;
- (BOOL)satisfies:(PluginVersion *)version;
- (NSString *)stringValue;
@end

NS_ASSUME_NONNULL_END
