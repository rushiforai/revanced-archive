import { callNativeMethodSync } from '@valenced/modules/native'

export function reloadApp() {
    callNativeMethodSync('valenced.app.reload', [])
}

declare module '@valenced/modules/native' {
    interface NativeMethods {
        'valenced.app.reload': [[], null]
    }
}
