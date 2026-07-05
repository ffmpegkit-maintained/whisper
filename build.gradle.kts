plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

// JitPack invokes `publishToMavenLocal` at the root — delegate to the library module
// so the AAR resolves out-of-the-box from a GitHub tag.
tasks.register("publishToMavenLocal") {
    dependsOn(":library:publishToMavenLocal")
}
