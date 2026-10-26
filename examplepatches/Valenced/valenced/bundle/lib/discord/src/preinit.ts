import { lookupModule } from '@valenced/modules/finders'
import {
    withDependencies,
    withName,
    withProps,
} from '@valenced/modules/finders/filters'
import { ImportTrackerModuleId } from './patches/import-tracker'
import type { Metro } from '@valenced/modules/types'
import type { DiscordModules } from './types'

const [, _asyncToGeneratorModuleId] = lookupModule(
    withName('_asyncToGenerator'),
)
const [, _classCallCheckModuleId] = lookupModule(withName('_classCallCheck'))
const [, _createClassModuleId] = lookupModule(withName('_createClass'))

// ../discord_common/js/packages/app-start-performance/AppStartPerformance.tsx
export const [AppStartPerformance] = lookupModule(
    withProps<DiscordModules.AppStartPerformance>('markAndLog').and(
        withDependencies([_asyncToGeneratorModuleId, ImportTrackerModuleId]),
    ),
) as [DiscordModules.AppStartPerformance, Metro.ModuleID]

AppStartPerformance.mark('👊', 'Pre-init')

import './patches/flux'
