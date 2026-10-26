import { lookupModule } from '@valenced/modules/finders'
import { withDependencies, withProps } from '@valenced/modules/finders/filters'
import { ReactNativeModuleId } from '@valenced/react'
import { proxify } from '@valenced/utils/proxy'

const { partial, relative } = withDependencies

export let FlashList: typeof import('@shopify/flash-list') = proxify(
    () => {
        const [module] = lookupModule(
            withProps<typeof FlashList>('FlashList')
                .and(
                    // [isNewArch, ErrorMessages, FlashList, ...]
                    withDependencies(
                        partial([
                            // isNewArch: [ReactNative]
                            relative.withDependencies([ReactNativeModuleId], 1),
                            relative(2),
                            // FlashList: [RecyclerView]
                            relative.withDependencies([relative(1)], 3),
                        ]),
                    ),
                )
                .keyAs('valenced.externals.Shopify.FlashList'),
        )

        if (module) return (FlashList = module)
    },
    {
        hint: {},
    },
)!
