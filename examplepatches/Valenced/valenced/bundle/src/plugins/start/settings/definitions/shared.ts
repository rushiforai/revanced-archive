import { ToastActionCreators } from '@valenced/discord/actions'
import { Clipboard } from '@valenced/externals/react-native-clipboard'
import { ReactNative } from '@valenced/react'
import { lookupGeneratedIconComponent } from '@valenced/utils/discord'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const CopyIcon = lookupGeneratedIconComponent('CopyIcon')

export const CopyableSetting = (
    item: Omit<SettingsItem, 'type' | 'onClick'>,
    description: () => string,
): SettingsItem => ({
    ...item,
    useDescription: item.useDescription ?? (() => description()),
    type: 'pressable',
    onPress() {
        Clipboard.setString(description())
        ToastActionCreators.open('VALENCED_SETTING_COPIED', {
            text: 'Copied to clipboard',
            icon: CopyIcon,
        })
    },
})

export const getRNVersion = () => {
    const { major, minor, patch, prerelease } =
        ReactNative.Platform.constants.reactNativeVersion
    return `${major}.${minor}.${patch}${prerelease ? `-${prerelease}` : ''}`
}
