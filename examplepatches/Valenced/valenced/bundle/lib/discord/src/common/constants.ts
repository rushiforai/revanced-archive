import { lookupModule } from '@valenced/modules/finders'
import { withDependencies, withProps } from '@valenced/modules/finders/filters'
import type { DiscordModules } from '@valenced/discord/types'

const { partial, relative } = withDependencies

export const [Constants, ConstantsModuleId] = lookupModule(
    withProps<DiscordModules.Constants>('ME')
        .and(
            withDependencies(
                partial([
                    null,
                    relative.withDependencies(partial([relative(2, true)]), 1),
                ]),
            ),
        )
        .keyAs('valenced.discord.common.Constants'),
)
