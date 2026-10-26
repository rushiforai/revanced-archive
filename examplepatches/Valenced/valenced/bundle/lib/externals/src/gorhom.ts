import { lookupModule } from '@valenced/modules/finders'
import { withDependencies, withProps } from '@valenced/modules/finders/filters'
import { proxify } from '@valenced/utils/proxy'

const { partial, relative } = withDependencies

export let BottomSheet: typeof import('@gorhom/bottom-sheet') = proxify(
    () => {
        const [module] = lookupModule(
            withProps<typeof BottomSheet>(
                'BottomSheetModal',
                'BottomSheetScrollView',
            )
                // [constants, ...]
                .and(withDependencies(partial([relative(1), relative(2)])))
                .keyAs('valenced.externals.Gorhom.BottomSheet'),
        )

        if (module) return (BottomSheet = module)
    },
    {
        hint: {},
    },
)!
