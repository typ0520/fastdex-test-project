package com.dx168.fastdex.build.snapshoot.diff.file;

import com.dx168.fastdex.build.snapshoot.diff.BaseDiffInfo;
import com.dx168.fastdex.build.snapshoot.diff.Status;

/**
 * 目录对比，file.length或者file.lastModified不一样时判定文件发生变化
 * Created by tong on 17/3/29.
 */
public class FileDiffInfo extends BaseDiffInfo {
    public String path;
    public String relativePath;

    //如果是删除没有这两个值
    public Long lastModified;
    public Long fileLength;

    public FileDiffInfo(String path, String relativePath, Status status) {
        super(status,relativePath);
        this.path = path;
        this.relativePath = relativePath;
    }
}
