import {
    onSettingsModulesLoaded,
    registerSettingsItem,
} from '@valenced/discord/modules/settings'
import { Setting } from '~plugins/start/settings/constants'
import ValencedEnterRecoveryModeSetting from './definitions/ValencedEnterRecoveryModeSetting'

onSettingsModulesLoaded(() => {
    registerSettingsItem(
        Setting.ValencedEnterRecoveryMode,
        ValencedEnterRecoveryModeSetting,
    )
})
