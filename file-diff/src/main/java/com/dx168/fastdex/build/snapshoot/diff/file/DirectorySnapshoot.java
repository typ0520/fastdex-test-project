package com.dx168.fastdex.build.snapshoot.diff.file;

import com.dx168.fastdex.build.snapshoot.diff.Snapshoot;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Created by tong on 17/3/29.
 */
public class DirectorySnapshoot implements Snapshoot<FileDiffInfo,FileItemInfo> {
    private String rootPath;
    private List<FileItemInfo> fileItemInfos;

    public DirectorySnapshoot() {
    }

    public DirectorySnapshoot(File directory,ScanFilter scanFilter) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory can not be null!!");
        }
        if (!directory.exists() || directory.isDirectory()) {
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
    public void addItemInfo(FileItemInfo itemInfo) {
        if (fileItemInfos == null) {
            fileItemInfos = new ArrayList<>();
        }
        fileItemInfos.add(itemInfo);
    }

    @Override
    public Collection<FileItemInfo> getAllItemInfo() {
        if (fileItemInfos == null) {
            return new ArrayList<>();
        }
        return fileItemInfos;
    }

    @Override
    public FileItemInfo getItemInfoByUniqueKey(String uniqueKey) {
        //TODO
        return null;
    }

    @Override
    public Collection<FileDiffInfo> diff(Snapshoot<FileDiffInfo,FileItemInfo> otherSnapshoot) {
        //        Set<FileInfo> deletedFileInfos = new HashSet<>(old.fileInfos);
//        deletedFileInfos.removeAll(fileInfos);

        /*
            假如 当前的sourceSets:
            com/dx168/fastdex/sample/MainActivity.java

            老的sourceSets:
            com/dx168/fastdex/sample/MainActivity.java
            com/dx168/fastdex/sample/MainActivity2.java

            删除的是
            com/dx168/fastdex/sample/MainActivity2.java
         */
        //删除掉的sourceSet
        Set<FileItemInfo> deletedFileInfos = new HashSet<>();
        deletedFileInfos.addAll(otherSnapshoot.getAllItemInfo());
        deletedFileInfos.removeAll(getAllItemInfo());

        /*
            假如 当前的sourceSets:
            com/dx168/fastdex/sample/MainActivity.java
            com/dx168/fastdex/sample/MainActivity2.java

            老的sourceSets:
            com/dx168/fastdex/sample/MainActivity.java

            新增的是
            com/dx168/fastdex/sample/MainActivity2.java
         */
        //新增的sourceSet
        Set<FileItemInfo> increasedFileInfos = new HashSet<>();
        increasedFileInfos.addAll(getAllItemInfo());
        increasedFileInfos.removeAll(otherSnapshoot.getAllItemInfo());

        /*
            假如 当前的sourceSets:
            com/dx168/fastdex/sample/MainActivity.java
            com/dx168/fastdex/sample/MainActivity2.java
            com/dx168/fastdex/sample/MainActivity3.java

            老的sourceSets:
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
        Set<FileItemInfo> needDiffFileInfos = new HashSet<>();
        needDiffFileInfos.addAll(getAllItemInfo());
        needDiffFileInfos.addAll(otherSnapshoot.getAllItemInfo());
        needDiffFileInfos.removeAll(deletedFileInfos);
        needDiffFileInfos.removeAll(increasedFileInfos);

        List<FileDiffInfo> diffInfos = new ArrayList<>();
        scanFromDeletedAndIncreased(diffInfos,otherSnapshoot,deletedFileInfos,increasedFileInfos);

        for (FileItemInfo fileItemInfo : needDiffFileInfos) {
            FileItemInfo now = fileItemInfo;
            FileItemInfo old = otherSnapshoot.getItemInfoByUniqueKey(fileItemInfo.getUniqueKey());

//            if (now.diff(old)) {
//                diffInfos.add(new FileDiffInfo(now,old, DiffInfo.Status.MODIFIED));
//            }
        }
        return diffInfos;
    }

    protected void scanFromDeletedAndIncreased(List<FileDiffInfo> diffInfos, Snapshoot otherSnapshoot, Set<FileItemInfo> deletedFileInfos, Set<FileItemInfo> increasedFileInfos) {

    }

    @Override
    public Snapshoot load(InputStream inputStream) {
        return null;
    }

    @Override
    public void serializeTo(OutputStream outputStream) throws IOException {

    }
}
