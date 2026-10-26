#pragma once

#import <Foundation/Foundation.h>

#ifdef __cplusplus
extern "C" {
#endif

NS_ASSUME_NONNULL_BEGIN

extern NSString * const FILES_DIR;
extern NSString * const CACHE_DIR;
extern NSString * const MAIN_SCRIPT_FILE;
extern NSString * const PRELOADS_DIR;

extern NSString * const ETAG_PATH;
extern NSString * const CONFIG_PATH;

extern NSString * const PLUGINS_DIR;
extern NSString * const PLUGIN_STORAGE_DIR;
extern NSString * const STATES_FILE;
extern NSString * const DEFAULTS_ONLY_MARKER_FILE;
extern NSString * const REPOS_DIR;
extern NSString * const REPOS_FILE;
extern NSString * const SOURCES_FILE;
extern NSString * const REPO_CACHE_DIR;
extern NSString * const REPO_INDEX_FILE;
extern NSString * const REPO_ETAG_FILE;
// Android: DEX jar		iOS: dylib
extern NSString * const PLUGIN_BINARY_FILE;

extern NSString * const INTERNAL_REPO_URL;
extern NSString * const REPO_CHANNEL_LATEST;

extern const int PLUGINS_STATES_CURRENT_VERSION;
extern const int REPOS_CONFIG_FORMAT;
extern const int SOURCES_CONFIG_FORMAT;
extern const int REPO_INDEX_FORMAT;

NS_ASSUME_NONNULL_END

#ifdef __cplusplus
}
#endif
