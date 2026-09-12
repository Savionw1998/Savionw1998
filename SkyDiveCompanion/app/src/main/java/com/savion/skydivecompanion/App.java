package com.savion.skydivecompanion;

import android.app.Application;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
