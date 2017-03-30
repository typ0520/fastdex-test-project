package com.dx168.fastdex.build.snapshoot.diff.file;

import com.dx168.fastdex.build.snapshoot.diff.ItemInfo;
import java.io.File;

/**
 * Created by tong on 17/3/29.
 */
public class FileItemInfo extends ItemInfo {
    public String absolutePath;
    public String relativePath;
    public long lastModified;
    public long fileLength;

    @Override
    public String getUniqueKey() {
        return relativePath;
    }

    /**
     * 如果发生变化返回true，反之false
     * @param old
     * @return
     */
    public boolean diff(FileItemInfo old) {
        return !(lastModified == old.lastModified && fileLength == old.fileLength);
    }

    public static FileItemInfo create(File rootDir, File file) {
        FileItemInfo fileInfo = new FileItemInfo();
        fileInfo.absolutePath = file.getAbsolutePath();
        fileInfo.relativePath = rootDir.toPath().relativize(file.toPath()).toString();
        fileInfo.lastModified = file.lastModified();
        fileInfo.fileLength = file.length();
        return fileInfo;
    }
}
