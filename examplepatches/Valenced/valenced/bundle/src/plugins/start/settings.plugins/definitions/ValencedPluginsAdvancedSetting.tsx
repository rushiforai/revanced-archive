import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { RouteNames, Setting } from '../constants'
import defer * as ValencedPluginsAdvancedSettingScreen from '../screens/ValencedPluginsAdvancedSettingScreen'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedPluginsAdvancedSetting: SettingsItem = {
    parent: null,
    type: 'route',
    IconComponent: () => <TableRowAssetIcon name="SettingsIcon" />,
    useTitle: () => 'Advanced',
    screen: {
        route: RouteNames[Setting.ValencedPluginsAdvanced],
        getComponent: () => ValencedPluginsAdvancedSettingScreen.default,
    },
}

export default ValencedPluginsAdvancedSetting
