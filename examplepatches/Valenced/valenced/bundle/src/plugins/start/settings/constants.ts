export const Setting = {
    // MAIN SETTINGS

    Valenced: 'Valenced',

    // SUBSETTINGS

    ValencedDiscord: 'ValencedDiscord',
    ValencedSourceRepository: 'ValencedSourceRepository',
    ValencedLicense: 'ValencedLicense',
    Reload: 'Reload',
    ValencedDeveloperMode: 'ValencedDeveloperMode',
    ValencedEnterRecoveryMode: 'ValencedEnterRecoveryMode',

    ValencedVersion: 'ValencedVersion',
    ReactVersion: 'ReactVersion',
    ReactNativeVersion: 'ReactNativeVersion',
    HermesVersion: 'HermesVersion',
    LoaderVersion: 'LoaderVersion',
} as const

export const RouteNames = {
    [Setting.Valenced]: 'Valenced',
} as const
