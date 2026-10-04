package com.supergo.browser;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

/** SUPERGO for Android - black, Safari-style glass bar, Google search. */
public class MainActivity extends Activity {
  static final String SEARCH = "https://www.google.com/search?q=";
  static final String NEWTAB = "https://supergo.newtab/";
  static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
  static final int BLUE = 0xFF0A84FF, ICON = 0xFFE5E5EA;

  static class Tab { WebView w; boolean priv; String title = "New tab"; }
  final List<Tab> tabs = new ArrayList<>();
  int cur = -1;
  volatile Set<String> blocked = new HashSet<>();
  boolean adblock, desktop, loading;
  SharedPreferences sp;
  FrameLayout content;
  View progress;
  TextView hostTv;
  Ic backI, fwdI, reloadI, tabsI;
  View customView; WebChromeClient.CustomViewCallback customCb;

  int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

  // ---------- glass look ----------
  static class Glass extends Drawable {
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    final float r, sw; final int top, bot;
    Glass(float r, float sw, int top, int bot) { this.r = r; this.sw = sw; this.top = top; this.bot = bot; edge.setStyle(Paint.Style.STROKE); shine.setStyle(Paint.Style.STROKE); }
    @Override public void draw(Canvas c) {
      RectF b = new RectF(getBounds());
      fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bot, Shader.TileMode.CLAMP));
      c.drawRoundRect(b, r, r, fill);
      RectF e = new RectF(b); e.inset(sw / 2, sw / 2);
      edge.setStrokeWidth(sw);
      edge.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, new int[]{0xD0FFFFFF, 0x12FFFFFF, 0x12FFFFFF, 0x70FFFFFF}, new float[]{0, .38f, .62f, 1}, Shader.TileMode.CLAMP));
      c.drawRoundRect(e, r, r, edge);
      RectF i = new RectF(b); i.inset(sw * 2.2f, sw * 2.2f);
      shine.setStrokeWidth(sw);
      shine.setShader(new LinearGradient(0, b.top, 0, b.bottom, new int[]{0x45FFFFFF, 0x00FFFFFF}, new float[]{0, .45f}, Shader.TileMode.CLAMP));
      c.drawRoundRect(i, Math.max(0, r - sw * 2.2f), Math.max(0, r - sw * 2.2f), shine);
    }
    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }

  // ---------- vector icons drawn in code (SF-symbol style) ----------
  static class Ic extends View {
    static final int BACK = 0, FWD = 1, SHARE = 2, BOOK = 3, TABS = 4, MORE = 5, RELOAD = 6, STOP = 7;
    int type, count;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), f = new Paint(Paint.ANTI_ALIAS_FLAG);
    Ic(Context c, int t) {
      super(c); type = t; setClickable(true);
      p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(ICON);
      f.setColor(ICON); f.setTextAlign(Paint.Align.CENTER); f.setTypeface(Typeface.DEFAULT_BOLD);
    }
    void set(int t) { if (type != t) { type = t; invalidate(); } }
    @Override protected void onDraw(Canvas c) {
      float s = Math.min(getWidth(), getHeight()) * (type == RELOAD || type == STOP ? .40f : .50f);
      c.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f); c.scale(s, s);
      p.setStrokeWidth(type == BACK || type == FWD ? .11f : .085f);
      Path a = new Path();
      switch (type) {
        case BACK: a.moveTo(.68f, .08f); a.lineTo(.26f, .5f); a.lineTo(.68f, .92f); c.drawPath(a, p); break;
        case FWD: a.moveTo(.32f, .08f); a.lineTo(.74f, .5f); a.lineTo(.32f, .92f); c.drawPath(a, p); break;
        case SHARE:
          a.moveTo(.5f, .62f); a.lineTo(.5f, .05f); a.moveTo(.3f, .24f); a.lineTo(.5f, .05f); a.lineTo(.7f, .24f);
          a.moveTo(.34f, .36f); a.lineTo(.18f, .36f); a.lineTo(.18f, .94f); a.lineTo(.82f, .94f); a.lineTo(.82f, .36f); a.lineTo(.66f, .36f);
          c.drawPath(a, p); break;
        case BOOK:
          a.moveTo(.5f, .22f); a.lineTo(.5f, .86f);
          a.moveTo(.5f, .22f); a.quadTo(.28f, .08f, .04f, .2f); a.lineTo(.04f, .78f); a.quadTo(.28f, .66f, .5f, .86f);
          a.moveTo(.5f, .22f); a.quadTo(.72f, .08f, .96f, .2f); a.lineTo(.96f, .78f); a.quadTo(.72f, .66f, .5f, .86f);
          c.drawPath(a, p); break;
        case TABS:
          a.moveTo(.28f, .26f); a.lineTo(.28f, .08f); a.lineTo(.94f, .08f); a.lineTo(.94f, .72f); a.lineTo(.76f, .72f);
          c.drawPath(a, p); c.drawRoundRect(new RectF(.06f, .26f, .76f, .94f), .14f, .14f, p);
          if (count > 0) { f.setTextSize(.36f); c.drawText(String.valueOf(count), .41f, .72f, f); }
          break;
        case MORE:
          for (float x : new float[]{.1f, .5f, .9f}) c.drawCircle(x, .5f, .095f, f); break;
        case RELOAD:
          c.drawArc(new RectF(.06f, .06f, .94f, .94f), -40, 285, false, p);
          double ang = Math.toRadians(245), ex = .5 + .44 * Math.cos(ang), ey = .5 + .44 * Math.sin(ang);
          double tx = -Math.sin(ang), ty = Math.cos(ang);
          for (int sgn = -1; sgn <= 1; sgn += 2) {
            double r = Math.toRadians(150 * sgn), cx = tx * Math.cos(r) - ty * Math.sin(r), cy = tx * Math.sin(r) + ty * Math.cos(r);
            c.drawLine((float) ex, (float) ey, (float) (ex + cx * .3), (float) (ey + cy * .3), p);
          } break;
        case STOP: c.drawLine(.08f, .08f, .92f, .92f, p); c.drawLine(.92f, .08f, .08f, .92f, p); break;
      }
    }
  }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    sp = getSharedPreferences("sg", 0);
    adblock = sp.getBoolean("ab", true);
    getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
    new Thread(() -> {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("hosts.txt")))) {
        Set<String> s = new HashSet<>(); String l;
        while ((l = r.readLine()) != null) s.add(l);
        blocked = s;
      } catch (Exception ignored) {}
    }).start();

    FrameLayout root = new FrameLayout(this);
    root.setFitsSystemWindows(true);
    root.setBackgroundColor(Color.BLACK);
    content = new FrameLayout(this);
    root.addView(content, new FrameLayout.LayoutParams(-1, -1));
    progress = new View(this); progress.setBackgroundColor(BLUE); progress.setPivotX(0); progress.setScaleX(0);
    root.addView(progress, new FrameLayout.LayoutParams(-1, dp(2)));

    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(8), dp(8), dp(8), dp(2));
    panel.setBackground(new Glass(dp(32), dp(1) * .9f, 0xCC1A1A1D, 0xE60B0B0D));
    FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    pl.setMargins(dp(10), 0, dp(10), dp(8));
    root.addView(panel, pl);

    LinearLayout pill = new LinearLayout(this);
    pill.setGravity(Gravity.CENTER_VERTICAL);
    pill.setBackground(new Glass(dp(24), dp(1) * .7f, 0x2EFFFFFF, 0x1AFFFFFF));
    TextView aa = new TextView(this);
    aa.setText("aA"); aa.setTextColor(ICON); aa.setTextSize(15); aa.setGravity(Gravity.CENTER); aa.setOnClickListener(v -> showMenu());
    hostTv = new TextView(this);
    hostTv.setGravity(Gravity.CENTER); hostTv.setSingleLine(true); hostTv.setEllipsize(TextUtils.TruncateAt.END);
    hostTv.setTextSize(16); hostTv.setTextColor(Color.WHITE); hostTv.setOnClickListener(v -> editUrl());
    reloadI = new Ic(this, Ic.RELOAD);
    reloadI.setOnClickListener(v -> { Tab t = tab(); if (t == null) return; if (loading) t.w.stopLoading(); else t.w.reload(); });
    pill.addView(aa, new LinearLayout.LayoutParams(dp(48), dp(48)));
    pill.addView(hostTv, new LinearLayout.LayoutParams(0, dp(48), 1f));
    pill.addView(reloadI, new LinearLayout.LayoutParams(dp(48), dp(48)));
    panel.addView(pill, new LinearLayout.LayoutParams(-1, dp(48)));

    LinearLayout row = new LinearLayout(this);
    backI = ic(Ic.BACK, v -> { Tab t = tab(); if (t != null && t.w.canGoBack()) t.w.goBack(); });
    fwdI = ic(Ic.FWD, v -> { Tab t = tab(); if (t != null && t.w.canGoForward()) t.w.goForward(); });
    Ic share = ic(Ic.SHARE, v -> share());
    Ic books = ic(Ic.BOOK, v -> showList("Bookmarks", "bm"));
    tabsI = ic(Ic.TABS, v -> showTabs());
    Ic more = ic(Ic.MORE, v -> showMenu());
    for (Ic t : new Ic[]{backI, fwdI, share, books, tabsI, more}) row.addView(t, new LinearLayout.LayoutParams(0, dp(52), 1f));
    panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    setContentView(root);

    Uri d = getIntent().getData();
    newTab(d != null ? d.toString() : null, false);
  }
  Ic ic(int type, View.OnClickListener c) { Ic i = new Ic(this, type); i.setOnClickListener(c); return i; }

  Tab tab() { return cur >= 0 && cur < tabs.size() ? tabs.get(cur) : null; }
  AlertDialog.Builder dlg() { return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert); }

  // ---------- tabs ----------
  void newTab(String url, boolean priv) {
    Tab t = new Tab(); t.priv = priv;
    WebView w = new WebView(this); t.w = w;
    w.setBackgroundColor(Color.BLACK);
    WebSettings s = w.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
    s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    s.setMediaPlaybackRequiresUserGesture(false);
    if (priv) { s.setCacheMode(WebSettings.LOAD_NO_CACHE); s.setSaveFormData(false); }
    if (desktop) s.setUserAgentString(DESKTOP_UA);
    CookieManager.getInstance().setAcceptThirdPartyCookies(w, false);
    w.setDownloadListener((u, ua, cd, mime, len) -> {
      try {
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(u));
        r.setMimeType(mime);
        r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(u));
        r.addRequestHeader("User-Agent", ua);
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(u, cd, mime));
        ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
        Toast.makeText(this, "Downloading\u2026", Toast.LENGTH_SHORT).show();
      } catch (Exception e) { Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show(); }
    });
    w.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        String u = r.getUrl().toString();
        if (u.startsWith("http") || u.startsWith("about:") || u.startsWith("data:")) return false;
        try { startActivity(Intent.parseUri(u, Intent.URI_INTENT_SCHEME)); } catch (Exception ignored) {}
        return true;
      }
      @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
        if (adblock && isBlocked(r.getUrl().getHost()))
          return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        return null;
      }
      @Override public void onPageStarted(WebView v, String u, Bitmap f) {
        if (tab() != null && tab().w == v) { loading = true; updateBar(); }
      }
      @Override public void onPageFinished(WebView v, String u) {
        if (u != null && u.startsWith("http") && !u.startsWith(NEWTAB))   // keep the last lines of a page clear of the bar
          v.evaluateJavascript("(function(){var b=document.body;if(b)b.style.paddingBottom='140px'})()", null);
        if (tab() != null && tab().w == v) { loading = false; progress.setScaleX(0); updateBar(); }
        if (!t.priv && u != null && u.startsWith("http") && !u.startsWith(NEWTAB))
          addList("hist", v.getTitle() == null ? u : v.getTitle(), u, 200);
      }
    });
    w.setWebChromeClient(new WebChromeClient() {
      @Override public void onReceivedTitle(WebView v, String title) { t.title = title; }
      @Override public void onProgressChanged(WebView v, int p) {
        if (tab() != null && tab().w == v) progress.animate().scaleX(p >= 100 ? 0 : p / 100f).setDuration(120).start();
      }
      @Override public void onShowCustomView(View v, CustomViewCallback cb) {
        customView = v; customCb = cb; v.setBackgroundColor(Color.BLACK);
        ((FrameLayout) getWindow().getDecorView()).addView(v);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
      }
      @Override public void onHideCustomView() {
        if (customView == null) return;
        ((FrameLayout) getWindow().getDecorView()).removeView(customView);
        customView = null; customCb.onCustomViewHidden();
        getWindow().getDecorView().setSystemUiVisibility(0);
      }
    });
    content.addView(w, new FrameLayout.LayoutParams(-1, -1));
    tabs.add(t);
    switchTo(tabs.size() - 1);
    if (url == null) loadHome(w); else w.loadUrl(toUrl(url));
  }

  void loadHome(WebView w) {
    StringBuilder fav = new StringBuilder();
    for (String[] b : getList("bm")) {
      String n = b[0].trim().isEmpty() ? "?" : b[0].trim().substring(0, 1).toUpperCase();
      fav.append("<a href='").append(esc(b[1])).append("'><i>").append(esc(n)).append("</i><span>")
         .append(esc(b[0].length() > 12 ? b[0].substring(0, 12) : b[0])).append("</span></a>");
    }
    String html = "<meta name=viewport content='width=device-width,initial-scale=1'><style>"
      + "body{background:#000;color:#fff;font-family:sans-serif;margin:0;padding:18vh 20px 160px;text-align:center}"
      + "h1{font-size:15px;letter-spacing:6px;font-weight:600;color:#8e8e93;margin:0 0 26px}"
      + "input{width:100%;box-sizing:border-box;padding:15px 20px;font-size:17px;border-radius:26px;border:0;outline:0;background:#1c1c1e;color:#fff}"
      + "p{display:flex;flex-wrap:wrap;justify-content:center;gap:18px;margin-top:34px}"
      + "a{width:72px;text-decoration:none;color:#fff;font-size:12px}"
      + "i{display:block;width:60px;height:60px;line-height:60px;margin:0 auto 6px;border-radius:16px;background:#1c1c1e;font-style:normal;font-size:24px;color:#0a84ff}"
      + "</style><h1>SUPERGO</h1><form action='https://www.google.com/search'><input name=q placeholder='Search Google or enter website' autocomplete=off></form>"
      + (fav.length() > 0 ? "<p>" + fav + "</p>" : "");
    w.loadDataWithBaseURL(NEWTAB, html, "text/html", "utf-8", null);
  }
  static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&#39;"); }

  void switchTo(int i) {
    for (int k = 0; k < tabs.size(); k++) tabs.get(k).w.setVisibility(k == i ? View.VISIBLE : View.GONE);
    cur = i; loading = false; progress.setScaleX(0); updateBar();
  }
  void closeTab(int i) {
    Tab t = tabs.remove(i); content.removeView(t.w); t.w.destroy();
    if (tabs.isEmpty()) newTab(null, false); else switchTo(Math.min(i, tabs.size() - 1));
  }

  void updateBar() {
    Tab t = tab(); if (t == null) return;
    String u = t.w.getUrl();
    boolean home = u == null || u.startsWith(NEWTAB) || u.startsWith("data:") || u.equals("about:blank");
    String h = home ? null : Uri.parse(u).getHost();
    hostTv.setText(home ? (t.priv ? "Private browsing" : "Search or enter website") : (h == null ? u : h.replaceFirst("^www\\.", "")));
    hostTv.setTextColor(home ? 0xFF8E8E93 : Color.WHITE);
    reloadI.set(loading ? Ic.STOP : Ic.RELOAD);
    backI.setAlpha(t.w.canGoBack() ? 1f : .3f);
    fwdI.setAlpha(t.w.canGoForward() ? 1f : .3f);
    tabsI.count = tabs.size(); tabsI.invalidate();
  }

  boolean isBlocked(String h) {
    if (h == null) return false;
    Set<String> s = blocked;
    while (h.contains(".")) { if (s.contains(h)) return true; h = h.substring(h.indexOf('.') + 1); }
    return false;
  }

  String toUrl(String x) {
    x = x.trim();
    if (x.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") || x.startsWith("about:") || x.startsWith("data:")) return x;
    if (!x.contains(" ") && (x.contains(".") || x.startsWith("localhost"))) return "https://" + x;
    try { return SEARCH + URLEncoder.encode(x, "UTF-8"); } catch (Exception e) { return SEARCH + x; }
  }

  // ---------- UI actions ----------
  void editUrl() {
    Tab t = tab(); if (t == null) return;
    EditText e = new EditText(this);
    e.setSingleLine(true); e.setImeOptions(EditorInfo.IME_ACTION_GO); e.setHint("Search Google or enter website");
    String u = t.w.getUrl(); e.setText(u == null || u.startsWith(NEWTAB) || u.startsWith("data:") || u.equals("about:blank") ? "" : u); e.selectAll();
    AlertDialog d = dlg().setView(e).create();
    e.setOnEditorActionListener((v, a, ev) -> { t.w.loadUrl(toUrl(e.getText().toString())); d.dismiss(); return true; });
    d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    d.show();
  }
  void share() {
    Tab t = tab(); if (t == null || t.w.getUrl() == null) return;
    startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.w.getUrl()), "Share"));
  }
  void showTabs() {
    String[] names = new String[tabs.size()];
    for (int i = 0; i < names.length; i++)
      names[i] = (i == cur ? "\u25CF  " : "     ") + (tabs.get(i).priv ? "Private \u2022 " : "") + tabs.get(i).title;
    dlg().setTitle(tabs.size() + (tabs.size() == 1 ? " Tab" : " Tabs")).setItems(names, (d, i) -> switchTo(i))
      .setPositiveButton("New tab", (d, i) -> newTab(null, false))
      .setNegativeButton("Close tab", (d, i) -> closeTab(cur)).show();
  }
  void showMenu() {
    String[] m = {"New Tab", "New Private Tab", "Add Bookmark", "History", "Downloads",
      "Request Desktop Site: " + (desktop ? "On" : "Off"), "Ad Blocker: " + (adblock ? "On" : "Off"), "Clear History and Data"};
    dlg().setItems(m, (d, i) -> {
      Tab t = tab();
      switch (i) {
        case 0: newTab(null, false); break;
        case 1: newTab(null, true); break;
        case 2: if (t != null && t.w.getUrl() != null && t.w.getUrl().startsWith("http") && !t.w.getUrl().startsWith(NEWTAB)) {
                  addList("bm", t.title, t.w.getUrl(), 500); Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show(); } break;
        case 3: showList("History", "hist"); break;
        case 4: startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); break;
        case 5: desktop = !desktop;
                for (Tab x : tabs) x.w.getSettings().setUserAgentString(desktop ? DESKTOP_UA : null);
                if (t != null) t.w.reload(); break;
        case 6: adblock = !adblock; sp.edit().putBoolean("ab", adblock).apply(); if (t != null) t.w.reload(); break;
        case 7: for (Tab x : tabs) { x.w.clearCache(true); x.w.clearHistory(); }
                CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData();
                sp.edit().remove("hist").apply(); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show(); break;
      }
    }).show();
  }
  void showList(String title, String key) {
    List<String[]> l = getList(key);
    String[] names = new String[l.size()];
    for (int i = 0; i < names.length; i++) names[i] = l.get(i)[0] + "\n" + l.get(i)[1];
    dlg().setTitle(title).setItems(names, (d, i) -> { Tab t = tab(); if (t != null) t.w.loadUrl(l.get(i)[1]); })
      .setNegativeButton("Close", null)
      .setNeutralButton("Clear", (d, i) -> sp.edit().remove(key).apply()).show();
  }

  // ---------- bookmark / history storage ("title\turl" per line, newest first) ----------
  List<String[]> getList(String key) {
    List<String[]> r = new ArrayList<>();
    for (String line : sp.getString(key, "").split("\n")) { String[] p = line.split("\t", 2); if (p.length == 2) r.add(p); }
    return r;
  }
  void addList(String key, String title, String url, int cap) {
    List<String[]> l = getList(key);
    for (Iterator<String[]> it = l.iterator(); it.hasNext(); ) if (it.next()[1].equals(url)) it.remove();
    l.add(0, new String[]{title.replace("\t", " ").replace("\n", " "), url});
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Math.min(cap, l.size()); i++) sb.append(l.get(i)[0]).append('\t').append(l.get(i)[1]).append('\n');
    sp.edit().putString(key, sb.toString()).apply();
  }

  // ---------- lifecycle ----------
  @Override protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    if (i.getData() != null) newTab(i.getData().toString(), false);
  }
  @Override public void onBackPressed() {
    if (customView != null) { tab().w.getWebChromeClient().onHideCustomView(); return; }
    Tab t = tab();
    if (t != null && t.w.canGoBack()) t.w.goBack();
    else if (tabs.size() > 1) closeTab(cur);
    else super.onBackPressed();
  }
  @Override protected void onPause() { super.onPause(); for (Tab t : tabs) t.w.onPause(); }
  @Override protected void onResume() { super.onResume(); for (Tab t : tabs) t.w.onResume(); }
}
