import type {
    UnscopedInitPluginApi,
    UnscopedPluginApi,
    UnscopedPreInitPluginApi,
} from '@valenced/plugins/types'

declare global {
    export var valenced:
        | UnscopedPreInitPluginApi
        | UnscopedInitPluginApi
        | UnscopedPluginApi
        | undefined
}
