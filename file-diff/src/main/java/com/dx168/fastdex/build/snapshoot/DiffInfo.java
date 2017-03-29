package com.dx168.fastdex.build.snapshoot;

/**
 * Created by tong on 17/3/29.
 */
public class DiffInfo {
    public String path;
    public String relativeRootPath;
    public String relativeSourceSetPath;
    public Status status;

    public DiffInfo(String path, String relativeRootPath, String relativeSourceSetPath, Status status) {
        this.path = path;
        this.relativeRootPath = relativeRootPath;
        this.relativeSourceSetPath = relativeSourceSetPath;
        this.status = status;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRelativeRootPath() {
        return relativeRootPath;
    }

    public void setRelativeRootPath(String relativeRootPath) {
        this.relativeRootPath = relativeRootPath;
    }

    public String getRelativeSourceSetPath() {
        return relativeSourceSetPath;
    }

    public void setRelativeSourceSetPath(String relativeSourceSetPath) {
        this.relativeSourceSetPath = relativeSourceSetPath;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public enum Status {
        ADD,DELETE,MODIFIED
    }
}
