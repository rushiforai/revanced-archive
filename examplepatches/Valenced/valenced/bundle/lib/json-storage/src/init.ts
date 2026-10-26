import { JsonStorage } from '@valenced/json-storage'
import { useReRender } from '@valenced/utils/react'
import { useLayoutEffect } from 'react'
import type { JsonStorageSubscription } from '@valenced/json-storage'

const proto = JsonStorage.prototype as JsonStorage<any>
// Actual implementation of JsonStorage#use
proto.use = function (filter) {
    if (!this.cache) this.get()

    const reRender = useReRender()

    useLayoutEffect(() => {
        const sub: JsonStorageSubscription = filter
            ? (update, mode) => {
                  if (filter(update, mode)) reRender()
              }
            : reRender

        return this.subscribe(sub)
    }, [filter, reRender])

    return this.cache
}
