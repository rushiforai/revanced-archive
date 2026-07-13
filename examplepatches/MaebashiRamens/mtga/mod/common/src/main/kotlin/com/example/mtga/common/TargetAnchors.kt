package com.example.mtga.common

/**
 * Version-INDEPENDENT locators for hook targets.
 *
 * [TargetSet] holds the obfuscated coordinates for one specific build — the
 * single-letter class and method names R8 reshuffles every release, and the
 * per-version calibration burden this object exists to reduce. The values here
 * are the opposite: signals R8 *never* rewrites. String literals survive minify
 * untouched (this app ships no string encryption), so a class that emits a
 * distinctive `const-string` in its own method body can be re-found on any build
 * by that string alone.
 *
 * These are distilled from the `HOW TO LOCATE` notes on [TargetSet]. Only the
 * subset whose anchor literal lives in the *target class's own bytecode* belongs
 * here — many notes locate a class by tracing a string that actually lives in a
 * JSON adapter or a Retrofit annotation (e.g. the `/api/v5/truth/ads` path, the
 * `smsCountry` adapter key), which a `const-string` scan can't attribute to the
 * class we want. Those stay calibration-only.
 *
 * ## How callers use these
 *
 * The resolution is **calibrated-first, anchor-fallback**, mirroring the
 * [StaticResolver] / FallbackResolver split: when the running build is in
 * [Targets.knownVersions] the verified [TargetSet] coordinate is used verbatim
 * (zero scan cost, zero behaviour change); only when that coordinate is absent
 * from the APK — an uncalibrated release, which would otherwise fail outright —
 * does the caller fall back to discovering the class by its anchor. The patch
 * vector does this against the DEX (see `resolveClassDescriptor` in the patches
 * module); the LSPosed vector has no const-string access at runtime, so it keeps
 * using FallbackResolver's reflection-based structural discovery instead.
 *
 * Keep these synced with the `HOW TO LOCATE` notes, never with one build's names.
 */
object TargetAnchors {
    /**
     * [TargetSet.integrityInterceptor] injects the Play Integrity assertion as
     * an OkHttp header; `addHeader("x-tru-assertion", …)` leaves this literal as
     * a `const-string` in the interceptor's intercept method.
     *
     * Not unique — a sibling helper class emits the same literal — so the caller
     * disambiguates structurally (the interceptor implements OkHttp's
     * `Interceptor`; the helper implements nothing). See `resolveClassDescriptor`.
     *
     * The Truth+ upsell and premium-roadblock route objects are intentionally
     * NOT anchored: their route string lives on the outer route holder (or in a
     * `route/{feature}` template), never as a bare `const-string` on the inner
     * `$a` route subclass the `instance-of` needs, so they stay calibration-only.
     */
    const val INTEGRITY_INTERCEPTOR = "x-tru-assertion"
}
