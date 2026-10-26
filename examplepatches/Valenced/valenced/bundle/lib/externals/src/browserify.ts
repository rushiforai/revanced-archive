import { lookupModule } from '@valenced/modules/finders'
import { withDependencies, withProps } from '@valenced/modules/finders/filters'
import { proxify } from '@valenced/utils/proxy'

const { partial, relative } = withDependencies

export let nodeUtil: typeof import('node:util') = proxify(
    () => {
        const [module] = lookupModule(
            withProps<typeof nodeUtil>('inspect').and(
                withDependencies([
                    partial([relative(2, true), relative(4, true)]),
                    [],
                    [],
                ]),
            ),
        )

        if (module) return (nodeUtil = module)
    },
    {
        hint: {},
    },
)!
