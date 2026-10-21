package com.dafeiyu.ballpacer;

import android.app.Application;

/**
 * 全局单例。只持有 Application 上下文，不会泄漏 Activity。
 */
public class App extends Application {

    private static App sInstance;

    private Db db;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        db = new Db(this);
    }

    public static App get() {
        return sInstance;
    }

    public Db db() {
        return db;
    }
}
