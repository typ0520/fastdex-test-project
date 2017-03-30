package com.dx168.fastdex.build;

import com.dx168.fastdex.build.snapshoot.diff.file.DirectorySnapshoot;
import com.dx168.fastdex.build.snapshoot.diff.file.FileDiffInfo;
import com.dx168.fastdex.build.snapshoot.diff.file.FileDiffResult;
import com.dx168.fastdex.build.snapshoot.diff.file.FileSuffixFilter;
import java.io.File;
import java.io.IOException;

/**
 * 目录对比，file.length或者file.lastModified不一样时判定文件发生变化
 *
 * Created by tong on 17/3/10.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        int count = 100;
        int totalTime1 = 0;

        int totalTime2 = 0;

        for (int i = 0;i < count;i++) {
            long start = System.currentTimeMillis();

            FileDiffResult diffResult = DirectorySnapshoot.diff(new File("/Users/tong/Desktop/now2"),new File("/Users/tong/Desktop/old2"),new FileSuffixFilter(".java"));
//            for (FileDiffInfo diffInfo : diffResult) {
//                System.out.println(diffInfo.toString());
//            }
            long end = System.currentTimeMillis();

            long use1 = (end - start);
            totalTime1 += use1;


            start = System.currentTimeMillis();
            JavaDirDiff.diff(new File("/Users/tong/Desktop/now2"),new File("/Users/tong/Desktop/old2"),true);
            end = System.currentTimeMillis();
            long use2 = (end - start);
            totalTime2 += use2;

            System.out.println(" use1: " + use1 + "ms" + " use2: " + use2 + "ms");
        }

        System.out.println("count: " + count + " avg1: " + (totalTime1 / count) + "ms" + " avg2: " + (totalTime2 / count) + "ms");
    }
}
