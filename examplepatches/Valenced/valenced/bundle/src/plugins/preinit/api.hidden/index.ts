/// <reference path="../../../../types/globals.hidden.ts" />

import { hiddenApi } from '@valenced/hidden'
import { registerInternalPlugin } from '@valenced/plugins/_'
import manifest from './manifest.json'

const pluginHiddenApi = registerInternalPlugin(manifest, {
    preInit({ cleanup, unscoped }) {
        globalThis.valenced = unscoped
        unscoped.hidden = hiddenApi

        cleanup(() => {
            // biome-ignore lint/performance/noDelete: We want to remove the API completely
            delete globalThis.valenced
            // biome-ignore lint/performance/noDelete: We want to remove the API completely
            delete unscoped.hidden
        })
    },
})

export default pluginHiddenApi
