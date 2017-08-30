package com.example.tong.butterknife;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.example.tong.butterknife.test.Restarter;

import java.util.List;

public class Main2Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);


        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Activity> activities = Restarter.getActivities(Main2Activity.this, false);

                Restarter.restartApp(Main2Activity.this,activities,true);
            }
        });
    }
}
