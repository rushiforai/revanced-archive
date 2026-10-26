import * as PluginsApiConstants from '@valenced/plugins/constants'
import * as PluginsApiUtils from '@valenced/plugins/utils'

export interface PluginApiPlugins {
    utils: typeof import('@valenced/plugins/utils')
    constants: typeof import('@valenced/plugins/constants')
}

export const plugins: PluginApiPlugins = {
    constants: PluginsApiConstants,
    utils: PluginsApiUtils,
}
