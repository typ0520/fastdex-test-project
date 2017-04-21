package com.dx168.fastdex.sample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Created by tong on 17/10/3.
 */
public class MainActivity2 extends Activity {
    private static final String TAG = MainActivity2.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String str = getIntent().getStringExtra("data");

        Toast.makeText(this,"MainActivity2 onCreate: " + str,Toast.LENGTH_LONG).show();
    }
}
