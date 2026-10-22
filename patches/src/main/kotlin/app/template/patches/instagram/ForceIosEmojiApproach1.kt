package app.template.patches.instagram

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

@Suppress("unused")
val forceIosEmojiApproach1 = bytecodePatch(
    name = "Force iOS Emojis (Approach 1 - Asset Direct)",
    description = "Downloads and embeds AppleColorEmoji.ttf into app assets without altering bytecode strings.",
    default = true
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute { context ->
        println("[Approach 1] Downloading font from GitHub release...")
        val fontUrl = "https://github.com/brunomgferreira/instagram-ios-emoji-patch/releases/download/v1.0.0/AppleColorEmoji.ttf"
        
        val url = URL(fontUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.connect()

        val fontBytes = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.use { it.readBytes() }
        } else {
            throw IllegalStateException("Failed to download emoji font from: $fontUrl")
        }

        // Inject font directly into app assets
        context.addAsset("fonts/ios_emoji.ttf", ByteArrayInputStream(fontBytes))
        println("[Approach 1] Successfully embedded ios_emoji.ttf into assets/fonts/")
    }
}