package com.dx168.fastdex.build.snapshoot.file;

import com.dx168.fastdex.build.snapshoot.api.ItemInfo;
import java.io.File;

/**
 * Created by tong on 17/3/29.
 */
public class FileItemInfo extends ItemInfo<FileItemInfo> {
    //public String absolutePath;
    public String relativePath;
    public long lastModified;
    public long fileLength;

    @Override
    public String getUniqueKey() {
        return relativePath;
    }

    @Override
    public boolean diffEquals(FileItemInfo anItemInfo) {
//        return lastModified == anItemInfo.lastModified
//                && fileLength == anItemInfo.fileLength;

        if (this == anItemInfo) return true;
        if (anItemInfo == null) return false;

        if (lastModified != anItemInfo.lastModified) return false;
        if (fileLength != anItemInfo.fileLength) return false;
        return equals(anItemInfo);
    }

    @Override
    public String toString() {
        return "FileItemInfo{" +
                "relativePath='" + relativePath + '\'' +
                ", lastModified=" + lastModified +
                ", fileLength=" + fileLength +
                '}';
    }

    public static FileItemInfo create(File rootDir, File file) {
        //相对路径作为key
        FileItemInfo fileInfo = new FileItemInfo();
        //fileInfo.absolutePath = file.getAbsolutePath();
        fileInfo.relativePath = rootDir.toPath().relativize(file.toPath()).toString();

        fileInfo.lastModified = file.lastModified();
        fileInfo.fileLength = file.length();
        return fileInfo;
    }
}
