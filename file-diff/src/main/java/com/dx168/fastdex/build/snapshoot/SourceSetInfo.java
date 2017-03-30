package com.dx168.fastdex.build.snapshoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Created by tong on 17/3/29.
 */
public class SourceSetInfo {
    public String relativePath;
    private List<FileInfo> fileInfos;
    private Map<String,Integer> fileInfoMap;

    public SourceSetInfo() {
    }

    public SourceSetInfo(String relativePath) {
        this.relativePath = relativePath;
    }

    public void addFileInfo(FileInfo fileInfo) {
        if (fileInfos == null) {
            fileInfos = new ArrayList<>();
        }
        if (fileInfoMap == null) {
            fileInfoMap = new HashMap<>();
        }
        fileInfos.add(fileInfo);
        fileInfoMap.put(fileInfo.relativePath,fileInfos.size() - 1);
    }

    public FileInfo getFileInfoByIndex(int index) {
        return fileInfos.get(index);
    }

    public FileInfo getByRelativePath(String relativePath) {
        return getFileInfoByIndex(fileInfoMap.get(relativePath));
    }

    public String join(JavaSourceSnapshoot javaSourceSnapshoot) {
        return new File(new File(javaSourceSnapshoot.rootDir),relativePath).getAbsolutePath();
    }

    public List<DiffInfo> diff(SourceSetInfo oldSourceSetInfo) {
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
        Set<FileInfo> deletedFileInfos = new HashSet<>(oldSourceSetInfo.fileInfos);
        deletedFileInfos.removeAll(fileInfos);

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
        Set<FileInfo> increasedFileInfos = new HashSet<>(fileInfos);
        increasedFileInfos.removeAll(oldSourceSetInfo.fileInfos);

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
        Set<FileInfo> needDiffFileInfos = new HashSet<>(fileInfos);
        needDiffFileInfos.addAll(oldSourceSetInfo.fileInfos);
        needDiffFileInfos.removeAll(deletedFileInfos);
        needDiffFileInfos.removeAll(increasedFileInfos);

        List<DiffInfo> diffInfos = new ArrayList<>();
        scanFromDeletedAndIncreased(diffInfos,oldSourceSetInfo,deletedFileInfos,increasedFileInfos);

        for (FileInfo fileInfo : needDiffFileInfos) {
            FileInfo now = fileInfo;
            FileInfo old = oldSourceSetInfo.getByRelativePath(fileInfo.relativePath);

            if (now.diff(old)) {
                diffInfos.add(new DiffInfo(now,old, DiffInfo.Status.MODIFIED));
            }
        }
        return diffInfos;
    }

    private void scanFromDeletedAndIncreased(List<DiffInfo> diffInfos,SourceSetInfo oldSourceSetInfo, Set<FileInfo> deletedFileInfos, Set<FileInfo> increasedFileInfos) {
        if (deletedFileInfos != null) {
            for (FileInfo fileInfo : deletedFileInfos) {
                diffInfos.add(new DiffInfo(null,fileInfo, DiffInfo.Status.DELETE));
            }
        }
        if (increasedFileInfos != null) {
            for (FileInfo fileInfo : increasedFileInfos) {
                diffInfos.add(new DiffInfo(fileInfo,null, DiffInfo.Status.DELETE));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SourceSetInfo that = (SourceSetInfo) o;

        return relativePath != null ? relativePath.equals(that.relativePath) : that.relativePath == null;

    }

    @Override
    public int hashCode() {
        return relativePath != null ? relativePath.hashCode() : 0;
    }

    public static SourceSetInfo create(File projectPath, File sourceSet, ScanFilter scanFilter) throws IOException {
        SourceSetInfo sourceSetInfo = new SourceSetInfo();
        sourceSetInfo.relativePath = projectPath.toPath().relativize(sourceSet.toPath()).toString();

        Files.walkFileTree(sourceSet.toPath(),new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                if (scanFilter != null) {
                    if (!scanFilter.preVisitFile(filePath.toFile())) {
                        return FileVisitResult.CONTINUE;
                    }
                }
                sourceSetInfo.addFileInfo(FileInfo.create(sourceSet,filePath.toFile()));
                return FileVisitResult.CONTINUE;
            }
        });
        return sourceSetInfo;
    }
}
