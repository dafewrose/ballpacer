package com.dafeiyu.ballpacer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 处理三件事：到点提醒、提前提醒、开机后重新登记闹钟。
 */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent == null ? null : intent.getAction();

            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                Notifier.scheduleAll(context);
                Notifier.refreshAll(context);
                return;
            }

            long habitId = intent.getLongExtra("habitId", 0L);
            if (Notifier.ACTION_LEAD.equals(action)) {
                if (habitId > 0) {
                    Notifier.onAlarm(context, habitId, true);
                }
                return;
            }
            if (Notifier.ACTION_REMIND.equals(action)) {
                if (habitId > 0) {
                    Notifier.onAlarm(context, habitId, false);
                } else {
                    Notifier.refreshAll(context);
                }
            }
        } catch (Exception e) {
            // 兜底，不要让广播崩掉
        }
    }
}
