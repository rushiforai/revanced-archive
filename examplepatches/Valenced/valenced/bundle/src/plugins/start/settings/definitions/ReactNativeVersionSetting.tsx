import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { Setting } from '../constants'
import { CopyableSetting, getRNVersion } from './shared'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ReactNativeVersionSetting: SettingsItem = CopyableSetting(
    {
        parent: Setting.Valenced,
        IconComponent: () => <TableRowAssetIcon name="ScienceIcon" />,
        useTitle: () => 'React Native',
    },
    getRNVersion,
)

export default ReactNativeVersionSetting
