package com.example.exception;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.lang.reflect.Method;

public class MainActivity2 extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            Class clazz = Class.forName("com.example.exception.Child");
            Method method = clazz.getMethod("aa");

            method.invoke(clazz.newInstance());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
