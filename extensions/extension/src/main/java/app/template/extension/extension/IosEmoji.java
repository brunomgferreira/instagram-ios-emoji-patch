package app.template.extension.extension;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Renders emojis with the iOS emoji font bundled in the APK as {@code assets/fonts/ios_emoji.ttf}.
 *
 * Two parts, as in InstaEclipse's custom emoji:
 * <ul>
 *   <li>EmojiCompat is made to fail loading, so it stops replacing emojis with spans of its own font.
 *       Emojis then go through normal text layout.</li>
 *   <li>Every typeface the app assigns is swapped for one that has the iOS emoji font as a fallback
 *       family behind the system text font, so emoji code points resolve to it.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class IosEmoji {
    private static final String ASSET_PATH = "fonts/ios_emoji.ttf";
    private static final String CACHE_FILE = "ios_emoji.ttf";

    private static volatile Typeface base;
    private static volatile boolean unavailable;

    private IosEmoji() {
    }

    /**
     * Called wherever the app assigns a typeface; null means the default one. The base typeface only
     * exists on API 29+, so the API 28 calls below are never reached on older devices.
     */
    @SuppressLint("NewApi")
    public static Typeface apply(Typeface typeface) {
        Typeface emojiBase = getBase();
        if (emojiBase == null) return typeface;
        if (typeface == null) return emojiBase;
        return Typeface.create(emojiBase, weightOf(typeface), typeface.isItalic());
    }

    private static Typeface getBase() {
        Typeface cached = base;
        if (cached != null || unavailable) return cached;

        synchronized (IosEmoji.class) {
            if (base != null || unavailable) return base;

            // Needs FontFamily/CustomFallbackBuilder (API 29), so older devices keep the app's fonts.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                unavailable = true;
                return null;
            }

            Context context = currentApplication();
            // Too early to have an application, try again on the next call.
            if (context == null) return null;

            try {
                File emojiFile = extractFont(context);
                FontFamily text = new FontFamily.Builder(
                        new Font.Builder(systemTextFont()).build()).build();
                FontFamily emoji = new FontFamily.Builder(new Font.Builder(emojiFile).build()).build();

                base = new Typeface.CustomFallbackBuilder(text)
                        .addCustomFallback(emoji)
                        .setSystemFallback("sans-serif")
                        .build();
            } catch (Throwable t) {
                unavailable = true;
            }
            return base;
        }
    }

    /** Copies the font out of the APK once, since fonts load from a file. */
    private static File extractFont(Context context) throws Exception {
        File out = new File(context.getCacheDir(), CACHE_FILE);
        if (out.length() > 0) return out;

        File tmp = new File(context.getCacheDir(), CACHE_FILE + ".tmp");
        try (InputStream in = context.getAssets().open(ASSET_PATH);
             FileOutputStream fos = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) fos.write(buffer, 0, read);
        }
        if (!tmp.renameTo(out)) throw new IllegalStateException("Could not move emoji font into place");
        return out;
    }

    private static File systemTextFont() {
        for (String path : new String[]{
                "/system/fonts/Roboto-Regular.ttf",
                "/system/fonts/NotoSans-Regular.ttf",
                "/system/fonts/DroidSans.ttf"}) {
            File file = new File(path);
            if (file.exists()) return file;
        }
        return new File("/system/fonts/Roboto-Regular.ttf");
    }

    private static Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            return (Context) activityThread.getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Typeface.create only accepts 1..1000, getWeight() can report 0. */
    @SuppressLint("NewApi")
    private static int weightOf(Typeface typeface) {
        int weight = typeface.getWeight();
        return weight < 1 || weight > 1000 ? 400 : weight;
    }

    /**
     * Called at the start of the EmojiCompat font loader's {@code run()}. Reports a failed load to the
     * loader's callback, which leaves EmojiCompat in its failed state where it processes nothing.
     *
     * @return true if the load was failed, so the caller must skip the real font fetch.
     */
    public static boolean disableEmojiCompat(Object loader) {
        try {
            return failLoad(loader, 0);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Looks for the loader callback reachable from the loader (loader -> callback, or one object
     * further): an abstract class with a void method taking a Throwable, which is onFailed. The
     * names are obfuscated, so it is found by shape.
     */
    private static boolean failLoad(Object object, int depth) throws Exception {
        if (object == null || depth > 2) return false;

        for (Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                Class<?> type = field.getType();
                if (Modifier.isStatic(field.getModifiers()) || type.isPrimitive() || isPlatform(type)) continue;

                field.setAccessible(true);
                Object value = field.get(object);
                if (value == null) continue;

                if (Modifier.isAbstract(type.getModifiers()) && !type.isInterface()) {
                    for (Method method : type.getDeclaredMethods()) {
                        Class<?>[] parameters = method.getParameterTypes();
                        if (method.getReturnType() == void.class
                                && parameters.length == 1 && parameters[0] == Throwable.class) {
                            method.setAccessible(true);
                            method.invoke(value, new Exception("iOS emoji: EmojiCompat disabled"));
                            return true;
                        }
                    }
                }
                if (failLoad(value, depth + 1)) return true;
            }
        }
        return false;
    }

    private static boolean isPlatform(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("android.")
                || name.startsWith("kotlin.") || name.startsWith("dalvik.");
    }
}
