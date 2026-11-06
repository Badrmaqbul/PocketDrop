package dev.badr.pocketdrop.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny dependency-free JSON reader/writer (objects, arrays, strings, numbers, booleans, null). */
public final class Json {
    private Json() {}

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.i != text.length()) throw new IllegalArgumentException("Unexpected data at " + p.i);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof String s ? s : null;
    }

    public static String str(Map<String, Object> m, String k, String def) {
        String s = str(m, k);
        return s == null ? def : s;
    }

    public static long lng(Map<String, Object> m, String k, long def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.longValue() : def;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean b ? b : def;
    }

    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o, false, 0);
        return sb.toString();
    }

    public static String pretty(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o, true, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o, boolean pretty, int depth) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            quote(sb, s);
        } else if (o instanceof Boolean || o instanceof Integer || o instanceof Long || o instanceof Short) {
            sb.append(o);
        } else if (o instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(d);
        } else if (o instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                if (pretty) indent(sb, depth + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                write(sb, e.getValue(), pretty, depth + 1);
            }
            if (pretty) indent(sb, depth);
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            boolean any = false;
            StringBuilder inner = new StringBuilder();
            for (Object x : it) {
                if (any) inner.append(',');
                any = true;
                if (pretty) indent(inner, depth + 1);
                write(inner, x, pretty, depth + 1);
            }
            if (!any) { sb.append("[]"); return; }
            sb.append('[').append(inner);
            if (pretty) indent(sb, depth);
            sb.append(']');
        } else {
            quote(sb, o.toString());
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append('\n');
        for (int i = 0; i < depth; i++) sb.append("  ");
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        final String s;
        int i;
        int depth;

        Parser(String s) { this.s = s; }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return number();
            }
        }

        void expect(String lit) {
            if (!s.startsWith(lit, i)) throw new IllegalArgumentException("Bad literal at " + i);
            i += lit.length();
        }

        void enter() {
            if (++depth > 64) throw new IllegalArgumentException("Too deeply nested");
        }

        Map<String, Object> object() {
            enter();
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') { i++; depth--; return m; }
            while (true) {
                skipWs();
                if (i >= s.length() || s.charAt(i) != '"') throw new IllegalArgumentException("Key expected at " + i);
                String k = string();
                skipWs();
                if (i >= s.length() || s.charAt(i) != ':') throw new IllegalArgumentException("':' expected at " + i);
                i++;
                skipWs();
                m.put(k, value());
                skipWs();
                if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
                char c = s.charAt(i++);
                if (c == '}') { depth--; return m; }
                if (c != ',') throw new IllegalArgumentException("',' expected at " + (i - 1));
            }
        }

        List<Object> array() {
            enter();
            List<Object> l = new ArrayList<>();
            i++;
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') { i++; depth--; return l; }
            while (true) {
                skipWs();
                l.add(value());
                skipWs();
                if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
                char c = s.charAt(i++);
                if (c == ']') { depth--; return l; }
                if (c != ',') throw new IllegalArgumentException("',' expected at " + (i - 1));
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                if (i >= s.length()) throw new IllegalArgumentException("Unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) throw new IllegalArgumentException("Bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw new IllegalArgumentException("Bad escape \\" + e);
                }
            }
        }

        Object number() {
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            boolean isDouble = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') { i++; }
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { isDouble = true; i++; }
                else break;
            }
            String t = s.substring(start, i);
            if (t.isEmpty()) throw new IllegalArgumentException("Unexpected character at " + start);
            try {
                if (!isDouble) return Long.parseLong(t);
                return Double.parseDouble(t);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Bad number '" + t + "'");
            }
        }
    }
}
