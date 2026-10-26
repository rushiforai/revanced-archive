import type { RouteNames } from './constants'

declare module '@valenced/externals/react-navigation' {
    interface ReactNavigationParamList extends ValencedSettingsParamList {}
}

type ValencedSettingsParamList = {
    [K in (typeof RouteNames)[keyof typeof RouteNames]]: object
}
