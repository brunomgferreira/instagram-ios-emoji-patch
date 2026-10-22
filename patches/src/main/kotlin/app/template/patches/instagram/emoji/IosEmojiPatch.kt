package app.template.patches.instagram.emoji

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.toInstruction
import app.template.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.net.HttpURLConnection
import java.net.URL

private const val FONT_URL =
    "https://github.com/brunomgferreira/instagram-ios-emoji-patch/releases/download/v1.0.0/AppleColorEmoji.ttf"

private const val FONT_ASSET = "assets/fonts/ios_emoji.ttf"

private const val EXTENSION_CLASS = "Lapp/template/extension/extension/IosEmoji;"
private const val APPLY = "$EXTENSION_CLASS->apply(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;"
private const val DISABLE_EMOJI_COMPAT = "$EXTENSION_CLASS->disableEmojiCompat(Ljava/lang/Object;)Z"

private const val TYPEFACE = "Landroid/graphics/Typeface;"

/** Direct subclasses of these get their typeface assignment overridden. */
private val TEXT_WIDGETS = listOf(
    "TextView", "EditText", "Button", "RadioButton", "CheckBox", "CheckedTextView",
    "AutoCompleteTextView", "MultiAutoCompleteTextView", "Switch", "ToggleButton",
).map { "Landroid/widget/$it;" }.toSet()

private val PAINT_CLASSES = setOf("Landroid/graphics/Paint;", "Landroid/text/TextPaint;")

private val addIosEmojiFontPatch = rawResourcePatch {
    execute {
        val connection = URL(FONT_URL).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000

        val font = try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw PatchException("Failed to download the iOS emoji font from $FONT_URL (${connection.responseCode})")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }

        // Nothing to decode from the original APK, the file only gets added.
        get(FONT_ASSET, copy = false).apply { parentFile.mkdirs() }.writeBytes(font)
    }
}

@Suppress("unused")
val iosEmojiPatch = bytecodePatch(
    name = "iOS emojis",
    description = "Replaces Instagram's emojis with the iOS ones.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    dependsOn(addIosEmojiFontPatch)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Stop EmojiCompat, so emojis are no longer drawn with its own font.
        EmojiCompatLoaderFingerprint.method.apply {
            val locals = implementation!!.registerCount - parameters.size - 1
            if (locals < 1) throw PatchException("$definingClass->$name has no free register")

            addInstructionsWithLabels(
                0,
                """
                    invoke-static/range {p0 .. p0}, $DISABLE_EMOJI_COMPAT
                    move-result v0
                    if-eqz v0, :load
                    return-void
                    :load
                    nop
                """,
            )
        }

        // 2. Give every typeface the app assigns the iOS emoji font as a fallback. Collected in one pass
        // and edited after, so classes are not rewritten while they are read.
        val textViewClasses = mutableListOf<String>()
        val paintTypefaceCallers = mutableListOf<Method>()

        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/template/extension/")) return@classDefForEach

            if (classDef.superclass in TEXT_WIDGETS) textViewClasses += classDef.type

            classDef.methods.forEach { method ->
                if (method.implementation?.instructions?.any { it.isPaintTypefaceCall() } == true) {
                    paintTypefaceCallers += method
                }
            }
        }

        textViewClasses.forEach { mutableClassDefBy(it).overrideTypefaceAssignment() }
        paintTypefaceCallers.forEach { caller ->
            mutableClassDefBy(caller.definingClass).methods.first {
                it.name == caller.name &&
                    it.returnType == caller.returnType &&
                    it.parameterTypes.map(CharSequence::toString) == caller.parameterTypes.map(CharSequence::toString)
            }.hookPaintTypefaceCalls()
        }
    }
}

/**
 * Overrides `setTypeface(Typeface)`, which every way a view gets its font ends in: XML,
 * `setTextAppearance`, `setTypeface(null)` or a resolved typeface.
 */
private fun MutableClass.overrideTypefaceAssignment() {
    val existing = methods.firstOrNull {
        it.name == "setTypeface" && it.returnType == "V" &&
            it.parameterTypes.map(CharSequence::toString) == listOf(TYPEFACE)
    }
    if (existing != null) {
        if (existing.implementation != null) {
            existing.addInstructions(
                0,
                """
                    invoke-static/range {p1 .. p1}, $APPLY
                    move-result-object p1
                """,
            )
        }
        return
    }

    // Registers: v0 is this, v1 the typeface.
    val implementation = MethodImplementationBuilder(2).apply {
        addInstruction("invoke-static {v1}, $APPLY".toInstruction())
        addInstruction("move-result-object v1".toInstruction())
        addInstruction("invoke-super {v0, v1}, $superclass->setTypeface($TYPEFACE)V".toInstruction())
        addInstruction("return-void".toInstruction())
    }.methodImplementation

    methods.add(
        MutableMethod(
            ImmutableMethod(
                type, "setTypeface", listOf(ImmutableMethodParameter(TYPEFACE, null, null)),
                "V", AccessFlags.PUBLIC.value, emptySet(), emptySet(), implementation,
            ),
        ),
    )
}

/** A `Paint.setTypeface` call, where custom drawn text (Litho, spans) sets its font. */
private fun Instruction.isPaintTypefaceCall() =
    (opcode == Opcode.INVOKE_VIRTUAL || opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
        getReference<MethodReference>()?.let { reference ->
            reference.name == "setTypeface" &&
                reference.definingClass in PAINT_CLASSES &&
                reference.parameterTypes.map(CharSequence::toString) == listOf(TYPEFACE)
        } == true

/** Passes the typeface of every `Paint.setTypeface` call the method makes through the extension. */
private fun MutableMethod.hookPaintTypefaceCalls() {
    // Last index first, so earlier indices stay valid while instructions are inserted.
    implementation!!.instructions
        .withIndex()
        .filter { it.value.isPaintTypefaceCall() }
        .map { it.index }
        .sortedDescending()
        .forEach { callIndex ->
            // The paint is the first register of the call, the typeface the second.
            val register = getInstruction(callIndex).registersUsed[1]
            addInstructions(
                callIndex,
                """
                    invoke-static/range {v$register .. v$register}, $APPLY
                    move-result-object v$register
                """,
            )
        }
}
