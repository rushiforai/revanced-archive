## [1.2.4](https://github.com/NL-TCH/portfolio-performance-freemium-patch/compare/v1.2.3...v1.2.4) (2026-07-27)


### Bug Fixes

* disable issue-related github features, add issues:write permission ([5f6e8cb](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/5f6e8cb9f690bf08cd00e076ae2ff6a995320c4c))

## [1.2.3](https://github.com/NL-TCH/portfolio-performance-freemium-patch/compare/v1.2.2...v1.2.3) (2026-07-27)


### Bug Fixes

* add @semantic-release/exec dep, remove gradle-semantic-release-plugin ([e599e46](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/e599e464520fc3efddb19d577ba4fbd4c3de2eea))
* replace gradle-semantic-release-plugin with exec for version bumping ([747116c](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/747116ce194e4ebb1a1d591611e84236e4f34114))

## [1.2.2](https://github.com/NL-TCH/portfolio-performance-freemium-patch/compare/v1.2.1...v1.2.2) (2026-07-27)


### Bug Fixes

* disable GPG signing and skip gradle publish step (rvp already built) ([28b528f](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/28b528f28e31db33d76f9ea55d5025fb45c1ff4b))

## [1.2.1](https://github.com/NL-TCH/portfolio-performance-freemium-patch/compare/v1.2.0...v1.2.1) (2026-07-27)


### Bug Fixes

* disable GPG signing for now ([a8800fc](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/a8800fc0b8e720ead0a061f2916449f91d3bc01c))

# [1.2.0](https://github.com/NL-TCH/portfolio-performance-freemium-patch/compare/v1.1.0...v1.2.0) (2026-07-27)


### Bug Fixes

* add dependencyResolutionManagement for revanced-patcher resolution ([22ab63c](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/22ab63ce24bf4b8ce3233d8c934426f27796e56e))
* build DEX for Android, fix source URL ([63b9d75](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/63b9d7593bab463bc9996c10e047afbc9908920d))
* commit package-lock.json for CI npm cache, remove from .gitignore ([0b6eeb9](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/0b6eeb9bccbac71675be23f846991d07a5a7292d))
* env vars for all Gradle invocations including semantic-release ([41c5b10](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/41c5b1090e8a0a54acfd98e9c3cb711e29884e80))
* pass githubPackagesUsername/Password for plugin 1.0.0-dev.11 ([5e270a8](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/5e270a848b1624175586e7a850f556600a0cd3bc))
* remove token placeholder from gradle.properties, pass via Gradle CLI in CI ([7bb5d2c](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/7bb5d2ccfc1cb188f2b0125fd3854f8383a25ff5))
* revert settings.gradle.kts to working pluginManagement-only form ([103f1c3](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/103f1c3a42996cddc2b2a383ba77ed6f65ddab3f))
* revert to working plugin/patcher versions, restore fingerprint API ([ed035ea](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/ed035ea6aee48c9ce2ec24e2a328280051f1b6ef))
* substitute revanced-patcher -> patcher for v22 compatibility ([6384702](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/6384702c6e20b2caaf342b491de1be2725b0c37e))
* try latest plugin app.revanced.patches:1.0.0-dev.11 ([411aa3b](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/411aa3b04ecdde320fdae981230138a2127edef1))
* try new plugin com.revanced.patches:1.0.0-dev.8 for v22 support ([f5492ab](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/f5492ab15df815addf6e2206d16439e1e70091c2))
* update Kotlin to 2.3.10 and smali to 3.0.9 for patcher v22 compatibility ([0e657e4](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/0e657e427d0ea3e81f36c648e76d36d4237b8751))
* update patch for v1.2.4 obfuscated types ([accd810](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/accd81003cd26d046c7a1fc56afe4aaa86bbdf94))
* upgrade Gradle to 9.1.0 (required by plugin 1.0.0-dev.11 with AGP 9.0) ([7c1abb7](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/7c1abb7f886b3acf3bea16d96ff4408f32d48033))
* use eachDependency to redirect revanced-patcher -> patcher for v22 ([1ea0e55](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/1ea0e554c1451f21d5a9fb4374c5f4d3a6069950))
* use ORG_GRADLE_PROJECT env vars for all Gradle invocations, Java 21 ([b4f27c6](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/b4f27c65074a43e19a1b1d29f2fa2f68d75303e5))
* VerifyError from .locals 0 register aliasing in method b ([bd41061](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/bd41061ca38b050e5e4e9a5404de8bf95c13a6d5))


### Features

* migrate to ReVanced Patcher v22, add ReVanced Manager v2 remote patching support ([ab4e163](https://github.com/NL-TCH/portfolio-performance-freemium-patch/commit/ab4e16373180b03a169356760362ec08de747e7e))
