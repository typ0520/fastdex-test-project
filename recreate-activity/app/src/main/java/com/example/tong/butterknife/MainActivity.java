package com.example.tong.butterknife;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.example.tong.butterknife.test.MonkeyPatcher;
import com.example.tong.butterknife.test.Restarter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static MainActivity mainActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainActivity = this;

        findViewById(R.id.btn_m2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,Main2Activity.class));
            }
        });

        findViewById(R.id.btn_go).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this,"go",Toast.LENGTH_LONG).show();
                File file = null;

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    file = new File(getFilesDir(),"res/resources.apk");
                }
                else {
                    new File(getFilesDir(),"res/");
                }

                file = new File(getFilesDir(),"res/resources.apk");

                if (file.exists()) {
                    List<Activity> activityList = new ArrayList<>();
                    activityList.add(MainActivity.this);
                    MonkeyPatcher.monkeyPatchExistingResources(MainActivity.this,file.getAbsolutePath(),activityList);
                    Restarter.restartActivityOnUiThread(MainActivity.this);
                }
            }
        });

        findViewById(R.id.btn_rc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recreate();

            }
        });

        Toast.makeText(MainActivity.this, "3 " + getApplication().getResources().getString(R.string.test_str),Toast.LENGTH_LONG).show();
    }
}
