art下能稳定重现

首先在注释掉MainActivity中的new Child().cc()和Parent类的cc方法进行全量打包，然后在打开两处注释执行补丁打包，看着是运行期oat时报的错误,apk中Parent.class中有两个public方法，补丁中的Parent.class有三个public方法
我试了如果把补丁dex按照classesN.dex的标准排好，在打包成apk经过安装时没问题的

```
09-01 18:01:03.512 12029-12029/? I/art: Late-enabling -Xcheck:jni
09-01 18:01:03.574 12029-12029/com.example.exception W/System: ClassLoader referenced unknown path: /data/app/com.example.exception-1/lib/arm
09-01 18:01:03.609 12029-12029/com.example.exception I/MultiDex: VM with version 2.1.0 has multidex support
09-01 18:01:03.609 12029-12029/com.example.exception I/MultiDex: install
09-01 18:01:03.609 12029-12029/com.example.exception I/MultiDex: VM has multidex support, MultiDex support library is disabled.
09-01 18:01:03.616 12029-12029/com.example.exception D/Fastdex: lastSourceModified: 1504288785000
09-01 18:01:03.616 12029-12029/com.example.exception D/Fastdex: load meta-info from assets:
                                                                {
                                                                  "projectPath": "/Users/tong/Desktop/IncompatibleClassChangeError/app",
                                                                  "rootProjectPath": "/Users/tong/Desktop/IncompatibleClassChangeError",
                                                                  "fastdexVersion": "0.3.3",
                                                                  "dexCount": 1,
                                                                  "buildMillis": 1504260111919,
                                                                  "variantName": "Debug",
                                                                  "mergedDexVersion": 0,
                                                                  "patchDexVersion": 0,
                                                                  "resourcesVersion": 0,
                                                                  "active": false
                                                                }
09-01 18:01:03.618 12029-12029/com.example.exception D/Fastdex: file: /data/user/0/com.example.exception/fastdex/patch/res/1__resources.apk renameTo: /data/user/0/com.example.exception/fastdex/patch/res/resources.apk
09-01 18:01:03.619 12029-12029/com.example.exception D/Fastdex: file: /data/user/0/com.example.exception/fastdex/patch/dex/1__patch.dex renameTo: /data/user/0/com.example.exception/fastdex/patch/dex/patch.dex
09-01 18:01:03.620 12029-12029/com.example.exception D/Fastdex: apply new patch: {"buildMillis":1504260111919,"variantName":"Debug","lastPatchPath":"","patchPath":"\/data\/user\/0\/com.example.exception\/fastdex\/patch","lastSourceModified":1504288785000,"mergedDexVersion":0,"patchDexVersion":1,"resourcesVersion":1}
09-01 18:01:03.621 12029-12029/com.example.exception D/Fastdex: apply res patch: /data/user/0/com.example.exception/fastdex/patch/res/resources.apk
09-01 18:01:03.628 12029-12029/com.example.exception W/Tinker.ResourcePatcher: try to clear typedArray cache!
09-01 18:01:03.629 12029-12029/com.example.exception D/Fastdex: apply dex patch: [/data/user/0/com.example.exception/fastdex/patch/dex/patch.dex]
09-01 18:01:03.630 12029-12029/com.example.exception I/Tinker.ClassLoaderAdder: installDexes dexOptDir: /data/user/0/com.example.exception/fastdex/patch/opt, dex size:1
09-01 18:01:03.630 12029-12029/com.example.exception W/System: ClassLoader referenced unknown path:
09-01 18:01:03.635 12029-12029/com.example.exception E/Tinker.NClassLoader: load TinkerTestAndroidNClassLoader fail, try to fixDexElementsForProtectedApp
09-01 18:01:03.827 12029-12029/com.example.exception I/Tinker.ClassLoaderAdder: after loaded classloader: fastdex.runtime.loader.AndroidNClassLoader[DexPathList[[dex file "/data/user/0/com.example.exception/fastdex/patch/dex/patch.dex", zip file "/data/app/com.example.exception-1/base.apk"],nativeLibraryDirectories=[/data/app/com.example.exception-1/lib/arm, /system/lib, /vendor/lib]]], dex size:1
09-01 18:01:03.832 12029-12029/com.example.exception D/Fastdex: About to create real application of class name = android.app.Application
09-01 18:01:03.833 12029-12029/com.example.exception V/Fastdex: Created real app instance successfully :android.app.Application@9e7600c
09-01 18:01:03.834 12029-12029/com.example.exception D/FastdexInstantRun: Starting Instant Run Server for com.example.exception
09-01 18:01:03.865 12029-12029/com.example.exception W/art: Incompatible structural change detected: Structural change of com.example.exception.Parent is hazardous (/data/app/com.example.exception-1/oat/arm/base.odex at compile time, /data/user/0/com.example.exception/fastdex/patch/opt/patch.dex at runtime): Virtual method count off: 2 vs 3
09-01 18:01:03.866 12029-12029/com.example.exception W/art: Lcom/example/exception/Parent; (Compile time):
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Static fields:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Instance fields:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Direct methods:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:   <init>()V
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Virtual methods:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:   aa()V
09-01 18:01:03.866 12029-12029/com.example.exception W/art:   bb()V
09-01 18:01:03.866 12029-12029/com.example.exception W/art: Lcom/example/exception/Parent; (Runtime):
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Static fields:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Instance fields:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Direct methods:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:   <init>()V
09-01 18:01:03.866 12029-12029/com.example.exception W/art:  Virtual methods:
09-01 18:01:03.866 12029-12029/com.example.exception W/art:   aa()V
09-01 18:01:03.866 12029-12029/com.example.exception W/art:   bb()V
09-01 18:01:03.866 12029-12029/com.example.exception W/art:   cc()V
09-01 18:01:03.932 12029-12029/com.example.exception W/art: Before Android 4.1, method android.graphics.PorterDuffColorFilter android.support.graphics.drawable.VectorDrawableCompat.updateTintFilter(android.graphics.PorterDuffColorFilter, android.content.res.ColorStateList, android.graphics.PorterDuff$Mode) would have incorrectly overridden the package-private method in android.graphics.drawable.Drawable
09-01 18:01:04.016 12029-12029/com.example.exception I/art: Rejecting re-init on previously-failed class java.lang.Class<com.example.exception.Child>: java.lang.IncompatibleClassChangeError: Structural change of com.example.exception.Parent is hazardous (/data/app/com.example.exception-1/oat/arm/base.odex at compile time, /data/user/0/com.example.exception/fastdex/patch/opt/patch.dex at runtime): Virtual method count off: 2 vs 3
09-01 18:01:04.016 12029-12029/com.example.exception I/art: Lcom/example/exception/Parent; (Compile time):
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Static fields:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Instance fields:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Direct methods:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:   <init>()V
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Virtual methods:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:   aa()V
09-01 18:01:04.016 12029-12029/com.example.exception I/art:   bb()V
09-01 18:01:04.016 12029-12029/com.example.exception I/art: Lcom/example/exception/Parent; (Runtime):
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Static fields:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Instance fields:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Direct methods:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:   <init>()V
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  Virtual methods:
09-01 18:01:04.016 12029-12029/com.example.exception I/art:   aa()V
09-01 18:01:04.016 12029-12029/com.example.exception I/art:   bb()V
09-01 18:01:04.016 12029-12029/com.example.exception I/art:   cc()V
09-01 18:01:04.016 12029-12029/com.example.exception I/art:  (declaration of 'com.example.exception.Child' appears in /data/app/com.example.exception-1/base.apk:classes2.dex)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class dalvik.system.DexFile.defineClassNative(java.lang.String, java.lang.ClassLoader, java.lang.Object, dalvik.system.DexFile) (DexFile.java:-2)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class dalvik.system.DexFile.defineClass(java.lang.String, java.lang.ClassLoader, java.lang.Object, dalvik.system.DexFile, java.util.List) (DexFile.java:299)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class dalvik.system.DexFile.loadClassBinaryName(java.lang.String, java.lang.ClassLoader, java.util.List) (DexFile.java:292)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class dalvik.system.DexPathList.findClass(java.lang.String, java.util.List) (DexPathList.java:418)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class dalvik.system.BaseDexClassLoader.findClass(java.lang.String) (BaseDexClassLoader.java:54)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class fastdex.runtime.loader.AndroidNClassLoader.findClass(java.lang.String) (AndroidNClassLoader.java:177)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class java.lang.ClassLoader.loadClass(java.lang.String, boolean) (ClassLoader.java:380)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Class java.lang.ClassLoader.loadClass(java.lang.String) (ClassLoader.java:312)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at java.lang.Object java.lang.Class.newInstance!() (Class.java:-2)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at android.app.Activity android.app.Instrumentation.newActivity(java.lang.ClassLoader, java.lang.String, android.content.Intent) (Instrumentation.java:1078)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at android.app.Activity android.app.ActivityThread.performLaunchActivity(android.app.ActivityThread$ActivityClientRecord, android.content.Intent) (ActivityThread.java:2575)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at void android.app.ActivityThread.handleLaunchActivity(android.app.ActivityThread$ActivityClientRecord, android.content.Intent, java.lang.String) (ActivityThread.java:2744)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at void android.app.ActivityThread.-wrap12(android.app.ActivityThread, android.app.ActivityThread$ActivityClientRecord, android.content.Intent, java.lang.String) (ActivityThread.java:-1)
09-01 18:01:04.016 12029-12029/com.example.exception I/art:     at void android.app.ActivityThread$H.handleMessage(android.os.Message) (ActivityThread.java:1493)
09-01 18:01:04.017 12029-12029/com.example.exception I/art:     at void android.os.Handler.dispatchMessage(android.os.Message) (Handler.java:102)
09-01 18:01:04.017 12029-12029/com.example.exception I/art:     at void android.os.Looper.loop() (Looper.java:154)
09-01 18:01:04.017 12029-12029/com.example.exception I/art:     at void android.app.ActivityThread.main(java.lang.String[]) (ActivityThread.java:6137)
09-01 18:01:04.017 12029-12029/com.example.exception I/art:     at java.lang.Object java.lang.reflect.Method.invoke!(java.lang.Object, java.lang.Object[]) (Method.java:-2)
09-01 18:01:04.017 12029-12029/com.example.exception I/art:     at void com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run() (ZygoteInit.java:867)
09-01 18:01:04.017 12029-12029/com.example.exception I/art:     at void com.android.internal.os.ZygoteInit.main(java.lang.String[]) (ZygoteInit.java:757)
09-01 18:01:04.017 12029-12029/com.example.exception D/AndroidRuntime: Shutting down VM
09-01 18:01:04.018 12029-12029/com.example.exception E/AndroidRuntime: FATAL EXCEPTION: main
                                                                       Process: com.example.exception, PID: 12029
                                                                       java.lang.IncompatibleClassChangeError: Structural change of com.example.exception.Parent is hazardous (/data/app/com.example.exception-1/oat/arm/base.odex at compile time, /data/user/0/com.example.exception/fastdex/patch/opt/patch.dex at runtime): Virtual method count off: 2 vs 3
                                                                       Lcom/example/exception/Parent; (Compile time):
                                                                        Static fields:
                                                                        Instance fields:
                                                                        Direct methods:
                                                                         <init>()V
                                                                        Virtual methods:
                                                                         aa()V
                                                                         bb()V
                                                                       Lcom/example/exception/Parent; (Runtime):
                                                                        Static fields:
                                                                        Instance fields:
                                                                        Direct methods:
                                                                         <init>()V
                                                                        Virtual methods:
                                                                         aa()V
                                                                         bb()V
                                                                         cc()V
                                                                        (declaration of 'com.example.exception.Child' appears in /data/app/com.example.exception-1/base.apk:classes2.dex)
                                                                           at dalvik.system.DexFile.defineClassNative(Native Method)
                                                                           at dalvik.system.DexFile.defineClass(DexFile.java:299)
                                                                           at dalvik.system.DexFile.loadClassBinaryName(DexFile.java:292)
                                                                           at dalvik.system.DexPathList.findClass(DexPathList.java:418)
                                                                           at dalvik.system.BaseDexClassLoader.findClass(BaseDexClassLoader.java:54)
                                                                           at fastdex.runtime.loader.AndroidNClassLoader.findClass(AndroidNClassLoader.java:177)
                                                                           at java.lang.ClassLoader.loadClass(ClassLoader.java:380)
                                                                           at java.lang.ClassLoader.loadClass(ClassLoader.java:312)
                                                                           at java.lang.Class.newInstance(Native Method)
                                                                           at android.app.Instrumentation.newActivity(Instrumentation.java:1078)
                                                                           at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2575)
                                                                           at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2744)
                                                                           at android.app.ActivityThread.-wrap12(ActivityThread.java)
                                                                           at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1493)
                                                                           at android.os.Handler.dispatchMessage(Handler.java:102)
                                                                           at android.os.Looper.loop(Looper.java:154)
                                                                           at android.app.ActivityThread.main(ActivityThread.java:6137)
                                                                           at java.lang.reflect.Method.invoke(Native Method)
                                                                           at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:867)
                                                                           at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:757)

```