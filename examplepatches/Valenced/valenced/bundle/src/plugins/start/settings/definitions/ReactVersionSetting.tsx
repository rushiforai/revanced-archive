import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { version } from 'react'
import { Setting } from '../constants'
import { CopyableSetting } from './shared'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ReactVersionSetting: SettingsItem = CopyableSetting(
    {
        parent: Setting.Valenced,
        IconComponent: () => <TableRowAssetIcon name="ScienceIcon" />,
        useTitle: () => 'React',
    },
    () => version,
)

export default ReactVersionSetting
