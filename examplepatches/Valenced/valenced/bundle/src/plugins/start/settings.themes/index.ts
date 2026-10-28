import { ToastActionCreators } from '@valenced/discord/actions'
import {
    isPluginSystemErrorPayload,
    PluginErrorCodes,
    registerInternalPlugin,
} from '@valenced/plugins/_'
import { updatePlugins } from '@valenced/plugins/_/repositories'
import { sleep } from '@valenced/utils/promise'
import apiManifest from '../../preinit/api.themes/manifest.json'
import { messageOf } from '../settings.plugins/utils/strings'
import manifest from './manifest.json'

const MaxAttempts = 5
const BaseRetryDelay = 1000

// with exponential backoff
async function updatePluginsWithRetries(ids: string[]) {
    let result = await updatePlugins(ids)

    for (let attempt = 1; attempt < MaxAttempts; attempt++) {
        // Other codes are not transient
        const retryable = result.errors.filter(
            ({ error }) =>
                isPluginSystemErrorPayload(error) &&
                error.code === PluginErrorCodes.InstallFailed,
        )
        if (!retryable.length) break

        await sleep(BaseRetryDelay * 2 ** (attempt - 1))

        const retry = await updatePlugins(retryable.map(e => e.id))
        result = {
            installed: [...result.installed, ...retry.installed],
            pending: [...result.pending, ...retry.pending],
            errors: [
                ...result.errors.filter(e => !retryable.includes(e)),
                ...retry.errors,
            ],
        }
    }

    return result
}

registerInternalPlugin(manifest, {
    start({ plugin }) {
        ToastActionCreators.open('VALENCED_THEMES_STUB_UPDATE', {
            text: 'Downloading and installing updates for Theme plugins...',
        })

        updatePluginsWithRetries([manifest.id, apiManifest.id])
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
    },
})
