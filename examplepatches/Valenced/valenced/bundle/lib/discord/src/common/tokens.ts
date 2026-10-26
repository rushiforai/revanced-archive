import { lookupModule } from '@valenced/modules/finders'
import { withProps } from '@valenced/modules/finders/filters'
import type { Metro } from '@valenced/modules/types'

// ../discord_common/js/packages/tokens/native.tsx
export const [Tokens, TokensModuleId] = lookupModule(withProps('RawColor')) as [
    any,
    Metro.ModuleID,
]
