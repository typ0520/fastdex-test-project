package com.dx168.fastdex.build.snapshoot.file;

import com.dx168.fastdex.build.snapshoot.api.Snapshoot;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

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
public class DirectorySnapshoot extends Snapshoot<FileDiffInfo,FileItemInfo> {
    public String rootPath;

    public DirectorySnapshoot() {
    }

    public DirectorySnapshoot(File directory) throws IOException {
        this(directory,null);
    }

    public DirectorySnapshoot(File directory,ScanFilter scanFilter) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory can not be null!!");
        }
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory);
        }
        this.rootPath = directory.getAbsolutePath();
        walkFileTree(directory,scanFilter);
    }

    protected void walkFileTree(File directory,ScanFilter scanFilter) throws IOException {
        Files.walkFileTree(directory.toPath(),new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return DirectorySnapshoot.this.visitFile(file,attrs,scanFilter);
            }
        });
    }

    protected FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs,ScanFilter scanFilter) throws IOException {
        if (scanFilter != null) {
            if (!scanFilter.preVisitFile(filePath.toFile())) {
                return FileVisitResult.CONTINUE;
            }
        }
        addItemInfo(FileItemInfo.create(new File(rootPath),filePath.toFile()));
        return FileVisitResult.CONTINUE;
    }

    @Override
    protected Collection<FileDiffInfo> createEmptyDiffInfos() {
        return new FileDiffResult();
    }

    public FileDiffResult diff(File old,ScanFilter scanFilter) throws IOException {
        return diff(new DirectorySnapshoot(old,scanFilter));
    }

    public File getAbsoluteFile(FileItemInfo fileItemInfo) {
        return new File(rootPath,fileItemInfo.getUniqueKey());
    }

    @Override
    public FileDiffResult diff(Snapshoot<FileDiffInfo, FileItemInfo> otherSnapshoot) {
        return (FileDiffResult) super.diff(otherSnapshoot);
    }

    public static FileDiffResult diff(File now,File old) throws IOException {
        return DirectorySnapshoot.diff(now,old,null);
    }

    public static FileDiffResult diff(File now,File old,ScanFilter scanFilter) throws IOException {
        return new DirectorySnapshoot(now,scanFilter).diff(new DirectorySnapshoot(old,scanFilter));
    }
}
