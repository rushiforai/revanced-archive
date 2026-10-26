import { getAssetIdByName } from '@valenced/assets'
import { Design } from '@valenced/discord/design'
import type { DiscordModules } from '@valenced/discord/types'

export default function TableRowAssetIcon(props: TableRowAssetIconProps) {
    return (
        <Design.TableRow.Icon
            source={props.name ? getAssetIdByName(props.name)! : props.id}
            {...props}
        />
    )
}

export type TableRowAssetIconProps = Omit<
    DiscordModules.Components.TableRowIconProps,
    'source'
> &
    (
        | {
              name: string
              id?: never
              IconComponent?: never
          }
        | {
              name?: never
              id: number
              IconComponent?: never
          }
        | {
              name?: never
              id?: never
              IconComponent: DiscordModules.Components.BaseIconImage
          }
    )
