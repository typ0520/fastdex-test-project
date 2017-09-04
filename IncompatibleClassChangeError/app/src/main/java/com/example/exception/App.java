package com.example.exception;

import com.example.exception.loader.SystemClassLoaderAdder;
import java.io.File;
import java.util.ArrayList;
import dalvik.system.PathClassLoader;

/**
 * Created by tong on 17/9/1.
 */
public class App extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();

        File file = new File(getFilesDir(),"classes.so");
        File file2 = new File(getFilesDir(),"classes2.so");


        ArrayList<File> files = new ArrayList<>();
        if (file.exists()) {
            files.add(file);
        }
        if (file2.exists()) {
            files.add(file2);
        }

        PathClassLoader classLoader = (PathClassLoader) App.class.getClassLoader();
        File dexOptDir = new File(getFilesDir(),"opt");
        if (!dexOptDir.exists()) {
            dexOptDir.mkdirs();
        }
        try {
            SystemClassLoaderAdder.installDexes(this,classLoader,dexOptDir,files);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
