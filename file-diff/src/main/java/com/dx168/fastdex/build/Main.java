package com.dx168.fastdex.build;

import com.dx168.fastdex.build.snapshoot.file.DirectorySnapshoot;
import com.dx168.fastdex.build.snapshoot.file.FileDiffInfo;
import com.dx168.fastdex.build.snapshoot.file.DirectoryDiffResult;
import com.dx168.fastdex.build.snapshoot.file.FileSuffixFilter;
import com.dx168.fastdex.build.snapshoot.sourceset.JavaDirectorySnapshoot;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 目录对比，file.length或者file.lastModified不一样时判定文件发生变化
 *
 * Created by tong on 17/3/10.
 */
public class Main {
    public static void main(String[] args) throws IOException {

        File dir1 = new File("/Users/tong/Desktop/now2");
        File dir2 = new File("/Users/tong/Desktop/old2");

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
//        DirectoryDiffResult r1 = snapshoot.diff(snapshoot2);
//        DirectoryDiffResult r2 = snapshoot.diff(snapshoot2);
//        for (FileDiffInfo s : r1) {
//            if (s.old != null) {
//                s.old.lastModified = 0;
//            }
//        }
//        System.out.println(r1.equals(r2));
//
//        DirectoryDiffResult diffResult = DirectorySnapshoot.diff(dir1,dir2,new FileSuffixFilter(".java"));


        JavaDirectorySnapshoot snapshoot = new JavaDirectorySnapshoot(dir1);
        JavaDirectorySnapshoot snapshoot2 = new JavaDirectorySnapshoot(dir2);

        snapshoot.serializeTo(new FileOutputStream("/Users/tong/Desktop/" + dir1.getName() + ".json"));
        snapshoot2.serializeTo(new FileOutputStream("/Users/tong/Desktop/" + dir2.getName() + ".json"));

        snapshoot = new JavaDirectorySnapshoot();
        snapshoot.load(new FileInputStream("/Users/tong/Desktop/" + dir1.getName() + ".json"));

        snapshoot2 = new JavaDirectorySnapshoot();
        snapshoot.load(new FileInputStream("/Users/tong/Desktop/" + dir2.getName() + ".json"));

        String str = new Gson().toJson(snapshoot.diff(snapshoot2));

        DirectoryDiffResult r1 = snapshoot.diff(snapshoot2);
        DirectoryDiffResult r2 = snapshoot.diff(snapshoot2);
        for (FileDiffInfo s : r1) {
            if (s.old != null) {
                s.old.lastModified = 0;
            }
        }
        System.out.println(r1.equals(r2));

        DirectoryDiffResult diffResult = DirectorySnapshoot.diff(dir1,dir2,new FileSuffixFilter(".java"));

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

            DirectoryDiffResult diffResult = DirectorySnapshoot.diff(dir1,dir2,new FileSuffixFilter(".java"));
            for (FileDiffInfo diffInfo : diffResult) {
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
