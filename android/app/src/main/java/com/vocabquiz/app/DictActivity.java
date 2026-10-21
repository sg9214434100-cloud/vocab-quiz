package com.vocabquiz.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shows a dictionary page (Merriam-Webster, Cambridge, Oxford) with a button that adds the word to My Words. */
public class DictActivity extends Activity {
    static final String EXTRA_QUERY = "q";
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";
    private WebView web;
    private String query;

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        query = getIntent().getStringExtra(EXTRA_QUERY);
        if (query == null) query = "";
        int blue = Color.parseColor("#2D4FD6");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(12), dp(6));
        top.setBackgroundColor(Color.parseColor("#F1F3F6"));
        Button back = new Button(this);
        back.setText("‹ Back");
        back.setAllCaps(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setTextColor(blue);
        back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        String t = getIntent().getStringExtra(EXTRA_TITLE);
        title.setText(t == null ? "Dictionary" : t);
        title.setTextColor(Color.parseColor("#5A6577"));
        title.setTextSize(14);
        top.addView(back);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("http")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
                return true;
            }
        });

        Button add = new Button(this);
        add.setText("＋  Add “" + query + "” to my words");
        add.setAllCaps(false);
        add.setTextColor(Color.WHITE);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setTextSize(16);
        add.setBackgroundColor(blue);
        add.setPadding(dp(16), dp(14), dp(16), dp(14));
        add.setOnClickListener(v -> {
            String q = currentQuery();
            Intent r = new Intent();
            r.putExtra(EXTRA_QUERY, q);
            setResult(RESULT_OK, r);
            finish();
        });

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        if (b != null) web.restoreState(b);
        else {
            String url = getIntent().getStringExtra(EXTRA_URL);
            web.loadUrl(url != null ? url : "https://www.merriam-webster.com/dictionary/" + Uri.encode(query));
        }
    }

    /** The word the person is looking at now, read from the dictionary page's address; else the original search. */
    private String currentQuery() {
        try {
            Uri u = Uri.parse(web.getUrl());
            String host = u.getHost() == null ? "" : u.getHost();
            java.util.List<String> seg = u.getPathSegments();
            String w = null;
            if (host.contains("merriam-webster.com") && seg.size() >= 2 && seg.get(0).equals("dictionary")) w = seg.get(1);
            else if (host.contains("cambridge.org") && seg.size() >= 3 && seg.get(0).equals("dictionary")) w = seg.get(2);
            else if (host.contains("oxfordlearnersdictionaries.com")) {
                if (seg.size() >= 3 && seg.get(0).equals("definition")) w = seg.get(2).replaceAll("_\\d+$", "");
                else w = u.getQueryParameter("q");
            } else if (host.contains("google.")) w = u.getQueryParameter("q");
            if (w != null) {
                w = w.replace('-', ' ').replace('_', ' ').trim();
                if (!w.isEmpty()) return w;
            }
        } catch (Exception ignored) { }
        return query;
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
