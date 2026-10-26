import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { Setting } from '../constants'
import { CopyableSetting } from './shared'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const props = HermesInternal.getRuntimeProperties()

const HermesVersionSetting: SettingsItem = CopyableSetting(
    {
        parent: Setting.Valenced,
        IconComponent: () => <TableRowAssetIcon name="TranscriptOutlineIcon" />,
        useTitle: () => 'Hermes',
    },
    () => `${props['Bytecode Version']} (${props.Build})`,
)

export default HermesVersionSetting
