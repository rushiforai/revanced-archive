import * as jsonStorage from '@valenced/json-storage'
import { exists, rm } from '@valenced/modules/native/fs'
import { pluginStorageDirFor } from '@valenced/plugins/constants'
import { defineLazyProperty } from '@valenced/utils/object'
import type { JsonStorage, JsonStorageOptions } from '@valenced/json-storage'
import type { AnyPlugin } from '@valenced/plugins/_'
import type { InitPluginApi, PreInitPluginApi } from '@valenced/plugins/types'
import type { AnyObject } from '@valenced/utils/types'

type PluginJsonStorageOptions = JsonStorageOptions & {
    file?: string
}

const storageOptions = new WeakMap<AnyPlugin, PluginJsonStorageOptions>()

export function preInit({ unscoped, decorate }: PreInitPluginApi) {
    unscoped.jsonStorage = jsonStorage

    decorate((plugin, { jsonStorage }) => {
        if (jsonStorage) storageOptions.set(plugin, jsonStorage)
    })
}

export function init({ decorate }: InitPluginApi) {
    const makePluginStorage = (
        plugin: AnyPlugin,
        opts?: PluginJsonStorageOptions,
    ) =>
        jsonStorage.getJsonStorage(
            jsonStorage.pluginStoragePathFor(plugin.manifest.id, opts?.file),
            opts,
        )

    decorate(plugin => {
        const opts = storageOptions.get(plugin)

        if (opts?.load) plugin.api.jsonStorage = makePluginStorage(plugin, opts)
        else
            defineLazyProperty(plugin.api, 'jsonStorage', () =>
                makePluginStorage(plugin, opts),
            )
    })
}

export async function deleteJsonStorageForPlugin(plugin: AnyPlugin) {
    const path = pluginStorageDirFor(plugin.manifest.id)
    if (await exists(path)) await rm(path)

    const api = plugin.api as
        | InitPluginApi<{ jsonStorage: AnyObject }>
        | undefined

    // Plugin may have never initialized, so there is no API and nothing to refresh
    if (!api) return

    // Only update already initialized storages
    const storage = Object.getOwnPropertyDescriptor(api, 'jsonStorage')
        ?.value as JsonStorage<AnyObject> | undefined

    await storage?.get()
}
