import type { RouteNames, Setting } from './constants'

declare module '@valenced/externals/react-navigation' {
    interface ReactNavigationParamList extends PluginsSettingsParamList {}
}

type PluginsSettingsParamList = {
    [K in (typeof RouteNames)[keyof typeof RouteNames]]: object
} & {
    [K in (typeof RouteNames)[(typeof Setting)['ValencedPlugins']]]: {
        sort?: string
        filter?: string[]
        matchAll?: boolean
        reverse?: boolean
    }
}
