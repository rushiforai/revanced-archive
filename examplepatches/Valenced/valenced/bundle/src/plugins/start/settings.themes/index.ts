import { ToastActionCreators } from '@valenced/discord/actions'
import { registerInternalPlugin } from '@valenced/plugins/_'
import { updatePlugins } from '@valenced/plugins/_/repositories'
import apiManifest from '../../preinit/api.themes/manifest.json'
import { messageOf } from '../settings.plugins/utils/strings'
import manifest from './manifest.json'

registerInternalPlugin(manifest, {
    start({ plugin }) {
        ToastActionCreators.open('VALENCED_THEMES_STUB_UPDATE', {
            text: 'Downloading and installing updates for Theme plugins...',
        })

        updatePlugins([manifest.id, apiManifest.id])
            .then(result => {
                if (!result.errors.length) return

                ToastActionCreators.open('VALENCED_THEMES_STUB_UPDATE_ERRORS', {
                    text: `Errors occurred while updating plugins: ${result.errors
                        .map(e => `${e.id}: ${messageOf(e.error)}`)
                        .join('\n')}`,
                    variant: 'critical',
                })

                for (const error of result.errors) {
                    plugin.reportError(
                        new Error(`Failed to update plugin ${error.id}`, {
                            cause: error.error,
                        }),
                    )
                }
            })
            .finally(() => {
                plugin.disable()
            })
    },
})
