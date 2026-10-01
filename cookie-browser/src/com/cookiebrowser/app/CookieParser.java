package com.cookiebrowser.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Đọc cookie từ các định dạng phổ biến:
 *  - JSON mảng (Cookie-Editor, EditThisCookie, iCookie, Puppeteer)
 *  - JSON object {"cookies":[...]} (Playwright storageState)
 *  - Netscape cookies.txt
 *  - Chuỗi header "a=1; b=2" (cần domain mặc định)
 */
public class CookieParser {

    public static class Cookie {
        public String name, value = "", domain, path = "/", sameSite = "";
        public boolean secure, httpOnly, hostOnly;
        public double expiry; // giây Unix, <= 0 là cookie phiên

        public String host() {
            return domain.startsWith(".") ? domain.substring(1) : domain;
        }

        public String url() {
            return "https://" + host() + "/";
        }

        public String toSetCookie(boolean keepSession) {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append('=').append(value);
            if (!hostOnly) sb.append("; Domain=").append(domain);
            sb.append("; Path=").append(path == null || path.isEmpty() ? "/" : path);
            if (expiry > 0) {
                SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("GMT"));
                sb.append("; Expires=").append(f.format(new Date((long) (expiry * 1000))));
            } else if (keepSession) {
                sb.append("; Max-Age=31536000");
            }
            String ss = sameSite == null ? "" : sameSite.toLowerCase(Locale.US);
            boolean ssNone = ss.equals("none") || ss.equals("no_restriction");
            if (secure || ssNone) sb.append("; Secure");
            if (httpOnly) sb.append("; HttpOnly");
            if (ssNone) sb.append("; SameSite=None");
            else if (ss.equals("lax")) sb.append("; SameSite=Lax");
            else if (ss.equals("strict")) sb.append("; SameSite=Strict");
            return sb.toString();
        }
    }

    /** @param defaultHost domain dùng cho chuỗi header kiểu "a=1; b=2" (có thể null) */
    public static List<Cookie> parse(String raw, String defaultHost) throws Exception {
        raw = raw.trim();
        if (raw.startsWith("﻿")) raw = raw.substring(1).trim();
        if (raw.startsWith("[") || raw.startsWith("{")) return parseJson(raw);
        if (raw.contains("\t")) return parseNetscape(raw);
        return parseHeader(raw, defaultHost);
    }

    private static List<Cookie> parseJson(String raw) throws Exception {
        JSONArray arr;
        if (raw.startsWith("{")) {
            JSONObject o = new JSONObject(raw);
            arr = o.optJSONArray("cookies");
            if (arr == null) {
                arr = new JSONArray();
                arr.put(o);
            }
        } else {
            arr = new JSONArray(raw);
        }
        List<Cookie> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !o.has("name")) continue;
            Cookie c = new Cookie();
            c.name = o.getString("name");
            c.value = o.optString("value", "");
            c.domain = o.optString("domain", o.optString("host", ""));
            if (c.domain.isEmpty()) continue;
            c.path = o.optString("path", "/");
            c.secure = o.optBoolean("secure", false);
            c.httpOnly = o.optBoolean("httpOnly", o.optBoolean("httponly", false));
            if (o.has("hostOnly")) c.hostOnly = o.optBoolean("hostOnly");
            else c.hostOnly = !c.domain.startsWith(".");
            if (c.hostOnly && c.domain.startsWith(".")) c.domain = c.domain.substring(1);
            c.expiry = o.optDouble("expirationDate", o.optDouble("expires", o.optDouble("expiry", -1)));
            if (Double.isNaN(c.expiry)) c.expiry = -1;
            if (c.expiry > 1e11) c.expiry /= 1000; // mili-giây -> giây
            if (o.optBoolean("session", false)) c.expiry = -1;
            c.sameSite = o.optString("sameSite", "");
            if (c.name.startsWith("__Host-")) { c.hostOnly = true; c.path = "/"; c.secure = true; c.domain = c.host(); }
            out.add(c);
        }
        return out;
    }

    private static List<Cookie> parseNetscape(String raw) {
        List<Cookie> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            boolean httpOnly = false;
            if (line.startsWith("#HttpOnly_")) {
                httpOnly = true;
                line = line.substring("#HttpOnly_".length());
            }
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t", -1);
            if (p.length < 7) continue;
            Cookie c = new Cookie();
            c.domain = p[0].trim();
            c.hostOnly = !p[1].trim().equalsIgnoreCase("TRUE");
            if (c.hostOnly && c.domain.startsWith(".")) c.domain = c.domain.substring(1);
            if (!c.hostOnly && !c.domain.startsWith(".")) c.domain = "." + c.domain;
            c.path = p[2];
            c.secure = p[3].trim().equalsIgnoreCase("TRUE");
            try { c.expiry = Double.parseDouble(p[4].trim()); } catch (NumberFormatException e) { c.expiry = -1; }
            c.name = p[5];
            c.value = p[6].trim();
            c.httpOnly = httpOnly;
            out.add(c);
        }
        return out;
    }

    private static List<Cookie> parseHeader(String raw, String defaultHost) throws Exception {
        if (raw.regionMatches(true, 0, "cookie:", 0, 7)) raw = raw.substring(7);
        if (defaultHost == null || defaultHost.isEmpty())
            throw new Exception("Chuỗi dạng \"a=1; b=2\" cần nhập domain/URL trước");
        String dom = defaultHost.startsWith("www.") ? defaultHost.substring(3) : "." + defaultHost;
        List<Cookie> out = new ArrayList<>();
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            Cookie c = new Cookie();
            c.name = part.substring(0, eq).trim();
            c.value = part.substring(eq + 1).trim();
            c.domain = dom;
            c.secure = true;
            if (c.name.startsWith("__Host-")) { c.hostOnly = true; c.domain = defaultHost; }
            out.add(c);
        }
        return out;
    }
}
