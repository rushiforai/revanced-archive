import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { isDefaultsOnlyBoot } from '@valenced/plugins/_'
import { useEnabledPluginCountInActiveSlot } from '@valenced/plugins/_/react'
import { RouteNames, Setting } from '../constants'
import defer * as ValencedPluginsSettingScreen from '../screens/ValencedPluginsSettingScreen'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedPluginsSetting: SettingsItem = {
    parent: null,
    type: 'route',
    IconComponent: () => <TableRowAssetIcon name="PuzzlePieceIcon" />,
    useTitle: () => 'Plugins',
    useTrailing: () =>
        `${useEnabledPluginCountInActiveSlot()} enabled` +
        (isDefaultsOnlyBoot ? ' (recovery)' : ''),
    screen: {
        route: RouteNames[Setting.ValencedPlugins],
        getComponent: () => ValencedPluginsSettingScreen.default,
    },
}

export default ValencedPluginsSetting
