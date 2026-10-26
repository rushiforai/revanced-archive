#import "Plugins/RepoStore.h"
#import "Plugins/RepoIndex.h"
#import "Constants.h"
#import "Bridge.h"
#import "Utils.h"
#import "Logger.h"

#import <UIKit/UIKit.h>

static const NSTimeInterval kRefreshTimeout = 10.0;

typedef NS_ENUM(NSInteger, ETagFetchKind) {
    ETagFetchKindFetched,
    ETagFetchKindNotModified,
};

static ETagFetchKind GetWithETag(NSString *url,
                                 NSString *etag,
                                 NSTimeInterval timeout,
                                 NSData * _Nullable * _Nullable outBytes,
                                 NSString * _Nullable * _Nullable outEtag,
                                 NSString * _Nullable * _Nullable outError) {
    NSMutableURLRequest *request =
        [NSMutableURLRequest requestWithURL:[NSURL URLWithString:url]
                                cachePolicy:NSURLRequestReloadIgnoringLocalAndRemoteCacheData
                            timeoutInterval:timeout];
    if (etag) [request setValue:etag forHTTPHeaderField:@"If-None-Match"];

    __block NSData *responseData = nil;
    __block NSHTTPURLResponse *httpResponse = nil;
    __block NSError *responseError = nil;
    dispatch_group_t group = dispatch_group_create();
    dispatch_group_enter(group);
    NSURLSession *session =
        [NSURLSession sessionWithConfiguration:[NSURLSessionConfiguration defaultSessionConfiguration]];
    [[session dataTaskWithRequest:request
                completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        responseData = data;
        if ([response isKindOfClass:[NSHTTPURLResponse class]]) httpResponse = (NSHTTPURLResponse *)response;
        responseError = error;
        dispatch_group_leave(group);
    }] resume];
    dispatch_group_wait(group, DISPATCH_TIME_FOREVER);
    [session finishTasksAndInvalidate];

    if (responseError) {
        if (outError) *outError = responseError.localizedDescription ?: @"Network error";
        return ETagFetchKindFetched;
    }
    if (httpResponse.statusCode == 304) return ETagFetchKindNotModified;
    if (httpResponse.statusCode == 200 && responseData) {
        if (outBytes) *outBytes = responseData;
        if (outEtag) *outEtag = [httpResponse valueForHTTPHeaderField:@"Etag"]
                             ?: [httpResponse valueForHTTPHeaderField:@"ETag"];
        return ETagFetchKindFetched;
    }
    if (outError) {
        *outError = [NSString stringWithFormat:@"Received status: %ld", (long)httpResponse.statusCode];
    }
    return ETagFetchKindFetched;
}

RepoIndex * _Nullable ParseRepoIndex(NSString *text, NSString *repoUrl);

NSString * _Nullable RefreshRepo(NSString *url, RepoIndex * _Nullable * _Nullable indexOut) {
    NSArray<UserRepo *> *repos = [[RepoStore sharedStore] list];
    BOOL known = NO;
    for (UserRepo *repo in repos) {
        if ([repo.url isEqualToString:url]) { known = YES; break; }
    }
    if (!known) return [NSString stringWithFormat:@"Unknown repository: '%@'", url];

    NSString *indexUrl = url;
    while ([indexUrl hasSuffix:@"/"]) indexUrl = [indexUrl substringToIndex:indexUrl.length - 1];
    NSURL *indexFile = [[RepoStore sharedStore] cachedIndexFileForURL:url];
    NSURL *etagFile = [[RepoStore sharedStore] cachedETagFileForURL:url];

    NSFileManager *fm = [NSFileManager defaultManager];
    NSString *etag = nil;
    if ([fm fileExistsAtPath:etagFile.path] && [fm fileExistsAtPath:indexFile.path]) {
        etag = [NSString stringWithContentsOfURL:etagFile encoding:NSUTF8StringEncoding error:nil];
    }

    NSData *bytes = nil;
    NSString *newEtag = nil;
    NSString *fetchError = nil;
    ETagFetchKind kind = GetWithETag(indexUrl, etag, kRefreshTimeout, &bytes, &newEtag, &fetchError);

    if (kind == ETagFetchKindNotModified) {
        RepoIndex *cached = [[RepoStore sharedStore] cachedIndexForURL:url];
        if (!cached) return [NSString stringWithFormat:@"Repository '%@' responded 304 but the cache is unreadable", url];
        if (indexOut) *indexOut = cached;
        return nil;
    }

    if (!bytes) return fetchError ?: @"Failed to refresh repository";

    NSString *text = [[NSString alloc] initWithData:bytes encoding:NSUTF8StringEncoding];
    RepoIndex *index = text ? ParseRepoIndex(text, url) : nil;
    if (!index) return [NSString stringWithFormat:@"Repository '%@' has an invalid index", url];

    [[NSFileManager defaultManager] createDirectoryAtURL:[[RepoStore sharedStore] cacheDirForURL:url]
                              withIntermediateDirectories:YES attributes:nil error:nil];
    [bytes writeToURL:indexFile atomically:YES];
    if (newEtag.length > 0) {
        [newEtag writeToURL:etagFile atomically:YES encoding:NSUTF8StringEncoding error:nil];
    } else {
        [fm removeItemAtURL:etagFile error:nil];
    }

    if (indexOut) *indexOut = index;
    return nil;
}
