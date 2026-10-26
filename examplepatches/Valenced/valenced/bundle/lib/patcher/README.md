# @valenced/patcher

This is a JavaScript library that allows to monkey patch functions in a way that is easy to use and understand.

It is made by [ryan-0324](https://github.com/ryan-0324) [(source uploaded on Discord)](https://discord.com/channels/1205207689832038522/1284131216156655646/1316076586981789709) and licensed under [CC0](./LICENSE).

This source code is polished and adapted to work with Valenced. We do not claim any rights to this library. You may use it granted you follow the license.

## How does this work?

Think of hooks as separate functions that are called `before`, `after`, or `instead` of the original function.
You can use them to modify the behavior of the original function, or to add new functionality.

### Before hooks

Before hooks allow you to modify the arguments passed to the original function, or to perform some action before the original function is called.

```js
import { before } from '@valenced/patcher'

const obj = {
    method: (a) => console.log('Original method called with:', a)
}

before(obj, 'method', ([a]) => {
    console.log('Before method called with:', a)
    // Modify arguments by returning new array
    return [a + 1]
})

obj.method(2)
// CONSOLE OUTPUT:
// Before method called with: 2
// Original method called with: 3
```

### After hooks

After hooks allow you to modify the return value of the original function, or to perform some action after the original function is called.

```js
import { after } from '@valenced/patcher'

const obj = {
    method: (a) => a * 2
}

after(obj, 'method', (result) => {
    console.log('After method called with result:', result)
    // Modify return value
    return result + 1
})

console.log(obj.method(2)) // 5
// CONSOLE OUTPUT:
// After method called with result: 4
// 5
```

### Instead hooks

Instead hooks allow you to completely replace the original function with a new one, while still being able to call the original function if needed.

```js
import { instead } from '@valenced/patcher'

const obj = {
    method: (a) => {
        console.log('Original method called with:', a)
        return 'original result'
    }
}

instead(obj, 'method', ([a], original) => {
    console.log('Instead method called with:', a)
    // Call the original function if needed
    const originalResult = original('modified')
    console.log('Original method was called')
    
    // Return a new value
    return 'new value'
})

console.log(obj.method('test')) // 'new value'
// CONSOLE OUTPUT:
// Instead method called with: test
// Original method called with: modified
// Original method was called
// new value
```

### Combining hooks

You can combine multiple hooks on the same function.

```js
import { before, after, instead } from '@valenced/patcher'

const obj = {
    method: (a) => a * 2
}

before(obj, 'method', ([a]) => {
    console.log('Before method called with:', a)
    return [a + 1] // 2 becomes 3
})

instead(obj, 'method', ([a], original) => {
    console.log('Instead method called with:', a)
    const result = original(a) // Call original with modified arg (3)
    console.log('Original method returned:', result)
    return result + 1 // 6 becomes 7  
})

after(obj, 'method', (result) => {
    console.log('After method called with result:', result)
    return result + 1 // 7 becomes 8
})

console.log(obj.method(2)) // 8
// CONSOLE OUTPUT:
// Before method called with: 2
// Instead method called with: 3
// Original method returned: 6
// After method called with result: 7
// 8
```

### Stacking hooks

Stacking multiple hooks of same type will execute them in a specific order:

1. **Before hooks** (execute in reverse order - last added runs first)
2. **Instead hooks** (only the last added executes, unless calling original)
3. **After hooks** (execute in order - first added runs first)

Using multiple `before` hooks will execute them in **reverse order** (LIFO - Last In, First Out):

```js
import { before } from '@valenced/patcher'

const obj = {
    method: (a) => console.log('Original method called with:', a)
}

before(obj, 'method', ([a]) => {
    console.log('First before hook called with:', a)
    return [a + 1]
})

before(obj, 'method', ([a]) => {
    console.log('Second before hook called with:', a)
    return [a + 2]
})

obj.method(2)
// CONSOLE OUTPUT:
// Second before hook called with: 2
// First before hook called with: 4
// Original method called with: 5
```

Multiple `after` hooks execute in **normal order** (FIFO - First In, First Out):

```js
import { after } from '@valenced/patcher'

const obj = {
    method: (a) => a * 2
}

after(obj, 'method', (result) => {
    console.log('First after hook called with result:', result)
    return result + 1
})

after(obj, 'method', (result) => {
    console.log('Second after hook called with result:', result)
    return result + 2
})

console.log(obj.method(2)) // 7
// CONSOLE OUTPUT:
// First after hook called with result: 4
// Second after hook called with result: 5
// 7
```

Multiple `instead` hooks will only execute the **last one added**. To chain them, you must call the original function:

```js
import { instead } from '@valenced/patcher'

const obj = {
    method: (a) => `original-${a}`
}

instead(obj, 'method', ([a], original) => {
    console.log('First instead hook called with:', a)
    return `first-${original(a)}`
})

instead(obj, 'method', ([a], original) => {
    console.log('Second instead hook called with:', a)
    // Calling original will execute the previous instead hook
    return `second-${original(a)}`
})

console.log(obj.method('test')) // 'second-first-original-test'
// CONSOLE OUTPUT:
// Second instead hook called with: test
// First instead hook called with: test
// second-first-original-test
```

### Removing hooks

Each hook returns an unpatch function that removes the hook:

```js
import { before } from '@valenced/patcher'

const obj = {
    method: (a) => console.log('Original method called with:', a)
}

const unpatch = before(obj, 'method', ([a]) => {
    console.log('Before method called with:', a)
    return [a + 1]
})

obj.method(2)
// CONSOLE OUTPUT:
// Before method called with: 2
// Original method called with: 3

unpatch() // Remove the hook

obj.method(2)
// CONSOLE OUTPUT:
// Original method called with: 2
```

Unpatch functions are safe to call multiple times and won't throw errors.

### Hook priority

All hooks support an optional priority parameter to control execution order. Higher priority hooks execute before lower priority hooks.

```js
import { before, after, instead, HookPriority } from '@valenced/patcher'

const obj = {
    method: (a) => a
}

// Before hooks execute in ascending priority order (lowest to highest)
before(obj, 'method', (args) => {
    console.log('Low priority before hook')
    return args
}, { priority: HookPriority.LOW })

before(obj, 'method', (args) => {
    console.log('High priority before hook')
    return args
}, { priority: HookPriority.HIGH })

obj.method(1)
// CONSOLE OUTPUT:
// Low priority before hook
// High priority before hook

// After hooks execute in descending priority order (highest to lowest)
after(obj, 'method', (result) => {
    console.log('High priority after hook')
    return result
}, { priority: HookPriority.HIGH })

after(obj, 'method', (result) => {
    console.log('Low priority after hook')
    return result
}, { priority: HookPriority.LOW })

obj.method(1)
// CONSOLE OUTPUT:
// High priority after hook
// Low priority after hook

// Instead hooks execute in descending priority order (highest to lowest)
instead(obj, 'method', function (args, original) {
    console.log('High priority instead hook')
    return original.apply(this, args)
}, { priority: HookPriority.HIGH })

instead(obj, 'method', function (args, original) {
    console.log('Low priority instead hook')
    return original.apply(this, args)
}, { priority: HookPriority.LOW })

obj.method(1)
// CONSOLE OUTPUT:
// High priority instead hook
// Low priority instead hook
```

Priority constants:

- `HookPriority.HIGHEST` (1000)
- `HookPriority.HIGH` (500)
- `HookPriority.NORMAL` (0) - default
- `HookPriority.LOW` (-500)
- `HookPriority.LOWEST` (-1000)

You can also use custom numeric values for fine-grained control.

### Constructor patching

You can also patch constructor functions:

```js
import { before } from '@valenced/patcher'

class MyClass {
    constructor(value) {
        this.value = value
    }
}

const exports = { MyClass }

before(exports, 'MyClass', ([value]) => {
    console.log('Constructor called with:', value)
    return [`modified-${value}`]
})

const instance = new exports.MyClass('test')
console.log(instance.value) // 'modified-test'
// CONSOLE OUTPUT:
// Constructor called with: test
// modified-test
```
