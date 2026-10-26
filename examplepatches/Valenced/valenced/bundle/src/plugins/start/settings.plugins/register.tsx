import {
    addSettingsItemToSection,
    registerSettingsItems,
} from '@valenced/discord/modules/settings'
import { Setting } from './constants'
import ValencedPluginsAdvancedSetting from './definitions/ValencedPluginsAdvancedSetting'
import ValencedPluginsBrowseSetting from './definitions/ValencedPluginsBrowseSetting'
import ValencedPluginsSetting from './definitions/ValencedPluginsSetting'
import ValencedPluginsUpdatesSetting from './definitions/ValencedPluginsUpdatesSetting'

registerSettingsItems({
    [Setting.ValencedPlugins]: ValencedPluginsSetting,
    [Setting.ValencedPluginsAdvanced]: ValencedPluginsAdvancedSetting,
    [Setting.ValencedPluginsBrowse]: ValencedPluginsBrowseSetting,
    [Setting.ValencedPluginsUpdates]: ValencedPluginsUpdatesSetting,
})

// The settings plugin registers this section, and it always starts before us (we depend on it).
addSettingsItemToSection('VALENCED', Setting.ValencedPlugins, 1)
