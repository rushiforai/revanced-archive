import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { FullVersion } from '~/constants'
import ValencedIcon from '~assets/ValencedIcon'
import { RouteNames, Setting } from '../constants'
import defer * as ValencedSettingScreen from '../screens/ValencedSettingScreen'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedSetting: SettingsItem = {
    parent: null,
    type: 'route',
    IconComponent: () => <TableRowAssetIcon id={ValencedIcon} />,
    useTitle: () => 'Valenced',
    useTrailing: () => FullVersion,
    screen: {
        route: RouteNames[Setting.Valenced],
        getComponent: () => ValencedSettingScreen.default,
    },
}

export default ValencedSetting
