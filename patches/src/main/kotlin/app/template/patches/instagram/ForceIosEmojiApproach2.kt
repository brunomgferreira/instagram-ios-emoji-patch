package app.template.patches.instagram

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

@Suppress("unused")
val forceIosEmojiApproach2 = bytecodePatch(
    name = "Force iOS Emojis (Approach 2 - Smali Rewrite)",
    description = "Downloads/embeds asset AND rewrites NotoColorEmoji string constants to load the custom font.",
    default = false
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute { context ->
        println("[Approach 2] Downloading font from GitHub release...")
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

        // 1. Inject asset
        context.addAsset("fonts/ios_emoji.ttf", ByteArrayInputStream(fontBytes))
        println("[Approach 2] Embedded font into assets/fonts/")

        // 2. Perform Smali string constant rewriting for NotoColorEmoji references
        var matchCount = 0
        context.classDefSequence.forEach { classDef ->
            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val mutableImplementation = MutableMethodImplementation(implementation)
                val instructions = mutableImplementation.instructions

                for (i in instructions.indices) {
                    val instruction = instructions[i]

                    if (instruction.opcode == Opcode.CONST_STRING) {
                        val refInst = instruction as? ReferenceInstruction
                        val stringRef = refInst?.reference as? StringReference

                        if (stringRef?.string?.contains("NotoColorEmoji") == true) {
                            mutableImplementation.replaceInstruction(
                                i,
                                BuilderInstruction21c(
                                    Opcode.CONST_STRING,
                                    0,
                                    context.dexFile.getFromString("fonts/ios_emoji.ttf")
                                )
                            )
                            matchCount++
                        }
                    }
                }
            }
        }
        println("[Approach 2] Successfully redirected $matchCount string references to custom emoji font.")
    }
}