import {
    registerSettingsItems,
    registerSettingsSection,
} from '@valenced/discord/modules/settings'
import { Setting } from './constants'
import HermesVersionSetting from './definitions/HermesVersionSetting'
import LoaderVersionSetting from './definitions/LoaderVersionSetting'
import ReactNativeVersionSetting from './definitions/ReactNativeVersionSetting'
import ReactVersionSetting from './definitions/ReactVersionSetting'
import ReloadSetting from './definitions/ReloadSetting'
import ValencedDeveloperModeSetting from './definitions/ValencedDeveloperModeSetting'
import ValencedDiscordSetting from './definitions/ValencedDiscordSetting'
import ValencedLicenseSetting from './definitions/ValencedLicenseSetting'
import ValencedSetting from './definitions/ValencedSetting'
import ValencedSourceRepositorySetting from './definitions/ValencedSourceRepositorySetting'
import ValencedVersionSetting from './definitions/ValencedVersionSetting'

registerSettingsItems({
    [Setting.Valenced]: ValencedSetting,
    [Setting.ValencedSourceRepository]: ValencedSourceRepositorySetting,
    [Setting.ValencedLicense]: ValencedLicenseSetting,
    [Setting.ValencedDiscord]: ValencedDiscordSetting,
    [Setting.Reload]: ReloadSetting,
    [Setting.ValencedDeveloperMode]: ValencedDeveloperModeSetting,
    [Setting.ValencedVersion]: ValencedVersionSetting,
    [Setting.ReactVersion]: ReactVersionSetting,
    [Setting.ReactNativeVersion]: ReactNativeVersionSetting,
    [Setting.HermesVersion]: HermesVersionSetting,
    [Setting.LoaderVersion]: LoaderVersionSetting,
})

registerSettingsSection('VALENCED', {
    label: 'Valenced',
    settings: [Setting.Valenced],
})
