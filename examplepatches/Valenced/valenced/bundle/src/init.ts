// Initialize init libraries
import '@valenced/react/init'
import '@valenced/json-storage/init'

import { onRunApplication } from '@valenced/react/native'
import { onError } from '~index'

const unsub = onRunApplication(() => {
    unsub()

    try {
        // @as-require
        import('./start')
    } catch (e) {
        onError(e)
    }
})

// Run all init plugins
// @as-require
import '~/plugins/init'
// @as-require
import '@valenced/plugins/init'
