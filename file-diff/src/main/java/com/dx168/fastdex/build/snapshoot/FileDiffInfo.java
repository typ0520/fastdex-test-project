package com.dx168.fastdex.build.snapshoot;

/**
 * Created by tong on 17/3/29.
 */
public class FileDiffInfo {
    public String relativePath;
    public long lastModified;
    public long fileLength;

    public FileDiffInfo() {
    }

    public FileDiffInfo(String relativePath, long lastModified, long fileLength) {
        this.relativePath = relativePath;
        this.lastModified = lastModified;
        this.fileLength = fileLength;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileDiffInfo that = (FileDiffInfo) o;

        return relativePath != null ? relativePath.equals(that.relativePath) : that.relativePath == null;

    }

    @Override
    public int hashCode() {
        return relativePath != null ? relativePath.hashCode() : 0;
    }

    public boolean diff(FileDiffInfo fileDiffInfo) {
        if (fileDiffInfo == null) {
            return true;
        }

        return relativePath.equals(fileDiffInfo.relativePath)
                && lastModified == fileDiffInfo.lastModified
                && fileLength == fileDiffInfo.fileLength;
    }
}
