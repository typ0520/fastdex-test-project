package com.github.typ0520.replace_application;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by tong on 17/9/18.
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("MyApplication","MyApplication onCreate");
    }

    public void sayHello() {
        Toast.makeText(this,"hello",Toast.LENGTH_SHORT).show();
    }
}
