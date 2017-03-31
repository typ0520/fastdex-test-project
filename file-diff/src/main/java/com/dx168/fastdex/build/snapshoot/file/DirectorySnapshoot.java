package com.dx168.fastdex.build.snapshoot.file;

/**

 当前
 com/dx168/fastdex/sample/MainActivity.java
 老的
 com/dx168/fastdex/sample/MainActivity.java
 com/dx168/fastdex/sample/MainActivity2.java
 删除的是
 com/dx168/fastdex/sample/MainActivity2.java

 假如
 com/dx168/fastdex/sample/MainActivity.java
 com/dx168/fastdex/sample/MainActivity2.java
 老的
 com/dx168/fastdex/sample/MainActivity.java
 新增的是
 com/dx168/fastdex/sample/MainActivity2.java

 当前的
 com/dx168/fastdex/sample/MainActivity.java
 com/dx168/fastdex/sample/MainActivity2.java
 com/dx168/fastdex/sample/MainActivity3.java
 老的
 com/dx168/fastdex/sample/MainActivity.java
 com/dx168/fastdex/sample/MainActivity2.java
 com/dx168/fastdex/sample/MainActivity4.java
 新增的是
 com/dx168/fastdex/sample/MainActivity3.java
 删除的是
 com/dx168/fastdex/sample/MainActivity4.java

 除了删除的和新增的就是所有需要进行扫描的SourceSetInfo
 com/dx168/fastdex/sample/MainActivity.java
 com/dx168/fastdex/sample/MainActivity2.java
 */

/**
 * Created by tong on 17/3/29.
 */
public final class DirectorySnapshoot extends VirtualDirectorySnapshoot<FileDiffInfo,FileItemInfo> {

}
