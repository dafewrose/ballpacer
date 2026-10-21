package com.dafeiyu.ballpacer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * 两套通知，分工明确：
 *  1) 常驻卡片（低优先级、不响）—— 让你随时能看到今天还有什么没打卡
 *  2) 提醒通知（高优先级、会弹横幅+响铃）—— 到点、以及提前 N 分钟各弹一次
 *
 * 每个事项两个闹钟：提前提醒 + 到点提醒。
 */
public class Notifier {

    public static final String CH_CARDS = "cards_v2";
    public static final String CH_ALERT = "alerts_v1";

    public static final String ACTION_CHECK = "com.dafeiyu.ballpacer.CHECK";
    public static final String ACTION_REMIND = "com.dafeiyu.ballpacer.REMIND";
    public static final String ACTION_LEAD = "com.dafeiyu.ballpacer.LEAD";

    private static int cardId(long habitId) {
        return (int) (1000 + habitId);
    }

    private static int alertId(long habitId) {
        return (int) (5000 + habitId);
    }

    private static int piFlags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            f |= PendingIntent.FLAG_IMMUTABLE;
        }
        return f;
    }

    // ==================== 通道 ====================

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CH_CARDS) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CH_CARDS, "打卡卡片", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("常驻的打卡卡片，安静、不打扰");
            ch.setShowBadge(false);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ALERT, "打卡提醒（会弹出）", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("到点和提前 N 分钟的提醒，会弹出横幅并响铃");
            ch.setShowBadge(true);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    // ==================== 点击入口 ====================

    private static PendingIntent contentPi(Context c, long habitId) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse("ballpacer://open/" + habitId));
        i.putExtra("habitId", habitId);
        return PendingIntent.getActivity(c, (int) (habitId * 10L + 1L), i, piFlags());
    }

    private static PendingIntent checkPi(Context c, long habitId) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction(ACTION_CHECK);
        i.setData(Uri.parse("ballpacer://check/" + habitId));
        i.putExtra("habitId", habitId);
        return PendingIntent.getActivity(c, (int) (habitId * 10L + 2L), i, piFlags());
    }

    private static PendingIntent remindPi(Context c, long habitId) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction(ACTION_REMIND);
        i.setData(Uri.parse("ballpacer://remind/" + habitId));
        i.putExtra("habitId", habitId);
        return PendingIntent.getBroadcast(c, (int) habitId, i, piFlags());
    }

    private static PendingIntent leadPi(Context c, long habitId) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction(ACTION_LEAD);
        i.setData(Uri.parse("ballpacer://lead/" + habitId));
        i.putExtra("habitId", habitId);
        return PendingIntent.getBroadcast(c, (int) (habitId + 500000L), i, piFlags());
    }

    private static Notification.Builder builder(Context c, String channel) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(c, channel);
        }
        return new Notification.Builder(c);
    }

    // ==================== 常驻卡片 ====================

    public static void postCard(Context c, JSONObject h, JSONArray recs, String today, boolean alert) {
        try {
            long id = h.optLong("id");
            if (id <= 0) {
                return;
            }
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            if (alert) {
                nm.cancel(cardId(id));
            }

            int mode = h.optInt("mode", 0);
            String name = h.optString("name", "打卡");
            int total = h.optInt("total", 0);

            int doneCount = 0;
            boolean doneToday = false;
            if (recs != null) {
                for (int i = 0; i < recs.length(); i++) {
                    JSONObject r = recs.optJSONObject(i);
                    if (r == null || r.optLong("habitId") != id) {
                        continue;
                    }
                    doneCount++;
                    if (today.equals(r.optString("date"))) {
                        doneToday = true;
                    }
                }
            }

            String text;
            if (mode == 1) {
                int left = total - doneCount;
                if (left < 0) {
                    left = 0;
                }
                text = (doneToday ? "今天已打卡" : "今天还没打卡") + " · 还剩 " + left + " 次";
            } else {
                text = doneToday ? "今天已打卡 ✓" : "今天该打卡了";
            }
            text = text + " · " + today;

            Notification.Builder nb = builder(c, CH_CARDS);
            nb.setSmallIcon(R.drawable.ic_stat_check);
            nb.setContentTitle(name);
            nb.setContentText(text);
            nb.setStyle(new Notification.BigTextStyle().bigText(text));
            nb.setOngoing(true);
            nb.setShowWhen(false);
            nb.setOnlyAlertOnce(true);
            nb.setContentIntent(contentPi(c, id));
            if (doneToday) {
                nb.addAction(0, "打开查看", contentPi(c, id));
            } else {
                nb.addAction(0, "✓ 打卡", checkPi(c, id));
                nb.addAction(0, "打开", contentPi(c, id));
            }
            try {
                nb.setColor(Color.parseColor(h.optString("color", "#FFB454")));
            } catch (Exception e) {
                // 忽略
            }
            if (Build.VERSION.SDK_INT < 26) {
                nb.setPriority(Notification.PRIORITY_LOW);
            }
            nm.notify(cardId(id), nb.build());
        } catch (Exception e) {
            // 通知失败不影响数据
        }
    }

    // ==================== 会弹出的提醒 ====================

    private static void postAlert(Context c, JSONObject h, boolean lead, int advance) {
        try {
            long id = h.optLong("id");
            if (id <= 0) {
                return;
            }
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            String name = h.optString("name", "打卡");
            String text = lead ? ("还有 " + advance + " 分钟就要开始了") : "该打卡了，别忘了";

            Notification.Builder nb = builder(c, CH_ALERT);
            nb.setSmallIcon(R.drawable.ic_stat_check);
            nb.setContentTitle(name);
            nb.setContentText(text);
            nb.setStyle(new Notification.BigTextStyle().bigText(text));
            nb.setAutoCancel(true);
            nb.setOngoing(false);
            nb.setOnlyAlertOnce(false);
            nb.setShowWhen(true);
            nb.setWhen(System.currentTimeMillis());
            nb.setContentIntent(contentPi(c, id));
            nb.addAction(0, "✓ 打卡", checkPi(c, id));
            nb.addAction(0, "打开", contentPi(c, id));
            try {
                nb.setColor(Color.parseColor(h.optString("color", "#FFB454")));
            } catch (Exception e) {
                // 忽略
            }
            if (Build.VERSION.SDK_INT < 26) {
                nb.setPriority(Notification.PRIORITY_HIGH);
                nb.setDefaults(Notification.DEFAULT_ALL);
            }
            nm.notify(alertId(id), nb.build());
        } catch (Exception e) {
            // 忽略
        }
    }

    // ==================== 对外 ====================

    public static void refreshAll(Context c) {
        try {
            ensureChannel(c);
            JSONObject d = App.get().db().dump();
            JSONArray habits = d.optJSONArray("habits");
            JSONArray recs = d.optJSONArray("records");
            String today = d.optString("today", "");
            if (habits == null) {
                return;
            }
            for (int i = 0; i < habits.length(); i++) {
                JSONObject h = habits.optJSONObject(i);
                if (h != null) {
                    postCard(c, h, recs, today, false);
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 闹钟到点：lead=true 是「提前提醒」，false 是「到点提醒」。
     */
    public static void onAlarm(Context c, long habitId, boolean lead) {
        try {
            ensureChannel(c);
            JSONObject d = App.get().db().dump();
            JSONArray habits = d.optJSONArray("habits");
            JSONArray recs = d.optJSONArray("records");
            String today = d.optString("today", "");
            if (habits == null) {
                return;
            }
            JSONObject target = null;
            for (int i = 0; i < habits.length(); i++) {
                JSONObject h = habits.optJSONObject(i);
                if (h != null && h.optLong("id") == habitId) {
                    target = h;
                    break;
                }
            }
            if (target == null) {
                return;
            }
            boolean doneToday = false;
            if (recs != null) {
                for (int i = 0; i < recs.length(); i++) {
                    JSONObject r = recs.optJSONObject(i);
                    if (r != null && r.optLong("habitId") == habitId
                            && today.equals(r.optString("date"))) {
                        doneToday = true;
                        break;
                    }
                }
            }
            if (!doneToday) {
                postAlert(c, target, lead, target.optInt("advance", 10));
            }
            if (!lead) {
                postCard(c, target, recs, today, false);
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    // ==================== 闹钟登记 ====================

    private static long nextAt(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long t = cal.getTimeInMillis();
        if (t <= System.currentTimeMillis()) {
            t += 24L * 3600L * 1000L;
        }
        return t;
    }

    public static void schedule(Context c, long habitId, int hour, int minute, int advance) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                return;
            }
            long at = nextAt(hour, minute);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at,
                    AlarmManager.INTERVAL_DAY, remindPi(c, habitId));

            if (advance > 0) {
                long leadAt = at - advance * 60000L;
                if (leadAt <= System.currentTimeMillis()) {
                    leadAt += 24L * 3600L * 1000L;
                }
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, leadAt,
                        AlarmManager.INTERVAL_DAY, leadPi(c, habitId));
            } else {
                am.cancel(leadPi(c, habitId));
            }
        } catch (Exception e) {
            // 某些 ROM 会拒绝，忽略
        }
    }

    public static void scheduleAll(Context c) {
        try {
            JSONObject d = App.get().db().dump();
            JSONArray habits = d.optJSONArray("habits");
            if (habits == null) {
                return;
            }
            for (int i = 0; i < habits.length(); i++) {
                JSONObject h = habits.optJSONObject(i);
                if (h == null) {
                    continue;
                }
                schedule(c, h.optLong("id"),
                        h.optInt("remindHour", 18), h.optInt("remindMin", 30),
                        h.optInt("advance", 10));
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    public static void cancelFor(Context c, long habitId) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(remindPi(c, habitId));
                am.cancel(leadPi(c, habitId));
            }
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(cardId(habitId));
                nm.cancel(alertId(habitId));
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}
