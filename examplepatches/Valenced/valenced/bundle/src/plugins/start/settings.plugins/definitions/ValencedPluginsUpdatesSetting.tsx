import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { RouteNames, Setting } from '../constants'
import defer * as ValencedPluginsUpdatesSettingScreen from '../screens/ValencedPluginsUpdatesSettingScreen'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedPluginsUpdatesSetting: SettingsItem = {
    parent: null,
    type: 'route',
    IconComponent: () => <TableRowAssetIcon name="DownloadIcon" />,
    useTitle: () => 'Plugin Updates',
    screen: {
        route: RouteNames[Setting.ValencedPluginsUpdates],
        getComponent: () => ValencedPluginsUpdatesSettingScreen.default,
    },
}

export default ValencedPluginsUpdatesSetting
