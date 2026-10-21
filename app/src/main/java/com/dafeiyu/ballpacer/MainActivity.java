package com.dafeiyu.ballpacer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 课包打卡 · 日历版
 *
 * 思路：提醒交给系统日历（每天重复、永不终止，所以你不去也照样提醒），
 * 打卡次数由本 App 记录；每打一次卡就把日历事件的标题更新成当前进度；
 * 打满预定的次数后，本 App 直接把那条日历事件删掉 —— 它在日历里彻底消失。
 */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 1001;

    private static final int C_BG = 0xFF0B0D12;
    private static final int C_CARD = 0xFF171B24;
    private static final int C_LINE = 0xFF262C38;
    private static final int C_TEXT = 0xFFE9ECF1;
    private static final int C_MUTED = 0xFF8B94A4;
    private static final int C_ACCENT = 0xFFFFB454;
    private static final int C_ON_ACCENT = 0xFF20180A;
    private static final int C_GREEN = 0xFF5DDC8F;

    private SharedPreferences sp;

    private TextView tvProgress;
    private TextView tvStatus;
    private TextView tvHistory;
    private ProgressBar bar;
    private Button btnMain;
    private Button btnUndo;
    private Button btnRebuild;

    /** 一个可写入的日历账户 */
    private static class Cal {
        long id;
        String name;
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("ballpacer", MODE_PRIVATE);
        buildUi();
        if (!hasPerm()) {
            requestPerm();
        } else {
            firstRunSetup();
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (hasPerm()) {
                firstRunSetup();
                refresh();
                toast("权限已授予");
            } else {
                toast("没有日历权限，App 无法工作");
                refresh();
            }
        }
    }

    private void firstRunSetup() {
        if (!hasPerm()) {
            return;
        }
        if (!sp.getBoolean("setup_done", false)) {
            if (createEvent()) {
                sp.edit().putBoolean("setup_done", true).apply();
            }
        }
    }

    // ==================== 界面 ====================

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(34));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("课包打卡 · 日历版");
        title.setTextSize(11);
        title.setTextColor(C_MUTED);
        title.setLetterSpacing(0.16f);
        root.addView(title);

        tvProgress = new TextView(this);
        tvProgress.setText("0 / 30");
        tvProgress.setTextSize(52);
        tvProgress.setTypeface(Typeface.DEFAULT_BOLD);
        tvProgress.setTextColor(C_TEXT);
        tvProgress.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(16);
        root.addView(tvProgress, plp);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(30);
        bar.setProgress(0);
        bar.setProgressTintList(ColorStateList.valueOf(C_ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(C_LINE));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        blp.topMargin = dp(6);
        root.addView(bar, blp);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(13);
        tvStatus.setTextColor(C_MUTED);
        tvStatus.setLineSpacing(dp(3), 1.0f);
        tvStatus.setBackground(round(C_CARD, dp(14), C_LINE));
        tvStatus.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(16);
        root.addView(tvStatus, slp);

        btnMain = new Button(this);
        btnMain.setText("打卡 +1");
        btnMain.setTextSize(19);
        btnMain.setAllCaps(false);
        btnMain.setTextColor(C_ON_ACCENT);
        btnMain.setBackgroundTintList(ColorStateList.valueOf(C_ACCENT));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        mlp.topMargin = dp(18);
        root.addView(btnMain, mlp);

        LinearLayout row1 = buttonRow();
        btnUndo = smallButton("撤销一次");
        btnRebuild = smallButton("重建提醒");
        row1.addView(btnUndo, weightLp(0, 1.0f, 0));
        row1.addView(btnRebuild, weightLp(8, 1.0f, 0));
        root.addView(row1);

        LinearLayout row2 = buttonRow();
        Button btnSettings = smallButton("设置");
        Button btnCalendar = smallButton("查看日历");
        row2.addView(btnSettings, weightLp(0, 1.0f, 0));
        row2.addView(btnCalendar, weightLp(8, 1.0f, 0));
        root.addView(row2);

        LinearLayout row3 = buttonRow();
        Button btnReset = smallButton("重新开始（清零）");
        row3.addView(btnReset, weightLp(0, 1.0f, 0));
        root.addView(row3);

        tvHistory = new TextView(this);
        tvHistory.setTextSize(12);
        tvHistory.setTextColor(C_MUTED);
        tvHistory.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(18);
        root.addView(tvHistory, hlp);

        TextView footer = new TextView(this);
        footer.setText("提醒由系统日历负责，不会被清理后台。\n卸载本 App 前请先在“重新开始”里清空，否则日历里会留下那条事件。");
        footer.setTextSize(11);
        footer.setTextColor(0xFF5C6474);
        footer.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(22);
        root.addView(footer, flp);

        setContentView(scroll);

        btnMain.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!hasPerm()) {
                    requestPerm();
                } else {
                    doCheckIn();
                }
            }
        });

        btnUndo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doUndo();
            }
        });

        btnRebuild.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                rebuild();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!hasPerm()) {
                    requestPerm();
                } else {
                    openSettings();
                }
            }
        });

        btnCalendar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openCalendarApp();
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirm("重新开始", "会把打卡进度清零并重建日历事件，确定吗？", new Runnable() {
                    public void run() {
                        resetAll();
                    }
                });
            }
        });
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(C_TEXT);
        b.setBackgroundTintList(ColorStateList.valueOf(C_CARD));
        return b;
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout.LayoutParams weightLp(int leftMarginDp, float weight, int extra) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), weight);
        lp.leftMargin = dp(leftMarginDp);
        return lp;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeColor != 0) {
            d.setStroke(dp(1), strokeColor);
        }
        return d;
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    // ==================== 权限 ====================

    private boolean hasPerm() {
        boolean read = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        boolean write = checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        return read && write;
    }

    private void requestPerm() {
        requestPermissions(new String[]{
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        }, REQ_PERM);
    }

    // ==================== 核心逻辑 ====================

    private int total() {
        return sp.getInt("total", 30);
    }

    private int done() {
        return sp.getInt("done", 0);
    }

    private void doCheckIn() {
        int total = total();
        int done = done();
        if (done >= total) {
            toast("已经完成 " + total + " 次了");
            return;
        }
        done = done + 1;
        String h = sp.getString("history", "");
        h = h.length() == 0 ? today() : h + "," + today();
        sp.edit().putInt("done", done).putString("history", h).apply();

        if (done >= total) {
            deleteEvent();
            toast("完成！日历事件已自动清除");
        } else {
            ensureEvent();
            updateEventText();
            toast("已打卡 " + done + "/" + total + "，还剩 " + (total - done) + " 次");
        }
        refresh();
    }

    private void doUndo() {
        int done = done();
        if (done <= 0) {
            toast("还没有打卡记录");
            return;
        }
        done = done - 1;
        String h = sp.getString("history", "");
        if (h.length() > 0) {
            int idx = h.lastIndexOf(',');
            h = idx >= 0 ? h.substring(0, idx) : "";
        }
        sp.edit().putInt("done", done).putString("history", h).apply();

        ensureEvent();
        updateEventText();
        refresh();
        toast("已撤销，当前 " + done + "/" + total());
    }

    private void resetAll() {
        deleteEvent();
        sp.edit().putInt("done", 0).putString("history", "").apply();
        createEvent();
        refresh();
        toast("已重置");
    }

    private void rebuild() {
        if (!hasPerm()) {
            requestPerm();
            return;
        }
        deleteEvent();
        if (createEvent()) {
            toast("日历事件已重建");
        }
        refresh();
    }

    private void ensureEvent() {
        if (sp.getLong("eventId", -1L) <= 0 && hasPerm()) {
            createEvent();
        }
    }

    // ==================== 日历读写 ====================

    private List<Cal> writableCalendars() {
        List<Cal> out = new ArrayList<Cal>();
        ContentResolver cr = getContentResolver();
        String[] proj = {
                Calendars._ID,
                Calendars.CALENDAR_DISPLAY_NAME,
                Calendars.ACCOUNT_NAME
        };
        String sel = Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] args = {String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR)};
        Cursor c = null;
        try {
            c = cr.query(Calendars.CONTENT_URI, proj, sel, args, null);
            while (c != null && c.moveToNext()) {
                Cal cal = new Cal();
                cal.id = c.getLong(0);
                cal.name = c.getString(1);
                if (cal.name == null || cal.name.length() == 0) {
                    cal.name = "日历";
                }
                String acc = c.getString(2);
                if (acc != null && acc.length() > 0) {
                    cal.name = cal.name + "（" + acc + "）";
                }
                out.add(cal);
            }
        } catch (Exception e) {
            // 权限缺失或厂商 ROM 限制时忽略
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    private String calName() {
        long id = sp.getLong("calId", -1L);
        List<Cal> cals = writableCalendars();
        for (int i = 0; i < cals.size(); i++) {
            if (cals.get(i).id == id) {
                return cals.get(i).name;
            }
        }
        return cals.isEmpty() ? "（无可用日历）" : "（自动选择）";
    }

    private boolean createEvent() {
        if (!hasPerm()) {
            return false;
        }
        List<Cal> cals = writableCalendars();
        if (cals.isEmpty()) {
            toast("没找到可写入的日历，请先在系统日历里添加一个账户");
            return false;
        }
        long calId = sp.getLong("calId", -1L);
        boolean found = false;
        for (int i = 0; i < cals.size(); i++) {
            if (cals.get(i).id == calId) {
                found = true;
            }
        }
        if (!found) {
            calId = cals.get(0).id;
            sp.edit().putLong("calId", calId).apply();
        }

        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, sp.getInt("hour", 18));
        when.set(Calendar.MINUTE, sp.getInt("minute", 30));
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (when.getTimeInMillis() <= System.currentTimeMillis()) {
            when.add(Calendar.DAY_OF_YEAR, 1);
        }

        ContentValues v = new ContentValues();
        v.put(Events.CALENDAR_ID, calId);
        v.put(Events.TITLE, eventTitle());
        v.put(Events.DESCRIPTION, eventDesc());
        v.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        v.put(Events.DTSTART, when.getTimeInMillis());
        v.put(Events.DURATION, "P30M");
        v.put(Events.RRULE, "FREQ=DAILY");
        v.put(Events.HAS_ALARM, 1);

        Uri uri = null;
        try {
            uri = getContentResolver().insert(Events.CONTENT_URI, v);
        } catch (Exception e) {
            toast("创建日历事件失败：" + e.getMessage());
            return false;
        }
        if (uri == null) {
            toast("创建日历事件失败");
            return false;
        }

        long id = ContentUris.parseId(uri);
        ContentValues r = new ContentValues();
        r.put(Reminders.EVENT_ID, id);
        r.put(Reminders.MINUTES, 0);
        r.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        try {
            getContentResolver().insert(Reminders.CONTENT_URI, r);
        } catch (Exception e) {
            // 提醒写入失败不致命，事件本身已存在
        }

        sp.edit().putLong("eventId", id).apply();
        return true;
    }

    private void updateEventText() {
        long id = sp.getLong("eventId", -1L);
        if (id <= 0 || !hasPerm()) {
            return;
        }
        ContentValues v = new ContentValues();
        v.put(Events.TITLE, eventTitle());
        v.put(Events.DESCRIPTION, eventDesc());
        try {
            getContentResolver().update(
                    ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null);
        } catch (Exception e) {
            // 忽略
        }
    }

    private void deleteEvent() {
        long id = sp.getLong("eventId", -1L);
        if (id > 0 && hasPerm()) {
            try {
                getContentResolver().delete(
                        ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null);
            } catch (Exception e) {
                // 忽略
            }
        }
        sp.edit().putLong("eventId", -1L).apply();
    }

    private String eventTitle() {
        String name = sp.getString("name", getString(R.string.default_name));
        return name + " · 已完成 " + done() + "/" + total();
    }

    private String eventDesc() {
        String h = sp.getString("history", "");
        String list = h.length() == 0 ? "（还没有打卡）" : h.replace(",", "、");
        return "共 " + total() + " 次，已完成 " + done() + " 次，还剩 " + Math.max(0, total() - done()) + " 次。\n"
                + "本事件每天提醒，直到打卡满 " + total() + " 次后自动删除。\n"
                + "打卡日期：" + list + "\n"
                + "（由「课包打卡」App 管理，请勿手动修改标题）";
    }

    // ==================== 设置 ====================

    private void openSettings() {
        List<Cal> cals = writableCalendars();
        final String[] calNames = new String[cals.size()];
        for (int i = 0; i < cals.size(); i++) {
            calNames[i] = cals.get(i).name;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);

        box.addView(label("课程名称（会显示在日历上）"));
        final EditText etName = new EditText(this);
        etName.setSingleLine(true);
        etName.setText(sp.getString("name", getString(R.string.default_name)));
        box.addView(etName);

        box.addView(label("总共多少次"));
        final EditText etTotal = new EditText(this);
        etTotal.setSingleLine(true);
        etTotal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etTotal.setImeOptions(EditorInfo.IME_ACTION_DONE);
        etTotal.setText(String.valueOf(total()));
        box.addView(etTotal);

        box.addView(label("每天的提醒时间"));
        final int[] hm = {sp.getInt("hour", 18), sp.getInt("minute", 30)};
        final Button btnTime = new Button(this);
        btnTime.setAllCaps(false);
        btnTime.setTextSize(14);
        btnTime.setText("每天 " + two(hm[0]) + ":" + two(hm[1]) + " 提醒");
        box.addView(btnTime);
        btnTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        hm[0] = hourOfDay;
                        hm[1] = minute;
                        btnTime.setText("每天 " + two(hm[0]) + ":" + two(hm[1]) + " 提醒");
                    }
                }, hm[0], hm[1], true).show();
            }
        });

        box.addView(label("写入哪个日历"));
        final long[] calId = {sp.getLong("calId", -1L)};
        final Button btnCal = new Button(this);
        btnCal.setAllCaps(false);
        btnCal.setTextSize(14);
        btnCal.setText(calName());
        box.addView(btnCal);
        btnCal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (calNames.length == 0) {
                    toast("没有可写入的日历");
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("选择日历")
                        .setItems(calNames, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                calId[0] = cals.get(which).id;
                                btnCal.setText(calNames[which]);
                            }
                        })
                        .show();
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("保存并重建", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        String nm = etName.getText().toString().trim();
                        if (nm.length() == 0) {
                            nm = getString(R.string.default_name);
                        }
                        int t = parseInt(etTotal.getText().toString(), 30);
                        t = Math.max(1, Math.min(999, t));
                        sp.edit()
                                .putString("name", nm)
                                .putInt("total", t)
                                .putInt("hour", hm[0])
                                .putInt("minute", hm[1])
                                .putLong("calId", calId[0])
                                .apply();
                        deleteEvent();
                        // 只有还没完成时才重建事件；如果总次数被调到已完成数以下，就不要留事件
                        if (done() < total()) {
                            createEvent();
                        }
                        refresh();
                        toast("设置已保存");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(C_MUTED);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    // ==================== 刷新界面 ====================

    private void refresh() {
        int total = total();
        int done = done();

        tvProgress.setText(done + " / " + total);
        bar.setMax(total);
        bar.setProgress(Math.min(done, total));

        boolean perm = hasPerm();
        long eventId = sp.getLong("eventId", -1L);

        if (!perm) {
            tvStatus.setText("还没有日历权限。点下面的按钮授权，App 才能写入提醒。");
            btnMain.setText("授权日历权限");
            btnMain.setEnabled(true);
            btnUndo.setEnabled(false);
            btnRebuild.setEnabled(false);
        } else if (done >= total) {
            tvStatus.setText("全部 " + total + " 次已完成，日历里的那条事件已经删掉了。\n可以点“重新开始”开启下一轮。");
            btnMain.setText("已完成");
            btnMain.setEnabled(false);
            btnUndo.setEnabled(true);
            btnRebuild.setEnabled(false);
        } else if (eventId <= 0) {
            tvStatus.setText("还没有创建日历事件。点“重建提醒”来创建。");
            btnMain.setText("打卡 +1");
            btnMain.setEnabled(true);
            btnUndo.setEnabled(done > 0);
            btnRebuild.setEnabled(true);
        } else {
            tvStatus.setText("日历提醒：每天 " + two(sp.getInt("hour", 18)) + ":" + two(sp.getInt("minute", 30))
                    + "（就算你没去打，它也会照常提醒）\n"
                    + "剩余 " + (total - done) + " 次 · 打满后这条日历事件会自动消失");
            btnMain.setText("打卡 +1");
            btnMain.setEnabled(true);
            btnUndo.setEnabled(done > 0);
            btnRebuild.setEnabled(true);
        }

        tvHistory.setText(historyText());
    }

    private String historyText() {
        String h = sp.getString("history", "");
        if (h.length() == 0) {
            return "还没有打卡记录";
        }
        String[] parts = h.split(",");
        StringBuilder sb = new StringBuilder("最近打卡：");
        int start = Math.max(0, parts.length - 7);
        for (int i = start; i < parts.length; i++) {
            if (i > start) {
                sb.append("   ");
            }
            sb.append(parts[i]);
        }
        if (parts.length > 7) {
            sb.append("   …");
        }
        return sb.toString();
    }

    // ==================== 小工具 ====================

    private void openCalendarApp() {
        try {
            Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon();
            b.appendPath("time");
            b.appendPath(Long.toString(System.currentTimeMillis()));
            Intent intent = new Intent(Intent.ACTION_VIEW, b.build());
            startActivity(intent);
        } catch (Exception e) {
            toast("没有找到可以打开的日历应用");
        }
    }

    private void confirm(String title, String msg, final Runnable ok) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        ok.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String today() {
        SimpleDateFormat f = new SimpleDateFormat("MM-dd", Locale.getDefault());
        return f.format(new Date());
    }

    private String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
