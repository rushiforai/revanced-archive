import defer * as Browserify from '@valenced/externals/browserify'
import defer * as Gorhom from '@valenced/externals/gorhom'
import defer * as ReactNativeClipboard from '@valenced/externals/react-native-clipboard'
import defer * as ReactNativeCommunity from '@valenced/externals/react-native-community'
import defer * as ReactNativeSafeAreaContext from '@valenced/externals/react-native-safe-area-context'
import defer * as ReactNavigation from '@valenced/externals/react-navigation'
import defer * as Shopify from '@valenced/externals/shopify'
import defer * as Zustand from '@valenced/externals/zustand'
import { defineLazyProperties } from '@valenced/utils/object'

export interface PluginApiExternals {
    Browserify: typeof import('@valenced/externals/browserify')
    Gorhom: typeof import('@valenced/externals/gorhom')
    ReactNativeClipboard: typeof import('@valenced/externals/react-native-clipboard')
    ReactNativeCommunity: typeof import('@valenced/externals/react-native-community')
    ReactNativeSafeAreaContext: typeof import('@valenced/externals/react-native-safe-area-context')
    ReactNavigation: typeof import('@valenced/externals/react-navigation')
    Shopify: typeof import('@valenced/externals/shopify')
    Zustand: typeof import('@valenced/externals/zustand')
}

export const externals: PluginApiExternals = defineLazyProperties(
    {} as PluginApiExternals,
    {
        Browserify: () => {
            return Browserify
        },
        Gorhom: () => {
            return Gorhom
        },
        ReactNativeClipboard: () => {
            return ReactNativeClipboard
        },
        ReactNativeCommunity: () => {
            return ReactNativeCommunity
        },
        ReactNativeSafeAreaContext: () => {
            return ReactNativeSafeAreaContext
        },
        ReactNavigation: () => {
            return ReactNavigation
        },
        Shopify: () => {
            return Shopify
        },
        Zustand: () => {
            return Zustand
        },
    },
)
