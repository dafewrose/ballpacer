package com.dafeiyu.ballpacer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 界面用本地 assets/index.html 渲染，原生层只负责：数据读写、通知、接收通知点击。
 * WebView 在 onDestroy 里显式销毁，避免内存泄漏。
 */
public class MainActivity extends Activity {

    private WebView web;
    private boolean pageReady = false;
    private boolean jsCanBack = false;
    private String pending = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Notifier.ensureChannel(this);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0D12);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);

        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(), "AndroidApi");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                flushPending();
            }
        });

        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        web.loadUrl("file:///android_asset/index.html");

        handleIntent(getIntent());
        Notifier.scheduleAll(this);
        Notifier.refreshAll(this);
        askNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{"android.permission.POST_NOTIFICATIONS"}, 9001);
                }
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    // ==================== 来自通知的点击 ====================

    private void handleIntent(Intent it) {
        if (it == null) {
            return;
        }
        long habitId = it.getLongExtra("habitId", 0L);
        if (habitId <= 0) {
            return;
        }
        JSONObject o = new JSONObject();
        try {
            if (Notifier.ACTION_CHECK.equals(it.getAction())) {
                // 通知上的「✓ 打卡」：先记下今天的打卡，再让界面弹留言框
                String today = App.get().db().today();
                long recId = App.get().db().checkIn(habitId, today, "");
                o.put("type", "note");
                o.put("habitId", habitId);
                o.put("recordId", recId);
                o.put("date", today);
                Notifier.refreshAll(this);
            } else {
                o.put("type", "openHabit");
                o.put("habitId", habitId);
            }
        } catch (JSONException e) {
            return;
        }
        deliver(o.toString());
    }

    private void deliver(String json) {
        if (json == null || json.length() == 0) {
            return;
        }
        if (pageReady && web != null) {
            web.evaluateJavascript("window.__native && window.__native(" + json + ");", null);
        } else {
            pending = json;
        }
    }

    private void flushPending() {
        if (pending == null || pending.length() == 0 || web == null) {
            return;
        }
        String p = pending;
        pending = "";
        web.evaluateJavascript("window.__native && window.__native(" + p + ");", null);
    }

    @Override
    public void onBackPressed() {
        if (jsCanBack && web != null) {
            web.evaluateJavascript("window.__back && window.__back();", null);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            if (web != null) {
                web.removeJavascriptInterface("AndroidApi");
                web.stopLoading();
                web.loadUrl("about:blank");
                web.clearHistory();
                if (web.getParent() instanceof ViewGroup) {
                    ((ViewGroup) web.getParent()).removeView(web);
                }
                web.destroy();
                web = null;
            }
        } catch (Exception e) {
            // 忽略
        }
        super.onDestroy();
    }

    // ==================== JS 桥 ====================

    public class Bridge {

        @JavascriptInterface
        public String ready() {
            String p = pending;
            pending = "";
            return p == null ? "" : p;
        }

        @JavascriptInterface
        public String dump() {
            try {
                return App.get().db().dump().toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String saveHabit(String json) {
            try {
                JSONObject o = new JSONObject(json);
                long id = App.get().db().saveHabit(o);
                Notifier.scheduleAll(MainActivity.this);
                Notifier.refreshAll(MainActivity.this);
                return String.valueOf(id);
            } catch (Exception e) {
                return "0";
            }
        }

        @JavascriptInterface
        public void deleteHabit(String id) {
            try {
                long hid = Long.parseLong(id);
                App.get().db().deleteHabit(hid);
                Notifier.cancelFor(MainActivity.this, hid);
                Notifier.refreshAll(MainActivity.this);
            } catch (Exception e) {
                // 忽略
            }
        }

        @JavascriptInterface
        public String checkIn(String habitId, String date, String note) {
            try {
                long hid = Long.parseLong(habitId);
                long rec = App.get().db().checkIn(hid, date, note == null ? "" : note);
                Notifier.refreshAll(MainActivity.this);
                return String.valueOf(rec);
            } catch (Exception e) {
                return "0";
            }
        }

        @JavascriptInterface
        public void setNote(String recordId, String note) {
            try {
                App.get().db().setNote(Long.parseLong(recordId), note == null ? "" : note);
                Notifier.refreshAll(MainActivity.this);
            } catch (Exception e) {
                // 忽略
            }
        }

        @JavascriptInterface
        public void deleteRecord(String recordId) {
            try {
                App.get().db().deleteRecord(Long.parseLong(recordId));
                Notifier.refreshAll(MainActivity.this);
            } catch (Exception e) {
                // 忽略
            }
        }

        @JavascriptInterface
        public void refreshNotifications() {
            Notifier.refreshAll(MainActivity.this);
        }

        @JavascriptInterface
        public void setCanBack(boolean can) {
            jsCanBack = can;
        }

        @JavascriptInterface
        public void toast(final String msg) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
