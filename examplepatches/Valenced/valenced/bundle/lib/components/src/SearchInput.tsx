import { Design } from '@valenced/discord/design'
import { lookupGeneratedIconComponent } from '@valenced/utils/discord'
import type { DiscordModules } from '@valenced/discord/types'

const MagnifyingGlassIcon = lookupGeneratedIconComponent('MagnifyingGlassIcon')

export default function SearchInput(
    props: DiscordModules.Components.TextInputProps,
) {
    return (
        <Design.TextInput
            leadingIcon={MagnifyingGlassIcon}
            placeholder="Search"
            returnKeyType="search"
            size="md"
            {...props}
        />
    )
}
