package com.dafeiyu.ballpacer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全部数据存在 App 私有目录的 SQLite 里，不联网、不上传。
 */
public class Db extends SQLiteOpenHelper {

    private static final String NAME = "ballpacer.db";
    private static final int VERSION = 2;

    public Db(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE habits ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "mode INTEGER NOT NULL DEFAULT 0,"
                + "weekdays TEXT NOT NULL DEFAULT '',"
                + "monthdays TEXT NOT NULL DEFAULT '',"
                + "total INTEGER NOT NULL DEFAULT 0,"
                + "remind_hour INTEGER NOT NULL DEFAULT 18,"
                + "remind_min INTEGER NOT NULL DEFAULT 30,"
                + "color TEXT NOT NULL DEFAULT '#FFB454',"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "created_at TEXT NOT NULL DEFAULT '',"
                + "advance INTEGER NOT NULL DEFAULT 10)");
        db.execSQL("CREATE TABLE records ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "habit_id INTEGER NOT NULL,"
                + "date TEXT NOT NULL,"
                + "ts INTEGER NOT NULL DEFAULT 0,"
                + "note TEXT NOT NULL DEFAULT '',"
                + "UNIQUE(habit_id, date))");
        db.execSQL("CREATE INDEX idx_rec_habit ON records(habit_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE habits ADD COLUMN advance INTEGER NOT NULL DEFAULT 10");
            } catch (Exception e) {
                // 已经存在就忽略
            }
        }
    }

    public String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    // ==================== 读取 ====================

    public JSONObject dump() {
        JSONObject out = new JSONObject();
        JSONArray habits = new JSONArray();
        JSONArray records = new JSONArray();
        try {
            SQLiteDatabase d = getReadableDatabase();

            Cursor c = d.rawQuery(
                    "SELECT id,name,mode,weekdays,monthdays,total,remind_hour,remind_min,"
                            + "color,created_at,advance FROM habits WHERE active=1 ORDER BY id", null);
            while (c.moveToNext()) {
                JSONObject h = new JSONObject();
                h.put("id", c.getLong(0));
                h.put("name", c.getString(1));
                h.put("mode", c.getInt(2));
                h.put("weekdays", c.getString(3));
                h.put("monthdays", c.getString(4));
                h.put("total", c.getInt(5));
                h.put("remindHour", c.getInt(6));
                h.put("remindMin", c.getInt(7));
                h.put("color", c.getString(8));
                h.put("createdAt", c.getString(9));
                h.put("advance", c.getInt(10));
                habits.put(h);
            }
            c.close();

            Cursor r = d.rawQuery(
                    "SELECT id,habit_id,date,ts,note FROM records ORDER BY date DESC, id DESC", null);
            while (r.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", r.getLong(0));
                o.put("habitId", r.getLong(1));
                o.put("date", r.getString(2));
                o.put("ts", r.getLong(3));
                o.put("note", r.getString(4));
                records.put(o);
            }
            r.close();

            out.put("habits", habits);
            out.put("records", records);
            out.put("today", today());
        } catch (JSONException e) {
            // 理论上不会发生
        }
        return out;
    }

    public long recordId(long habitId, String date) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM records WHERE habit_id=? AND date=?",
                new String[]{String.valueOf(habitId), date});
        long id = 0;
        if (c.moveToFirst()) {
            id = c.getLong(0);
        }
        c.close();
        return id;
    }

    // ==================== 写入 ====================

    public long saveHabit(JSONObject o) {
        ContentValues v = new ContentValues();
        v.put("name", o.optString("name", "打卡"));
        v.put("mode", o.optInt("mode", 0));
        v.put("weekdays", o.optString("weekdays", ""));
        v.put("monthdays", o.optString("monthdays", ""));
        v.put("total", o.optInt("total", 0));
        v.put("remind_hour", o.optInt("remindHour", 18));
        v.put("remind_min", o.optInt("remindMin", 30));
        v.put("advance", o.optInt("advance", 10));
        v.put("color", o.optString("color", "#FFB454"));
        v.put("active", 1);

        long id = o.optLong("id", 0L);
        if (id > 0) {
            getWritableDatabase().update("habits", v, "id=?",
                    new String[]{String.valueOf(id)});
        } else {
            v.put("created_at", today());
            id = getWritableDatabase().insert("habits", null, v);
        }
        return id;
    }

    public void deleteHabit(long id) {
        getWritableDatabase().delete("records", "habit_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("habits", "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * 打卡。同一天同一事项只能一条，冲突时返回已有的记录 id。
     */
    public long checkIn(long habitId, String date, String note) {
        ContentValues v = new ContentValues();
        v.put("habit_id", habitId);
        v.put("date", date);
        v.put("ts", System.currentTimeMillis());
        v.put("note", note == null ? "" : note);
        long id = getWritableDatabase().insertWithOnConflict(
                "records", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id <= 0) {
            id = recordId(habitId, date);
        }
        return id;
    }

    public void setNote(long recordId, String note) {
        ContentValues v = new ContentValues();
        v.put("note", note == null ? "" : note);
        getWritableDatabase().update("records", v, "id=?",
                new String[]{String.valueOf(recordId)});
    }

    public void deleteRecord(long recordId) {
        getWritableDatabase().delete("records", "id=?",
                new String[]{String.valueOf(recordId)});
    }
}
