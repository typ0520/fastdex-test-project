package com.dx168.fastdex.build.snapshoot;

import java.io.File;

/**
 * Created by tong on 17/3/29.
 */
public class FileInfo {
    public String relativePath;
    public long lastModified;
    public long fileLength;

    public FileInfo() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileInfo fileInfo = (FileInfo) o;

        return relativePath != null ? relativePath.equals(fileInfo.relativePath) : fileInfo.relativePath == null;
    }

    public boolean diff(FileInfo old) {
        return lastModified == old.lastModified && fileLength == old.fileLength;
    }

    @Override
    public int hashCode() {
        return relativePath != null ? relativePath.hashCode() : 0;
    }

    public static FileInfo create(File sourceSet, File file) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.relativePath = sourceSet.toPath().relativize(file.toPath()).toString();
        fileInfo.lastModified = file.lastModified();
        fileInfo.fileLength = file.length();
        return fileInfo;
    }
}
