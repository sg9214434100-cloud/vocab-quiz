package com.vocabquiz.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import org.json.JSONObject;
import java.util.ArrayList;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int PICK_FILE = 42;
    private static final int VOICE = 43;
    private static final int GOOGLE = 44;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("http")) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "Choose backup file"), PICK_FILE);
                return true;
            }
        });
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    private void callJs(String fn, String arg) {
        String js = "window." + fn + "&&window." + fn + "(" + JSONObject.quote(arg == null ? "" : arg) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == VOICE) {
            String text = "";
            if (res == RESULT_OK && data != null) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) text = r.get(0);
            }
            callJs("onVoiceResult", text);
            return;
        }
        if (req == GOOGLE) {
            if (res == RESULT_OK && data != null) callJs("addFromDict", data.getStringExtra(DictActivity.EXTRA_QUERY));
            return;
        }
        if (req == PICK_FILE && fileCallback != null) {
            Uri[] r = (res == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(r);
            fileCallback = null;
        } else super.onActivityResult(req, res, data);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    class Bridge {
        @JavascriptInterface
        public void openDict(String url, String q, String title) {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, DictActivity.class);
                i.putExtra(DictActivity.EXTRA_URL, url);
                i.putExtra(DictActivity.EXTRA_QUERY, q);
                i.putExtra(DictActivity.EXTRA_TITLE, title);
                startActivityForResult(i, GOOGLE);
            });
        }

        /** Fetches a dictionary API address natively (no browser cross-site limits) and hands the text back to the page. */
        @JavascriptInterface
        public void httpGet(String id, String url) {
            new Thread(() -> {
                int status = 0;
                String body = "";
                try {
                    if (!url.startsWith("https://")) throw new Exception("https only");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "VocabQuiz/1.0 (Android)");
                    status = c.getResponseCode();
                    java.io.InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (in != null) {
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0 && bo.size() < 3_000_000) bo.write(buf, 0, n);
                        in.close();
                        body = bo.toString("UTF-8");
                    }
                    c.disconnect();
                } catch (Exception e) { status = 0; }
                final String js = "window.__httpDone&&window.__httpDone(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(body) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }

        @JavascriptInterface
        public void startVoice() {
            runOnUiThread(() -> {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a word or phrase");
                try { startActivityForResult(i, VOICE); }
                catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Voice search needs the Google app on this phone", Toast.LENGTH_LONG).show();
                    callJs("onVoiceResult", "");
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String name, String text, String mime) {
            try {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    try (FileOutputStream fo = new FileOutputStream(new File(dir, name))) { fo.write(bytes); }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved " + name + " to Downloads", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not save the file", Toast.LENGTH_LONG).show());
            }
        }
    }
}
