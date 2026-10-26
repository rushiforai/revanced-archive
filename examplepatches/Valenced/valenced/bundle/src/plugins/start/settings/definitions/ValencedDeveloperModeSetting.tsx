import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { ToastActionCreators } from '@valenced/discord/actions'
import {
    disablePluginInActiveSlot,
    enablePluginInActiveSlot,
    isDefaultsOnlyBoot,
    pList,
    runPluginLate,
} from '@valenced/plugins/_'
import { usePluginEnabledById } from '@valenced/plugins/_/react'
import pluginHiddenApi from '~plugins/preinit/api.hidden'
import { Setting } from '../constants'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedDeveloperModeSetting: SettingsItem = {
    parent: Setting.Valenced,
    type: 'toggle',
    IconComponent: () => <TableRowAssetIcon name="WrenchIcon" />,
    useTitle: () => 'Developer Mode',
    useDescription: () =>
        isDefaultsOnlyBoot
            ? 'Unavailable in Recovery Mode. Reload to exit.'
            : 'Exposes internal Valenced APIs for development purposes. Makes client mod detection easier. Use with caution.',
    useIsDisabled: () => isDefaultsOnlyBoot,
    useValue: () => usePluginEnabledById(pluginHiddenApi),
    onValueChange: enabled => {
        const plugin = pList.get(pluginHiddenApi)
        if (!plugin) return

        if (enabled)
            enablePluginInActiveSlot(plugin, true)
                .then(() => runPluginLate(plugin))
                .catch(showFailureToast)
        else disablePluginInActiveSlot(plugin).catch(showFailureToast)
    },
}

function showFailureToast(e: unknown) {
    ToastActionCreators.open('VALENCED_DEVELOPER_MODE_FAILED', {
        text: `Failed to toggle Developer Mode: ${e instanceof Error ? e.message : String(e)}`,
        variant: 'critical',
    })
}

export default ValencedDeveloperModeSetting
