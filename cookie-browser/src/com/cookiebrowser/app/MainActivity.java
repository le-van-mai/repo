package com.cookiebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Trình duyệt WebView tối giản cho phép người dùng nạp (import) cookie của
 * CHÍNH tài khoản mình để chuyển phiên đăng nhập từ máy tính sang điện thoại.
 */
public class MainActivity extends Activity {

    private WebView web;
    private EditText urlBar;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private boolean handledByIntent = false;

    private static final String PREF_AUTO_URI = "auto_uri";
    private static final String PREF_AUTO_HASH = "auto_hash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("cb", MODE_PRIVATE);
        setContentView(buildUi());
        setupWebView();
        if (handleIntent(getIntent())) {
            handledByIntent = true;
        } else {
            String last = prefs.getString("last_url", "");
            if (!last.isEmpty()) web.loadUrl(last);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Moi lan mo app, tu doc lai file cookie da ghi nho; chi nap khi noi dung thay doi.
        autoLoadFromRememberedFile();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setHint("Nhap URL hoac tu khoa");
        urlBar.setTextSize(14);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlBar.setSelectAllOnFocus(true);
        urlBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                go(urlBar.getText().toString());
                return true;
            }
        });
        bar.addView(urlBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bar.addView(iconButton("⟳", new View.OnClickListener() {
            @Override public void onClick(View v) { web.reload(); }
        }));

        Button cookieBtn = iconButton("Cookie", new View.OnClickListener() {
            @Override public void onClick(View v) { showCookieDialog(); }
        });
        // Nhan giu nut Cookie = chon file cookie.txt de tu dong nap moi lan mo app.
        cookieBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showAutoFileDialog(); return true; }
        });
        bar.addView(cookieBtn);

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private Button iconButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(l);
        return b;
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                urlBar.setText(url);
                prefs.edit().putString("last_url", url).apply();
                CookieManager.getInstance().flush();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void go(String text) {
        text = text.trim();
        if (text.isEmpty()) return;
        String url;
        if (text.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            url = text;
        } else if (text.contains(".") && !text.contains(" ")) {
            url = "https://" + text;
        } else {
            try {
                url = "https://www.google.com/search?q=" + Uri.encode(text);
            } catch (Exception e) {
                url = "https://www.google.com";
            }
        }
        hideKeyboard();
        web.loadUrl(url);
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    private String readClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (t != null) return t.toString();
        }
        return "";
    }

    // ----- Giao dien import cookie -----

    private void showCookieDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText input = new EditText(this);
        input.setHint("Dan cookie: JSON (Cookie-Editor), cookies.txt, hoac \"a=1; b=2\"");
        input.setMinLines(5);
        input.setGravity(Gravity.TOP);
        input.setTextSize(12);
        String clip = readClipboard();
        if (clip.startsWith("[") || clip.startsWith("{") || clip.contains("\t") || clip.contains("=")) {
            input.setText(clip);
        }
        box.addView(input);

        final CheckBox reload = new CheckBox(this);
        reload.setText("Tai lai trang sau khi nap");
        reload.setChecked(true);
        box.addView(reload);

        final CheckBox openHost = new CheckBox(this);
        openHost.setText("Mo trang cua cookie sau khi nap");
        box.addView(openHost);

        new AlertDialog.Builder(this)
                .setTitle("Import cookie")
                .setView(box)
                .setPositiveButton("Nap", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        importCookies(input.getText().toString(), reload.isChecked(), openHost.isChecked());
                    }
                })
                .setNeutralButton("Chon file", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        pickFile();
                    }
                })
                .setNegativeButton("Huy", null)
                .show();
    }

    private static final int REQ_FILE = 11;

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Chon file cookie"), REQ_FILE);
        } catch (Exception e) {
            toast("Khong mo duoc trinh chon file");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && res == RESULT_OK && data != null && data.getData() != null) {
            String content = readUri(data.getData());
            if (content.isEmpty()) toast("File rong hoac khong doc duoc");
            else importCookies(content, true, true);
        } else if (req == REQ_AUTO_FILE && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception e) {
                toast("Khong giu duoc quyen doc file lau dai: " + e.getMessage());
                return;
            }
            prefs.edit().putString(PREF_AUTO_URI, uri.toString()).remove(PREF_AUTO_HASH).apply();
            toast("Da ghi nho file. App se tu nap moi lan mo.");
            autoLoadFromRememberedFile();
        }
    }

    private String readUri(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }

    private String currentHost() {
        try {
            String u = web.getUrl();
            if (u != null) return Uri.parse(u).getHost();
        } catch (Exception ignored) {}
        return null;
    }

    private void importCookies(String raw, boolean reload, boolean openHost) {
        if (raw == null || raw.trim().isEmpty()) {
            toast("Chua co du lieu cookie");
            return;
        }
        List<CookieParser.Cookie> cookies;
        try {
            cookies = CookieParser.parse(raw, currentHost());
        } catch (Exception e) {
            toast("Loi doc cookie: " + e.getMessage());
            return;
        }
        if (cookies.isEmpty()) {
            toast("Khong tim thay cookie hop le");
            return;
        }
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        int ok = 0;
        String firstUrl = null;
        for (CookieParser.Cookie c : cookies) {
            try {
                cm.setCookie(c.url(), c.toSetCookie(true));
                if (firstUrl == null) firstUrl = c.url();
                ok++;
            } catch (Exception ignored) {}
        }
        cm.flush();
        final int done = ok;
        toast("Da nap " + done + "/" + cookies.size() + " cookie");
        if (openHost && firstUrl != null) {
            web.loadUrl(firstUrl);
        } else if (reload && web.getUrl() != null) {
            web.reload();
        }
    }

    // ----- Tu dong nap tu file cookie.txt da ghi nho -----

    private static final int REQ_AUTO_FILE = 12;

    private void showAutoFileDialog() {
        String uri = prefs.getString(PREF_AUTO_URI, "");
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("File cookie tu dong");
        if (uri.isEmpty()) {
            b.setMessage("Chon mot file cookie.txt (hoac .json). App se tu nap lai moi lan mo, "
                    + "va tu cap nhat khi ban ghi de file do bang cookie moi tu may tinh.");
            b.setPositiveButton("Chon file", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) { pickAutoFile(); }
            });
        } else {
            String name = Uri.parse(uri).getLastPathSegment();
            b.setMessage("Dang ghi nho:\n" + name + "\n\nMoi lan mo app se tu nap lai file nay neu noi dung thay doi.");
            b.setPositiveButton("Nap ngay", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    prefs.edit().remove(PREF_AUTO_HASH).apply(); // buoc nap lai du noi dung chua doi
                    autoLoadFromRememberedFile();
                }
            });
            b.setNeutralButton("Doi file", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) { pickAutoFile(); }
            });
            b.setNegativeButton("Bo ghi nho", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    forgetAutoFile(uri);
                    toast("Da bo ghi nho file tu dong");
                }
            });
        }
        b.show();
    }

    private void pickAutoFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_AUTO_FILE);
        } catch (Exception e) {
            toast("Khong mo duoc trinh chon file");
        }
    }

    private void forgetAutoFile(String uri) {
        try {
            getContentResolver().releasePersistableUriPermission(Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        prefs.edit().remove(PREF_AUTO_URI).remove(PREF_AUTO_HASH).apply();
    }

    private void autoLoadFromRememberedFile() {
        String uriStr = prefs.getString(PREF_AUTO_URI, "");
        if (uriStr.isEmpty()) return;
        String content = readUri(Uri.parse(uriStr));
        if (content.isEmpty()) return; // file tam thoi khong doc duoc, bo qua
        int hash = content.hashCode();
        int last = prefs.getInt(PREF_AUTO_HASH, 0);
        if (hash == last) return; // noi dung chua doi -> khong nap lai
        prefs.edit().putInt(PREF_AUTO_HASH, hash).apply();
        importCookies(content, true, web.getUrl() == null || web.getUrl().isEmpty());
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.isEmpty()) {
                importCookies(text, true, true);
                return true;
            }
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) {
                String content = readUri(stream);
                if (!content.isEmpty()) {
                    importCookies(content, true, true);
                    return true;
                }
            }
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            Uri data = intent.getData();
            String scheme = data.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                web.loadUrl(data.toString());
                return true;
            }
            String content = readUri(data);
            if (!content.isEmpty()) {
                importCookies(content, true, true);
                return true;
            }
        }
        return false;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
