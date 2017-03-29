package com.dx168.fastdex.build.snapshoot.diff;

/**
 * 目录对比，file.length或者file.lastModified不一样时判定文件发生变化
 *
 * Created by tong on 17/3/10.
 */
public class DiffInfo {
    String relativePath;
    String absolutePath;

    DiffInfo(String relativePath, String absolutePath) {
        this.relativePath = relativePath;
        this.absolutePath = absolutePath;
    }
}
