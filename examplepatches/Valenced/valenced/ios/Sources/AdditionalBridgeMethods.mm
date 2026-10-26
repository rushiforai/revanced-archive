#import "Bridge.h"
#import "Constants.h"
#import "Utils.h"
#import "Logger.h"

extern "C" void RegisterAdditionalBridgeMethods(void) {
    bridgeHandlers()[@"valenced.app.reload"] = ^NSDictionary *(NSArray *args) {
        dispatch_async(dispatch_get_main_queue(), ^{
            UIViewController *rootVC = [UIApplication sharedApplication].windows.firstObject.rootViewController;
            reloadApp(rootVC);
        });
        return @{@"result": [NSNull null]};
    };

    bridgeHandlers()[@"valenced.fs.getConstants"] = ^NSDictionary *(NSArray *args) {
        NSURL *valencedDir = getValencedDirectory();
        NSURL *filesDir = valencedDir;
        NSURL *cacheDir = [valencedDir URLByAppendingPathComponent:CACHE_DIR];
        return @{@"result": @{
            @"data": valencedDir.path,
            @"files": filesDir.path,
            @"cache": cacheDir.path,
        }};
    };

    bridgeHandlers()[@"valenced.fs.exists"] = ^NSDictionary *(NSArray *args) {
        NSString *path = argString(args, 0, nil);
        if (!path) return @{@"error": @"Expected a string path argument"};
        return @{@"result": @([[NSFileManager defaultManager] fileExistsAtPath:path])};
    };

    bridgeHandlers()[@"valenced.fs.read"] = ^NSDictionary *(NSArray *args) {
        NSString *path = argString(args, 0, nil);
        if (!path) return @{@"error": @"Expected a string path argument"};
        BOOL isDir = NO;
        if (![[NSFileManager defaultManager] fileExistsAtPath:path isDirectory:&isDir]) {
            return @{@"error": [NSString stringWithFormat:@"Path does not exist: %@", path]};
        }
        if (isDir) {
            return @{@"error": [NSString stringWithFormat:@"Path is not a file: %@", path]};
        }
        NSError *error = nil;
        NSString *content = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:&error];
        if (!content && error) return @{@"error": error.localizedDescription};
        return @{@"result": content ?: @""};
    };

    bridgeHandlers()[@"valenced.fs.write"] = ^NSDictionary *(NSArray *args) {
        NSString *path = argString(args, 0, nil);
        NSString *contents = argString(args, 1, nil);
        if (!path) return @{@"error": @"Expected a string path argument"};
        if (!contents) return @{@"error": @"Expected a string content argument"};
        BOOL isDir = NO;
        if ([[NSFileManager defaultManager] fileExistsAtPath:path isDirectory:&isDir] && isDir) {
            return @{@"error": [NSString stringWithFormat:@"Path is a directory: %@", path]};
        }
        NSString *parent = [path stringByDeletingLastPathComponent];
        if (parent.length) {
            [[NSFileManager defaultManager] createDirectoryAtPath:parent
                                      withIntermediateDirectories:YES attributes:nil error:nil];
        }
        NSError *error = nil;
        if (![contents writeToFile:path atomically:YES encoding:NSUTF8StringEncoding error:&error]) {
            return @{@"error": error.localizedDescription ?: @"Failed to write file"};
        }
        return @{@"result": [NSNull null]};
    };

    bridgeHandlers()[@"valenced.fs.delete"] = ^NSDictionary *(NSArray *args) {
        NSString *path = argString(args, 0, nil);
        if (!path) return @{@"error": @"Expected a string path argument"};
        NSFileManager *fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        BOOL deleted = YES;
        if ([fm fileExistsAtPath:path isDirectory:&isDir]) {
            if (isDir) deleted = [fm removeItemAtPath:path error:nil];
            else deleted = [fm removeItemAtPath:path error:nil];
        }
        return @{@"result": @(deleted)};
    };
}
