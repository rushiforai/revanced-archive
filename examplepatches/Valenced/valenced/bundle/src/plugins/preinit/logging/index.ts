import { AppStartPerformance } from '@valenced/discord/preinit'
import { registerInternalPlugin } from '@valenced/plugins/_'
import { BuildEnvironment, FullVersion } from '~/constants'
import manifest from './manifest.json'

if (__DEV__) {
    AppStartPerformance.mark('✨', 'Plugins register')

    const tsReg = performance.now()
    let tsPreInit: number
    let tsInit: number

    registerInternalPlugin(manifest, {
        preInit() {
            tsPreInit = performance.now()
            AppStartPerformance.mark('✨', 'Plugins preInit', tsPreInit - tsReg)
        },
        init() {
            tsInit = performance.now()
            AppStartPerformance.mark('✨', 'Plugins init', tsInit - tsPreInit)
        },
        start({ logger }) {
            nativeLoggingHook(`\u001b[31m--- START STAGE ---\u001b[0m`, 1)

            AppStartPerformance.mark(
                '✨',
                'Plugins start',
                performance.now() - tsInit,
            )
            logger.log(
                `✨ Valenced. Supercharge your Discord experience. (${FullVersion} (${BuildEnvironment}))`,
            )
        },
    })
}
