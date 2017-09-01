package com.example.exception;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Child().aa();


        //把Parent类cc方法注释打开，再把下面的注释打开就会报错
        new Child().cc();
    }
}
