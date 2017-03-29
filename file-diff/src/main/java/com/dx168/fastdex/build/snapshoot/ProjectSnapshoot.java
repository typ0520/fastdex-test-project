package com.dx168.fastdex.build.snapshoot;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 {
     "name": "",
     "rootDir": "/project",
     "sourceSetInfos": [
         {
         "path": "/project/src/main/java",
         "relativePath": "/src/main/java",
         "fileInfos": [
             {
                 "relativePath": "com/dx168/fastdex/sample/MainActivity.java",
                 "lastModified": 14325345636,
                 "fileLength": 323232
             },
             {
                 "relativePath": "com/dx168/fastdex/sample/MainActivity.java",
                 "lastModified": 14325345636,
                 "fileLength": 323232
             }
             ],
             "fileInfoMap": {
                 "com/dx168/fastdex/sample/MainActivity.java": 0,
                 "com/dx168/fastdex/sample/MainActivity.java": 1
             }
         }
     ]
 */

/**
 *
 * Created by tong on 17/3/29.
 */
public class ProjectSnapshoot {
    public String name;
    public String rootDir;
    public Set<SourceSetInfo> sourceSetInfos;

    public ProjectSnapshoot() {
    }

    public ProjectSnapshoot(String name, String rootDir, Set<SourceSetInfo> sourceSetInfos) {
        this.name = name;
        this.rootDir = rootDir;
        this.sourceSetInfos = sourceSetInfos;
    }

    /**
     * 保存到本地
     * @param file
     */
    public void serialize(File file) {
        //保存到本地
    }

    public SourceSetInfo getSourceSetInfoByRelativePath(String relativePath) {
        for (SourceSetInfo sourceSetInfo : sourceSetInfos) {
            if (sourceSetInfo.relativePath.equals(relativePath)) {
                return sourceSetInfo;
            }
        }
        return null;
    }

    /**
     * 当前的快照与老的快照作对比(以当前快照为第一视角)
     * @param oldSnapshoot 老的快照
     * @return
     */
    public DiffResult diff(ProjectSnapshoot oldSnapshoot) {
        if (!rootDir.equals(oldSnapshoot.rootDir)) {
            throw new IllegalStateException("root dir not equal");
        }
        /*
            假如 当前的sourceSets:
            src/main/java

            老的sourceSets:
            src/main/java
            src/main/java2

            删除的是
            src/main/java2
         */
        //删除掉的sourceSet
        Set<SourceSetInfo> deletedSourceSetInfos = new HashSet<>(oldSnapshoot.sourceSetInfos);
        deletedSourceSetInfos.removeAll(sourceSetInfos);

        /*
            假如 当前的sourceSets:
            src/main/java
            src/main/java2

            老的sourceSets:
            src/main/java

            新增的是
            src/main/java2
         */
        //新增的sourceSet
        Set<SourceSetInfo> increasedSourceSetInfos = new HashSet<>(sourceSetInfos);
        increasedSourceSetInfos.removeAll(oldSnapshoot.sourceSetInfos);

        /*
            假如 当前的sourceSets:
            src/main/java
            src/main/java2
            src/main/java3

            老的sourceSets:
            src/main/java
            src/main/java2
            src/main/java4

            新增的是
            src/main/java3
            删除的是
            src/main/java4

            除了删除的和新增的就是所有需要进行扫描的SourceSetInfo
            src/main/java
            src/main/java2
         */
        Set<SourceSetInfo> needScanSourceSetInfos = new HashSet<>(sourceSetInfos);
        needScanSourceSetInfos.addAll(oldSnapshoot.sourceSetInfos);
        needScanSourceSetInfos.removeAll(deletedSourceSetInfos);
        needScanSourceSetInfos.removeAll(increasedSourceSetInfos);

        DiffResult result = new DiffResult(deletedSourceSetInfos,increasedSourceSetInfos);
        result.scanFromDeletedAndIncreased();

        for (SourceSetInfo sourceSetInfo : needScanSourceSetInfos) {
            SourceSetInfo now = sourceSetInfo;
            SourceSetInfo old = oldSnapshoot.getSourceSetInfoByRelativePath(sourceSetInfo.relativePath);

            List<DiffInfo> diffInfos = now.diff(old);
            result.addAll(diffInfos);
        }
        return result;
    }

    /**
     * 从本地读取
     * @param file
     * @return
     */
    public static ProjectSnapshoot load(File file, File currentRootDir) {
        //TODO
        return null;
    }

    public static ProjectSnapshoot create(String name, File projectPath, Set<File> sourceSets, ScanFilter scanFilter) throws IOException {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("name is null");
        }
        if (projectPath == null) {
            throw new IllegalArgumentException("project path is null");
        }
        if (!projectPath.isDirectory()) {
            throw new IllegalArgumentException("project path is not directory: " + projectPath.getAbsolutePath());
        }
        if (sourceSets == null || sourceSets.isEmpty()) {
            return null;
        }

        ProjectSnapshoot snapshoot = new ProjectSnapshoot(name,projectPath.getAbsolutePath(),new HashSet<>());
        for (File sourceSet : sourceSets) {
            snapshoot.sourceSetInfos.add(SourceSetInfo.create(projectPath,sourceSet,scanFilter));
        }
        return snapshoot;
    }
}
