dependencies {
    // Everything this patch stands on is already in the app: it is put there by
    // ReVanced's own extension, whichever bundle brings it. Compiling against it
    // without shipping it is what keeps this extension to its own classes, so that
    // both bundles can be picked at once.
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:bettercaptions:stub"))
    compileOnly(project(":extensions:youtube:stub"))
    compileOnly(libs.annotation)
}

android {
    defaultConfig {
        minSdk = 26
    }
}
