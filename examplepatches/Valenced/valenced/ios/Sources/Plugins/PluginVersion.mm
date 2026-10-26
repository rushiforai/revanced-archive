#import "Plugins/PluginVersion.h"

#import <limits.h>

#import "ValencedCoreBridge.h"

@implementation PluginVersion

+ (instancetype)versionWithNumbers:(NSArray<NSNumber *> *)numbers label:(nullable NSString *)label {
    PluginVersion *v = [[PluginVersion alloc] init];
    v->_numbers = [numbers copy];
    v->_label = [label copy];
    return v;
}

+ (nullable PluginVersion *)versionFromString:(NSString *)value error:(NSString * _Nullable * _Nullable)error {
    char *parseError = NULL;
    char *canonical = ValencedCore_VersionParse(value.UTF8String, &parseError);
    if (!canonical) {
        if (error) *error = ValencedCore_ConsumeError(parseError);
        return nil;
    }

    NSString *text = ValencedCore_ConsumeString(canonical);
    NSString *numbersPart = text;
    NSString *label = nil;
    NSRange dash = [text rangeOfString:@"-"];
    if (dash.location != NSNotFound) {
        numbersPart = [text substringToIndex:dash.location];
        label = [text substringFromIndex:dash.location + 1];
    }

    NSArray<NSString *> *segments = [numbersPart componentsSeparatedByString:@"."];
    NSMutableArray<NSNumber *> *numbers = [NSMutableArray arrayWithCapacity:segments.count];
    for (NSString *segment in segments) [numbers addObject:@(segment.integerValue)];

    return [PluginVersion versionWithNumbers:numbers label:label];
}

- (NSComparisonResult)compare:(PluginVersion *)other {
    int result = ValencedCore_VersionCompare(self.stringValue.UTF8String, other.stringValue.UTF8String);
    if (result == VALENCED_CORE_INVALID || result == 0) return NSOrderedSame;
    return result < 0 ? NSOrderedAscending : NSOrderedDescending;
}

- (NSString *)stringValue {
    NSMutableArray<NSString *> *parts = [NSMutableArray arrayWithCapacity:self.numbers.count];
    for (NSNumber *n in self.numbers) [parts addObject:n.stringValue];
    NSString *base = [parts componentsJoinedByString:@"."];
    return self.label ? [base stringByAppendingFormat:@"-%@", self.label] : base;
}

- (PluginVersion *)bareVersion {
    if (!self.label) return self;
    return [PluginVersion versionWithNumbers:self.numbers label:nil];
}

- (id)copyWithZone:(NSZone *)zone {
    return [PluginVersion versionWithNumbers:self.numbers label:self.label];
}

@end

@implementation PluginVersionRangeBound

+ (instancetype)boundWithOp:(PluginVersionRangeOp)op version:(PluginVersion *)version {
    PluginVersionRangeBound *b = [[PluginVersionRangeBound alloc] init];
    b.op = op;
    b.version = version;
    return b;
}

- (id)copyWithZone:(NSZone *)zone {
    return [PluginVersionRangeBound boundWithOp:self.op version:self.version];
}

@end

@implementation PluginVersionRange {
    NSString *_canonical;
    NSArray<PluginVersionRangeBound *> *_cachedBounds;
}

+ (instancetype)anyVersionRange {
    return [self versionRangeWithCanonical:@"*"];
}

+ (instancetype)versionRangeWithCanonical:(NSString *)canonical {
    PluginVersionRange *r = [[PluginVersionRange alloc] init];
    r->_canonical = [canonical copy];
    return r;
}

+ (nullable PluginVersionRange *)versionRangeFromString:(NSString *)value error:(NSString * _Nullable * _Nullable)error {
    char *parseError = NULL;
    char *canonical = ValencedCore_VersionRangeParse(value.UTF8String, &parseError);
    if (!canonical) {
        if (error) *error = ValencedCore_ConsumeError(parseError);
        return nil;
    }
    return [self versionRangeWithCanonical:ValencedCore_ConsumeString(canonical)];
}

- (NSArray<PluginVersionRangeBound *> *)bounds {
    if (_cachedBounds) return _cachedBounds;

    NSMutableArray<PluginVersionRangeBound *> *bounds = [NSMutableArray array];
    if (![_canonical isEqualToString:@"*"]) {
        for (NSString *token in [_canonical componentsSeparatedByString:@" "]) {
            if (token.length == 0) continue;
            PluginVersionRangeOp op;
            NSString *opString;
            if ([token hasPrefix:@">="]) { op = PluginVersionRangeOpGTE; opString = @">="; }
            else if ([token hasPrefix:@"<="]) { op = PluginVersionRangeOpLTE; opString = @"<="; }
            else if ([token hasPrefix:@"<"]) { op = PluginVersionRangeOpLT; opString = @"<"; }
            else if ([token hasPrefix:@">"]) { op = PluginVersionRangeOpGT; opString = @">"; }
            else if ([token hasPrefix:@"="]) { op = PluginVersionRangeOpEQ; opString = @"="; }
            else continue;

            PluginVersion *version = [PluginVersion versionFromString:[token substringFromIndex:opString.length] error:NULL];
            if (version) [bounds addObject:[PluginVersionRangeBound boundWithOp:op version:version]];
        }
    }
    _cachedBounds = [bounds copy];
    return _cachedBounds;
}

- (BOOL)satisfies:(PluginVersion *)version {
    return ValencedCore_VersionRangeSatisfies(self.stringValue.UTF8String, version.stringValue.UTF8String) == 1;
}

- (NSString *)stringValue {
    return _canonical ?: @"*";
}

- (id)copyWithZone:(NSZone *)zone {
    return [PluginVersionRange versionRangeWithCanonical:self.stringValue];
}

@end
