import { lookupModule } from '@valenced/modules/finders'
import { withDependencies, withProps } from '@valenced/modules/finders/filters'
import { ImportTrackerModuleId } from '../patches/import-tracker'
import type { Metro } from '@valenced/modules/types'
import type { DiscordModules } from '../types'

const { relative, partial } = withDependencies

// ../discord_common/js/packages/flux

export const [Dispatcher, DispatcherModuleId] = lookupModule(
    withProps<DiscordModules.Flux.Dispatcher>('_interceptors').and(
        withDependencies([
            relative(1),
            null,
            null,
            partial([relative(1), relative(2)]),
            null,
            ImportTrackerModuleId,
        ]),
    ),
) as [DiscordModules.Flux.Dispatcher, Metro.ModuleID]
