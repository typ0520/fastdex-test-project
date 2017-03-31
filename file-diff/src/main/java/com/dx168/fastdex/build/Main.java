package com.dx168.fastdex.build;

import com.dx168.fastdex.build.snapshoot.api.ResultSet;
import com.dx168.fastdex.build.snapshoot.api.Snapshoot;
import com.dx168.fastdex.build.snapshoot.file.*;
import com.dx168.fastdex.build.snapshoot.sourceset.JavaDirectoryResultSet;
import com.dx168.fastdex.build.snapshoot.sourceset.JavaDirectorySnapshoot;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 目录对比，file.length或者file.lastModified不一样时判定文件发生变化
 *
 * Created by tong on 17/3/10.
 */
public class Main {
    public static void main(String[] args) throws Exception {

        File dir1 = new File("/Users/tong/Desktop/java");
        File dir2 = new File("/Users/tong/Desktop/java2");

//        DirectorySnapshoot snapshoot = new DirectorySnapshoot(dir1,new FileSuffixFilter(".java"));
//        DirectorySnapshoot snapshoot2 = new DirectorySnapshoot(dir2,new FileSuffixFilter(".java"));
//
//        snapshoot.serializeTo(new FileOutputStream("/Users/tong/Desktop/" + dir1.getName() + ".json"));
//        snapshoot2.serializeTo(new FileOutputStream("/Users/tong/Desktop/" + dir2.getName() + ".json"));
//
//        snapshoot = new DirectorySnapshoot();
//        snapshoot.load(new FileInputStream("/Users/tong/Desktop/" + dir1.getName() + ".json"));
//
//        snapshoot2 = new DirectorySnapshoot();
//        snapshoot.load(new FileInputStream("/Users/tong/Desktop/" + dir2.getName() + ".json"));
//
//        String str = new Gson().toJson(snapshoot.diff(snapshoot2));
//
//        DirectoryResultSet r1 = snapshoot.diff(snapshoot2);
//        DirectoryResultSet r2 = snapshoot.diff(snapshoot2);
//        for (FileDiffInfo s : r1) {
//            if (s.old != null) {
//                s.old.lastModified = 0;
//            }
//        }
//        System.out.println(r1.equals(r2));
//
//        DirectoryResultSet diffResult = DirectorySnapshoot.diff(dir1,dir2,new FileSuffixFilter(".java"));

//        JavaFileDiffInfo fileDiffInfo = new JavaFileDiffInfo();
//        fileDiffInfo.status = Status.MODIFIED;
//        fileDiffInfo.uniqueKey = "/com/dx168/xx.java";
//        fileDiffInfo.now = FileItemInfo.create(new File("/com/dx168"),new File("/com/dx168/xx.java"));
//
//        JavaFileDiffInfo fileDiffInfo2 = new JavaFileDiffInfo();
//        fileDiffInfo2.status = Status.MODIFIED;
//        fileDiffInfo2.uniqueKey = "/com/dx168/xx2.java";
//        fileDiffInfo2.now = FileItemInfo.create(new File("/com/dx168"),new File("/com/dx168/xx.java"));
//
//        JavaFileDiffInfo fileDiffInfo3 = new JavaFileDiffInfo();
//        fileDiffInfo3.status = Status.MODIFIED;
//        fileDiffInfo3.uniqueKey = "/com/dx168/xx.java";
//        fileDiffInfo3.now = FileItemInfo.create(new File("/com/dx168"),new File("/com/dx168/xx.java"));
//
//        JavaFileDiffInfo fileDiffInfo4 = new JavaFileDiffInfo();
//        fileDiffInfo4.status = Status.MODIFIED;
//        fileDiffInfo4.uniqueKey = "/com/dx168/xx2.java";
//        fileDiffInfo4.now = FileItemInfo.create(new File("/com/dx168"),new File("/com/dx168/xx.java"));
//
//        Set<JavaFileDiffInfo> set1 = new HashSet<>();
//        Set<JavaFileDiffInfo> set2 = new HashSet<>();
//
//        set1.add(fileDiffInfo);
//        set1.add(fileDiffInfo2);
//
//        set2.add(fileDiffInfo3);
//        set2.add(fileDiffInfo4);
//        System.out.println(set1.equals(set2));
//
        JavaDirectorySnapshoot snapshoot = new JavaDirectorySnapshoot(dir1);
        snapshoot.serializeTo(new FileOutputStream("/Users/tong/Desktop/dir1.json"));

        snapshoot = (JavaDirectorySnapshoot) Snapshoot.load(new FileInputStream("/Users/tong/Desktop/dir1.json"),JavaDirectorySnapshoot.class);



        JavaDirectorySnapshoot snapshoot2 = new JavaDirectorySnapshoot(dir2);
        JavaDirectoryResultSet r1 = (JavaDirectoryResultSet) snapshoot.diff(snapshoot2);
        //r1.serializeTo(new FileOutputStream("/Users/tong/Desktop/result.json"));

        JavaDirectoryResultSet r2 = (JavaDirectoryResultSet) ResultSet.load(new FileInputStream("/Users/tong/Desktop/result.json"),JavaDirectoryResultSet.class);
//
        System.out.println(new Gson().toJson(r1));
        System.out.println(new Gson().toJson(r2));


        System.out.println(r1.diffInfos.equals(r2.diffInfos));

        //diff();
    }

    private static void diff() throws IOException {
        int count = 10;
        int totalTime1 = 0;

        int totalTime2 = 0;

        File dir1 = new File("/Users/tong/Desktop/now2");
        File dir2 = new File("/Users/tong/Desktop/old2");

        for (int i = 0;i < count;i++) {
            long start = System.currentTimeMillis();

            ResultSet diffResult = DirectorySnapshoot.diff(dir1,dir2,new FileSuffixFilter(".java"));
            for (FileDiffInfo diffInfo : (Set<FileDiffInfo>)diffResult.getAllDiffInfos()) {
                System.out.println(diffInfo.toString());
            }
            long end = System.currentTimeMillis();

            long use1 = (end - start);
            totalTime1 += use1;


            start = System.currentTimeMillis();
            List<String> diffRes = JavaDirDiff.diff(dir1,dir2,true);
            for (String str : diffRes) {
                System.out.println("== " + str);
            }
            end = System.currentTimeMillis();
            long use2 = (end - start);
            totalTime2 += use2;

            System.out.println();
            System.out.println("==========use1: " + use1 + "ms" + " use2: " + use2 + "ms");
        }

        System.out.println("count: " + count + " avg1: " + (totalTime1 / count) + "ms" + " avg2: " + (totalTime2 / count) + "ms");
    }
}
