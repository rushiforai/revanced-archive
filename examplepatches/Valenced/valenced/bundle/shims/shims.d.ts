declare global {
    // CommonJS modules, we don't want to depend on @types/node
    const module: {
        exports: any
    }
}

export {}
