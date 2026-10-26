import type { Metro } from '@valenced/modules/types'
import type { ReactNative } from '@valenced/react/types'

export type Asset = PackagerAsset | CustomAsset
export type AssetId = number

export type PackagerAsset = ReactNative.AssetsRegistry.PackagerAsset
export interface CustomAsset
    extends Pick<PackagerAsset, 'name' | 'width' | 'height' | 'type' | 'id'> {
    uri: string
    moduleId?: undefined
}

export type RegisterableAsset = Omit<CustomAsset, 'id'>

/** Source used in place of an asset. Dimensions default to the original asset's. */
export interface AssetOverride {
    /** Any URI React Native can load, eg. `file://`, `https://` or `data:`. */
    uri: string
    width?: number
    height?: number
    scale?: number
}

declare module '@valenced/react/types' {
    export namespace ReactNative {
        export namespace AssetsRegistry {
            export interface PackagerAsset {
                id: AssetId
                moduleId: Metro.ModuleID
            }
        }
    }
}
