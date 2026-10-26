import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { Linking } from 'react-native'
import { Setting } from '../constants'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedDiscordSetting: SettingsItem = {
    parent: Setting.Valenced,
    IconComponent: () => <TableRowAssetIcon name="Discord" />,
    useTitle: () => 'Discord Server',
    useDescription: () => __BUILD_DISCORD_SERVER_URL__,
    onPress: () => {
        Linking.openURL(__BUILD_DISCORD_SERVER_URL__)
    },
    type: 'pressable',
}

export default ValencedDiscordSetting
