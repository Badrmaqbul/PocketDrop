package dev.badr.pocketdrop.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

/** Arabic / English texts. Language is chosen inside the app only. */
public final class I18n {
    private static volatile Properties current = new Properties();
    private static volatile Properties fallback = load("en");
    private static volatile String lang = "en";

    private I18n() {}

    public static void setLanguage(String code) {
        String c = "ar".equals(code) ? "ar" : "en";
        lang = c;
        current = c.equals("en") ? fallback : load(c);
    }

    public static String lang() { return lang; }

    public static boolean rtl() { return "ar".equals(lang); }

    /** Language of the device: "ar", "en", or "other" (the first screen is then shown in English). */
    public static String deviceLanguage() {
        String l = Locale.getDefault().getLanguage();
        if ("ar".equals(l)) return "ar";
        if ("en".equals(l)) return "en";
        return "other";
    }

    public static String t(String key, Object... args) {
        String s = current.getProperty(key);
        if (s == null) s = fallback.getProperty(key);
        if (s == null) return key;
        if (args.length == 0) return s;
        Object[] strs = new Object[args.length];
        for (int i = 0; i < args.length; i++) strs[i] = String.valueOf(args[i]);
        return new MessageFormat(s, Locale.ROOT).format(strs);
    }

    public static Properties bundle(String code) { return load(code); }

    private static Properties load(String code) {
        Properties p = new Properties();
        try (InputStream in = I18n.class.getResourceAsStream("/i18n/" + code + ".properties")) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
        } catch (IOException ignored) { }
        return p;
    }
}
