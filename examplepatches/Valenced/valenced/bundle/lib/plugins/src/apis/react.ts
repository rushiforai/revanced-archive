import * as PluginApiReact_ from '@valenced/react'
import * as PluginApiReactJsxRuntime from '@valenced/react/jsx-runtime'
import * as PluginApiReactNative from '@valenced/react/native'
import { spreadDescriptors } from '.'

export type PluginApiReact = typeof PluginApiReact_ & {
    jsxRuntime: typeof PluginApiReactJsxRuntime
    native: typeof PluginApiReactNative
}

export const react: PluginApiReact = spreadDescriptors(PluginApiReact_, {
    jsxRuntime: PluginApiReactJsxRuntime,
    native: PluginApiReactNative,
})
