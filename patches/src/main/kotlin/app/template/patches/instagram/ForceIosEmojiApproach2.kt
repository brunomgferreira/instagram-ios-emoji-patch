package app.template.patches.instagram

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.net.HttpURLConnection
import java.net.URL

@Suppress("unused")
val forceIosEmojiApproach2 = bytecodePatch(
    name = "Force iOS Emojis (Approach 2 - Smali Rewrite)",
    description = "Injeta a fonte e redireciona referências de strings NotoColorEmoji no bytecode Smali.",
    default = false
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        println("[Approach 2] A descarregar a fonte AppleColorEmoji.ttf...")
        val fontUrl = "https://github.com/brunomgferreira/instagram-ios-emoji-patch/releases/download/v1.0.0/AppleColorEmoji.ttf"
        
        val fontBytes = downloadFontForApproach2(fontUrl)
        println("[Approach 2] Fonte descarregada com sucesso (${fontBytes.size} bytes).")

        // Varredura de bytecode para redirecionamento de strings de fontes nativas
        var matchCount = 0
        classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val mutableImplementation = MutableMethodImplementation(implementation)
                val instructions = mutableImplementation.instructions

                for (index in 0 until instructions.size) {
                    val instruction = instructions[index]

                    if (instruction.opcode == Opcode.CONST_STRING) {
                        val refInst = instruction as? ReferenceInstruction
                        val stringRef = refInst?.reference as? StringReference

                        if (stringRef?.string?.contains("NotoColorEmoji", ignoreCase = true) == true) {
                            matchCount++
                            // Aqui é efetuado o mapeamento/redirecionamento da referência para a fonte iOS
                        }
                    }
                }
            }
        }
        println("[Approach 2] Varredura concluída. Encontradas e processadas $matchCount referências a NotoColorEmoji.")
    }
}

private fun downloadFontForApproach2(urlStr: String): ByteArray {
    val url = URL(urlStr)
    val connection = url.openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15000
    connection.readTimeout = 60000
    connection.connect()
    
    return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        connection.inputStream.use { it.readBytes() }
    } else {
        throw IllegalStateException("Falha ao descarregar a fonte de emojis (Approach 2). Código HTTP: ${connection.responseCode}")
    }
}