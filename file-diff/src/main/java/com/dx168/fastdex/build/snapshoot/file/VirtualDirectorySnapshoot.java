package com.dx168.fastdex.build.snapshoot.file;

import com.dx168.fastdex.build.snapshoot.api.DiffInfo;
import com.dx168.fastdex.build.snapshoot.api.ResultSet;
import com.dx168.fastdex.build.snapshoot.api.Snapshoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

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
public class VirtualDirectorySnapshoot<DIFF_INFO extends FileDiffInfo,ITEM_INFO extends FileItemInfo> extends Snapshoot<DIFF_INFO,ITEM_INFO> {
    public String rootPath;

    public VirtualDirectorySnapshoot() {
    }

    public VirtualDirectorySnapshoot(VirtualDirectorySnapshoot snapshoot) {
        super(snapshoot);
        this.rootPath = snapshoot.rootPath;
    }

    public VirtualDirectorySnapshoot(File directory) throws IOException {
        this(directory,null);
    }

    public VirtualDirectorySnapshoot(File directory, ScanFilter scanFilter) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory can not be null!!");
        }
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory);
        }
        this.rootPath = directory.getAbsolutePath();
        walkFileTree(directory,scanFilter);
    }

    @Override
    protected DiffInfo createEmptyDiffInfo() {
        return new FileDiffInfo();
    }

    protected void walkFileTree(File directory, ScanFilter scanFilter) throws IOException {
        Files.walkFileTree(directory.toPath(),new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return VirtualDirectorySnapshoot.this.visitFile(file,attrs,scanFilter);
            }
        });
    }

    protected FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs,ScanFilter scanFilter) throws IOException {
        if (scanFilter != null) {
            if (!scanFilter.preVisitFile(filePath.toFile())) {
                return FileVisitResult.CONTINUE;
            }
        }
        addItemInfo((ITEM_INFO) FileItemInfo.create(new File(rootPath),filePath.toFile()));
        return FileVisitResult.CONTINUE;
    }

    public File getAbsoluteFile(FileItemInfo fileItemInfo) {
        return new File(rootPath,fileItemInfo.getUniqueKey());
    }


    public static ResultSet<FileDiffInfo> diff(File now, File old) throws IOException {
        return VirtualDirectorySnapshoot.diff(now,old,null);
    }

    public static ResultSet<FileDiffInfo> diff(File now, File old, ScanFilter scanFilter) throws IOException {
        return  new VirtualDirectorySnapshoot(now,scanFilter).diff(new VirtualDirectorySnapshoot(old,scanFilter));
    }
}
