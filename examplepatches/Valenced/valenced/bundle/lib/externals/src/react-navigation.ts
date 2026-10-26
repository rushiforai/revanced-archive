import { lookupModule } from '@valenced/modules/finders'
import {
    withDependencies,
    withName,
    withProps,
} from '@valenced/modules/finders/filters'
import { ReactJSXRuntimeModuleId, ReactModuleId } from '@valenced/react'
import { proxify } from '@valenced/utils/proxy'

const { partial, relative } = withDependencies

export let ReactNavigationNative: typeof import('@react-navigation/native') =
    proxify(
        () => {
            const [, _createClassModuleId] = lookupModule(
                withName('_createClass'),
            )
            const [, _classCallCheckModuleId] = lookupModule(
                withName('_classCallCheck'),
            )

            const [module] = lookupModule(
                withProps<typeof ReactNavigationNative>('useLinkTo').and(
                    withDependencies(
                        partial([
                            [],
                            partial([
                                [_createClassModuleId, _classCallCheckModuleId],
                            ]),
                        ]),
                    ),
                ),
            )

            if (module) return (ReactNavigationNative = module)
        },
        {
            hint: {},
        },
    )!

export let ReactNavigationStack: typeof import('@react-navigation/stack') =
    proxify(
        () => {
            const firstDep = relative.withDependencies(
                partial([
                    ReactModuleId,
                    ReactJSXRuntimeModuleId,
                    null,
                    relative(2, true),
                ]),
                1,
            )

            const [module] = lookupModule(
                withProps<typeof ReactNavigationStack>('StackView')
                    .and(
                        withDependencies(
                            partial([firstDep, null, relative(2)]),
                        ),
                    )
                    .keyAs(
                        'valenced.externals.ReactNavigation.ReactNavigationStack',
                    ),
            )

            if (module) return (ReactNavigationStack = module)
        },
        {
            hint: {},
        },
    )!

export interface ReactNavigationParamList {
    [Page: string]: any
}

declare global {
    namespace ReactNavigation {
        interface RootParamList extends ReactNavigationParamList {}
    }
}
