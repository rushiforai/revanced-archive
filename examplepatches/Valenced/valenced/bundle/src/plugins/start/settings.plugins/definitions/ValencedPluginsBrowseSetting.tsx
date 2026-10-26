import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { RouteNames, Setting } from '../constants'
import defer * as ValencedPluginsBrowseSettingScreen from '../screens/ValencedPluginsBrowseSettingScreen'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedPluginsBrowseSetting: SettingsItem = {
    parent: null,
    type: 'route',
    IconComponent: () => <TableRowAssetIcon name="PlusLargeIcon" />,
    useTitle: () => 'Browse Plugins',
    screen: {
        route: RouteNames[Setting.ValencedPluginsBrowse],
        getComponent: () => ValencedPluginsBrowseSettingScreen.default,
    },
}

export default ValencedPluginsBrowseSetting
