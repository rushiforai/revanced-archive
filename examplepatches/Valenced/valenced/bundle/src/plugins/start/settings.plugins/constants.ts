export const Setting = {
    ValencedPlugins: 'ValencedPlugins',
    ValencedPluginsAdvanced: 'ValencedPluginsAdvanced',
    ValencedPluginsBrowse: 'ValencedPluginsBrowse',
    ValencedPluginsUpdates: 'ValencedPluginsUpdates',
} as const

export const RouteNames = {
    [Setting.ValencedPlugins]: 'Valenced Plugins',
    [Setting.ValencedPluginsAdvanced]: 'Valenced Plugins Advanced',
    [Setting.ValencedPluginsBrowse]: 'Browse Plugins',
    [Setting.ValencedPluginsUpdates]: 'Plugin Updates',
} as const
