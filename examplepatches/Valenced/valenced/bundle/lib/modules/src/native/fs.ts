import {
    callNativeMethod,
    callNativeMethodSync,
} from '@valenced/modules/native'

export function readFile(path: string) {
    return callNativeMethod('valenced.fs.read', [path])
}

export function writeFile(path: string, data: string) {
    return callNativeMethod('valenced.fs.write', [path, data])
}

export function exists(path: string) {
    return callNativeMethod('valenced.fs.exists', [path])
}

export function rm(path: string) {
    return callNativeMethod('valenced.fs.delete', [path])
}

export function existsSync(path: string) {
    return callNativeMethodSync('valenced.fs.exists', [path])
}

export function readFileSync(path: string) {
    return callNativeMethodSync('valenced.fs.read', [path])
}

export function writeFileSync(path: string, data: string) {
    return callNativeMethodSync('valenced.fs.write', [path, data])
}

export function rmSync(path: string) {
    return callNativeMethodSync('valenced.fs.delete', [path])
}

export function deleteFileSync(path: string) {
    return callNativeMethodSync('valenced.fs.delete', [path])
}

export function getConstants() {
    return callNativeMethodSync('valenced.fs.getConstants', [])
}

declare module '@valenced/modules/native' {
    export interface NativeMethods {
        'valenced.fs.getConstants': [
            [],
            {
                data: string
                files: string
                cache: string
            },
        ]
        'valenced.fs.read': [[path: string], string]
        'valenced.fs.write': [[path: string, data: string], void]
        'valenced.fs.exists': [[path: string], boolean]
        'valenced.fs.delete': [[path: string], boolean]
    }
}
