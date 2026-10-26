import type { DiscordModules } from '.'

declare module '@valenced/plugins/types' {
    export interface InitPluginApi<O extends PluginApiExtensionsOptions> {
        logger: DiscordModules.Logger
    }
}
