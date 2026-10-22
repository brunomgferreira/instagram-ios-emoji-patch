package app.template.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    val COMPATIBILITY_INSTAGRAM = Compatibility(
        name = "Instagram",
        packageName = "com.instagram.android",
        targets = listOf(
            AppTarget(
                version = "439.0.0.37.89",
                versionCodes = mapOf(
                    SupportedAbi.ARM64_V8A to 384510827,
                    SupportedAbi.ARMEABI_V7A to 384510838
                )
            )
        )
    )
}
