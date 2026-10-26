import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { Linking } from 'react-native'
import { Setting } from '../constants'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedSourceRepositorySetting: SettingsItem = {
    parent: Setting.Valenced,
    IconComponent: () => <TableRowAssetIcon name="PaperIcon" />,
    useTitle: () => 'Source Code',
    useDescription: () => __BUILD_SOURCE_REPOSITORY_URL__,
    onPress: () => {
        Linking.openURL(__BUILD_SOURCE_REPOSITORY_URL__)
    },
    type: 'pressable',
}

export default ValencedSourceRepositorySetting
