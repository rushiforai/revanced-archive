import * as PluginApiModulesFinders_ from '@valenced/modules/finders'
import * as PluginApiModulesFindersFilters from '@valenced/modules/finders/filters'
import * as PluginApiModulesMetroSubscriptions from '@valenced/modules/metro/subscriptions'
import * as PluginApiModulesMetroUtils from '@valenced/modules/metro/utils'
import * as PluginApiModulesNative_ from '@valenced/modules/native'
import * as PluginApiModulesNativeApp from '@valenced/modules/native/app'
import * as PluginApiModulesNativeFileSystem from '@valenced/modules/native/fs'
import { spreadDescriptors } from '.'

export interface PluginApiModules {
    finders: PluginApiModulesFinders
    metro: PluginApiModulesMetro
    native: PluginApiModulesNative
}

export type PluginApiModulesNative = typeof PluginApiModulesNative_ & {
    app: typeof PluginApiModulesNativeApp
    fs: typeof PluginApiModulesNativeFileSystem
}

export type PluginApiModulesMetro =
    // biome-ignore format: Don't
    typeof PluginApiModulesMetroUtils &
    typeof PluginApiModulesMetroSubscriptions

export type PluginApiModulesFinders = typeof PluginApiModulesFinders_ & {
    filters: typeof PluginApiModulesFindersFilters
}

export const modules: PluginApiModules = {
    finders: spreadDescriptors(PluginApiModulesFinders_, {
        filters: PluginApiModulesFindersFilters,
    }),
    metro: spreadDescriptors(
        PluginApiModulesMetroUtils,
        spreadDescriptors(PluginApiModulesMetroSubscriptions, {}),
    ),
    native: spreadDescriptors(PluginApiModulesNative_, {
        app: PluginApiModulesNativeApp,
        fs: PluginApiModulesNativeFileSystem,
    }),
}
