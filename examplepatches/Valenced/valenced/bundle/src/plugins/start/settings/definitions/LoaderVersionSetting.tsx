import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { getBridgeInfo } from '@valenced/modules/native'
import { Setting } from '../constants'
import { CopyableSetting } from './shared'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const bridgeInfo = getBridgeInfo()

const LoaderVersionSetting: SettingsItem = CopyableSetting(
    {
        parent: Setting.Valenced,
        IconComponent: () => <TableRowAssetIcon name="SendMessageIcon" />,
        useTitle: () => 'Loader',
        usePredicate: () => Boolean(bridgeInfo),
    },
    () => `${bridgeInfo!.name} (${bridgeInfo!.version})`,
)

export default LoaderVersionSetting
