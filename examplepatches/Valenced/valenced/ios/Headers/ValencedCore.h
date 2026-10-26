#pragma once

#include <limits.h>

#ifdef __cplusplus
extern "C" {
#endif

#define VALENCED_CORE_INVALID INT_MIN

char *ValencedCore_VersionParse(const char *value, char **errorOut);
int ValencedCore_VersionCompare(const char *a, const char *b);
char *ValencedCore_VersionRangeParse(const char *value, char **errorOut);
int ValencedCore_VersionRangeSatisfies(const char *range, const char *version);
char *ValencedCore_ManifestParse(const char *json, char **errorOut);

char *ValencedCore_RepoIndexParse(const char *json, const char *repoUrl, char **errorOut);
char *ValencedCore_RepoStoreList(char **errorOut);
int ValencedCore_RepoStoreSet(const char *entriesJson, char **errorOut);
char *ValencedCore_RepoStoreCacheDir(const char *url, char **errorOut);
char *ValencedCore_RepoStoreCachedIndexFile(const char *url, char **errorOut);
char *ValencedCore_RepoStoreCachedETagFile(const char *url, char **errorOut);

char *ValencedCore_SourcesStoreAll(char **errorOut);
int ValencedCore_SourcesStoreSet(const char *pluginId, const char *sourceJson, char **errorOut);
int ValencedCore_SourcesStoreRemove(const char *pluginId, char **errorOut);
int ValencedCore_SourcesStoreForgetRepos(const char *repoUrlsJson, char **errorOut);
char *ValencedCore_SourcesStoreSetHeld(const char *pluginId, int held, char **errorOut);

char *ValencedCore_ResolveInstall(const char *pluginId, const char *optionsJson,
                                  const char *installedJson, const char *installedDepsJson,
                                  char **errorOut);

char *ValencedCore_FindUpdates(const char *installedJson, const char *pendingJson,
                               const char *installedDepsJson, char **errorOut);

int ValencedCore_PluginIdIsValid(const char *id);

char *ValencedCore_Sha256Hex(const char *data, unsigned long long length, char **errorOut);

char *ValencedCore_PluginsLoadOrder(const char *manifestsJson, char **errorOut);

char *ValencedCore_RequiredDependents(const char *manifestsJson, const char *pluginId, char **errorOut);

char *ValencedCore_StagePluginZip(const char *zipPath, const char *tmpName, char **codeOut, char **errorOut);

char *ValencedCore_OrderPluginsByDependencies(const char *requestJson, char **errorOut);

char *ValencedCore_PluginStatesGetSlots(char **errorOut);
char *ValencedCore_PluginStatesRead(char **errorOut);
int ValencedCore_PluginStatesSetActiveSlot(const char *slot, int oneShot, char **errorOut);
char *ValencedCore_PluginStatesUpdate(const char *slot, const char *pluginId,
                                      const char *stateJson, char **errorOut);
int ValencedCore_PluginStatesActiveFlags(const char *pluginId, char **errorOut);
int ValencedCore_PluginStatesBootFlags(const char *pluginId, char **errorOut);
int ValencedCore_PluginStatesSetActiveFlags(const char *pluginId, int mask, char **errorOut);
int ValencedCore_PluginStatesRemoveAll(const char *pluginId, char **errorOut);
int ValencedCore_PluginStatesDefaultsBoot(void);
char *ValencedCore_PluginStatesBootSlot(char **errorOut);

void ValencedCore_FreeString(char *value);

#ifdef __cplusplus
}
#endif
