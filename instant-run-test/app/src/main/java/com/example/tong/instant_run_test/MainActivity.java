package com.example.tong.instant_run_test;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static String str = "11";
    private String xx = "11";

    private String xx2 = "11";

    private String xx23= "22";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        Toast.makeText(this,"4" + new A().toString() + xx + xx23,Toast.LENGTH_LONG).show();
//
//        final int xx = R.id.tv;
//        TextView tv = (TextView) findViewById(R.id.tv);
//        tv.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(getLastSourceModified(this))));
    }

    public void aa() {
    }

    /**
     * 获取上一次源码发生变化的日期
     * @param context
     * @return
     */
    public static long getLastSourceModified(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            return 0;
        }

        return new File(applicationInfo.sourceDir).lastModified();
    }


    public void mm() {

    }
}
