import { lookupModule } from '@valenced/modules/finders'
import { withName } from '@valenced/modules/finders/filters'
import type { DiscordModules } from '@valenced/discord/types'
import type { Metro } from '@valenced/modules/types'

// ../discord_common/js/packages/logger/Logger.tsx
export const [Logger, LoggerModuleId] = lookupModule(
    withName<typeof DiscordModules.Logger>('Logger'),
) as [typeof DiscordModules.Logger, Metro.ModuleID]
