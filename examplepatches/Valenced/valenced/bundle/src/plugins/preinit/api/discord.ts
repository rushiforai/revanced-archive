import { defineLazyProperty } from '@valenced/utils/object'
import type { InitPluginApi } from '@valenced/plugins/types'

export function init({
    decorate,
    unscoped: {
        discord: {
            common: {
                logger: { Logger },
            },
        },
    },
}: InitPluginApi) {
    decorate(plugin => {
        defineLazyProperty(
            plugin.api,
            'logger',
            () => new Logger(`Valenced > Plugins (${plugin.manifest.id})`),
        )
    })
}
