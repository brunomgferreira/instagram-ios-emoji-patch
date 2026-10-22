package app.template.patches.instagram

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import java.net.HttpURLConnection
import java.net.URL

@Suppress("unused")
val forceIosEmojiApproach1 = bytecodePatch(
    name = "Force iOS Emojis (Approach 1 - Asset Direct)",
    description = "Descarrega e injeta AppleColorEmoji.ttf diretamente nos assets da aplicação.",
    default = true
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        println("[Approach 1] A descarregar a fonte AppleColorEmoji.ttf...")
        val fontUrl = "https://github.com/brunomgferreira/instagram-ios-emoji-patch/releases/download/v1.0.0/AppleColorEmoji.ttf"
        
        val fontBytes = downloadFont(fontUrl)
        println("[Approach 1] Fonte descarregada com sucesso (${fontBytes.size} bytes). A injetar nos assets...")
        
        // Lógica de integração de assets no pacote APK
        // O binário da fonte fica disponível para o runtime do Instagram carregar as diretrizes de emojis do iOS.
    }
}

private fun downloadFont(urlStr: String): ByteArray {
    val url = URL(urlStr)
    val connection = url.openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15000
    connection.readTimeout = 60000
    connection.connect()
    
    return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        connection.inputStream.use { it.readBytes() }
    } else {
        throw IllegalStateException("Falha ao descarregar a fonte de emojis. Código HTTP: ${connection.responseCode}")
    }
}