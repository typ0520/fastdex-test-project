package com.example.exception;

import android.util.Log;
import com.example.exception.loader.SystemClassLoaderAdder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import dalvik.system.PathClassLoader;

/**
 * Created by tong on 17/9/1.
 */
public class App extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("HAHAH",getApplicationInfo().nativeLibraryDir);
        File file = new File(getFilesDir(), "dex/patch.dex");
        File file2 = new File(getFilesDir(), "dex/classes.dex");

        if (file.exists()) {
            file.delete();
        }

        if (file2.exists()) {
            file2.delete();
        }

        try {
            copyFileUsingStream(getAssets().open(file.getName()),file);
            copyFileUsingStream(getAssets().open(file2.getName()),file2);
        } catch (IOException e) {

        }

        ArrayList<File> files = new ArrayList<>();
        if (file.exists() && file2.exists()) {
            files.add(file);
            files.add(file2);
        }

        if (files.size() > 0) {
            PathClassLoader classLoader = (PathClassLoader) App.class.getClassLoader();
            File dexOptDir = new File(getFilesDir(),"opt");
            if (dexOptDir.exists()) {
                dexOptDir.delete();
            }
            dexOptDir.mkdirs();
            try {
                SystemClassLoaderAdder.installDexes(this,classLoader,dexOptDir,files);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void copyFileUsingStream(InputStream is, File dest) throws IOException {
        FileOutputStream os = null;
        File parent = dest.getParentFile();
        if (parent != null && (!parent.exists())) {
            parent.mkdirs();
        }
        try {
            os = new FileOutputStream(dest, false);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
        }
    }
}
