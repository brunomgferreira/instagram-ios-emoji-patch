package app.template.patches.instagram.emoji

import app.morphe.patcher.Fingerprint

/**
 * The androidx.emoji2 font loader's worker, which fetches the emoji font and builds EmojiCompat's
 * metadata from it. Matched on the trace section name it opens, since everything else is obfuscated.
 */
internal object EmojiCompatLoaderFingerprint : Fingerprint(
    name = "run",
    returnType = "V",
    parameters = listOf(),
    strings = listOf("EmojiCompat.MetadataRepo.create"),
)
