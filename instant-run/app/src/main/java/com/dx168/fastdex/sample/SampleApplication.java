package com.dx168.fastdex.sample;

import android.app.ActivityManager;
import android.content.Context;
import android.support.multidex.MultiDex;
import android.util.Log;

import static com.dx168.fastdex.sample.runtime.Server.LOG_TAG;

import android.os.Process;
import android.app.ActivityManager.RunningAppProcessInfo;

import com.dx168.fastdex.sample.runtime.AppInfo;
import com.dx168.fastdex.sample.runtime.Server;

import java.util.List;

/**
 * Created by tong on 17/10/3.
 */
public class SampleApplication extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            boolean foundPackage = false;
            int pid = Process.myPid();
            ActivityManager manager = (ActivityManager) getSystemService(
                    Context.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> processes = manager.getRunningAppProcesses();

            boolean startServer;
            if (processes != null && processes.size() > 1) {
                // Multiple processes: look at each, and if the process name matches
                // the package name (for the current pid), it's the main process.
                startServer = false;
                for (RunningAppProcessInfo processInfo : processes) {
                    if (AppInfo.applicationId.equals(processInfo.processName)) {
                        foundPackage = true;
                        if (processInfo.pid == pid) {
                            startServer = true;
                            break;
                        }
                    }
                }
                if (!startServer && !foundPackage) {
                    // Safety check: If for some reason we didn't even find the main package,
                    // start the server anyway. This safeguards against apps doing strange
                    // things with the process name.
                    startServer = true;
                    if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                        Log.v(LOG_TAG, "Multiprocess but didn't find process with package: "
                                + "starting server anyway");
                    }
                }
            } else {
                // If there is only one process, start the server.
                startServer = true;
            }

            if (startServer) {
                Server.create(AppInfo.applicationId, SampleApplication.this);
            }
        } catch (Throwable t) {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "Failed during multi process check", t);
            }
            Server.create(AppInfo.applicationId, SampleApplication.this);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
    }
}
