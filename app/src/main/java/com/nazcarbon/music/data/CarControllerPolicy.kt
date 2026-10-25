package com.nazcarbon.music.data

object CarControllerPolicy {
    const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
    fun canBrowse(trusted: Boolean, automotive: Boolean, companion: Boolean, packageName: String,
        uidOwnsPackage: Boolean, matchesGoogleSignature: Boolean): Boolean =
        (automotive || companion) && (trusted || (companion && packageName == ANDROID_AUTO_PACKAGE &&
            uidOwnsPackage && matchesGoogleSignature))
}
