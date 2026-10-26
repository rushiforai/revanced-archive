import {
    createPatchedFunctionProxy,
    patchedFunctionProxyStates,
    unproxy,
} from '../_internal'
import type { HookNode, PatchedFunctionProxyState } from '../_internal'
import type {
    BeforeHook,
    FiniteDomain,
    HookOptions,
    UnknownFunction,
    UnpatchFunction,
} from '../types'

function unpatchBefore<T extends UnknownFunction>(
    state: PatchedFunctionProxyState<PropertyKey, T>,
    hookNode: HookNode<BeforeHook<T>>,
) {
    if (hookNode.unpatched) return

    hookNode.unpatched = true
    hookNode.hook = undefined

    const { prev, next } = hookNode
    if (prev === undefined) {
        state.before = next
        if (next === undefined) {
            if (state.instead === undefined && state.after === undefined)
                unproxy(state)
            return
        }
    } else {
        prev.next = next
        hookNode.prev = undefined
        if (next === undefined) return
    }
    next.prev = prev
    hookNode.next = undefined
}

/**
 * Before hooks allow you to modify the arguments passed to the original function, or to perform some action before the original function is called.
 *
 * ```js
 * import { before } from '@valenced/patcher'
 *
 * const obj = {
 *     method: (a) => console.log('Original method called with:', a)
 * }
 *
 * before(obj, 'method', ([a]) => {
 *     console.log('Before method called with:', a)
 *     // Modify arguments by returning new array
 *     return [a + 1]
 * })
 *
 * obj.method(2)
 * // CONSOLE OUTPUT:
 * // Before method called with: 2
 * // Original method called with: 3
 * ```
 *
 * @param parent The parent object containing the method to patch.
 * @param key The key of the method to patch.
 * @param hook The hook function to execute before the original method.
 * @param options Optional configuration including priority.
 *
 * @returns A function to unpatch.
 */
export function before<
    Parent extends Record<Key, UnknownFunction>,
    Key extends keyof Parent,
>(
    parent: Parent,
    key: Key,
    hook: BeforeHook<Parent[Key]>,
    options?: HookOptions,
): UnpatchFunction
export function before<Key extends PropertyKey, Value extends UnknownFunction>(
    parent: Record<Key, Value>,
    key: FiniteDomain<Key>,
    hook: BeforeHook<Value>,
    options?: HookOptions,
): UnpatchFunction {
    const target = parent[key]
    const priority = options?.priority ?? 0

    let state = patchedFunctionProxyStates.get(target)
    let hookNode: HookNode<typeof hook>
    if (state?.parent === parent && state.key === key) {
        let current = state.before
        let prev: HookNode<typeof hook> | undefined

        while (current && current.priority < priority) {
            prev = current
            current = current.next
        }

        hookNode = {
            hook,
            priority,
            next: current,
            prev,
            unpatched: false,
        }

        if (prev) {
            prev.next = hookNode
        } else {
            state.before = hookNode
        }

        if (current) {
            current.prev = hookNode
        }
    } else {
        hookNode = {
            hook,
            priority,
            next: undefined,
            prev: undefined,
            unpatched: false,
        }
        state = createPatchedFunctionProxy(
            target,
            parent,
            key,
            hookNode,
            undefined,
            undefined,
        )
    }

    return unpatchBefore.bind(undefined, state, hookNode)
}
