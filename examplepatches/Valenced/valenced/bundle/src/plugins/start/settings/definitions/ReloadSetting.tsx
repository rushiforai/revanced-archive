import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { reloadApp } from '@valenced/modules/native/app'
import { Setting } from '../constants'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ReloadSetting: SettingsItem = {
    parent: Setting.Valenced,
    IconComponent: () => <TableRowAssetIcon name="RetryIcon" />,
    useTitle: () => 'Reload App',
    onPress: () => {
        reloadApp()
    },
    type: 'pressable',
}

export default ReloadSetting
