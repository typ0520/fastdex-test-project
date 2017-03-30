package com.dx168.fastdex.build.snapshoot.sourceset;

import com.dx168.fastdex.build.snapshoot.api.Status;
import com.dx168.fastdex.build.snapshoot.file.FileDiffInfo;
import com.dx168.fastdex.build.snapshoot.file.FileItemInfo;

/**
 * 目录对比，file.length或者file.lastModified不一样时判定文件发生变化
 * Created by tong on 17/3/29.
 */
public class JavaFileDiffInfo extends FileDiffInfo {
    public JavaFileDiffInfo() {
    }

    public JavaFileDiffInfo(Status status, FileItemInfo now, FileItemInfo old) {
        super(status, now, old);
    }

    public Status status;
    public String uniqueKey;
    public FileItemInfo now;//如果是删除此值为null
    public FileItemInfo old;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        JavaFileDiffInfo that = (JavaFileDiffInfo) o;

        if (status != that.status) return false;
        if (uniqueKey != null ? !uniqueKey.equals(that.uniqueKey) : that.uniqueKey != null) return false;
        if (now != null ? !now.diffEquals(that.now) : that.now != null) return false;
        return old != null ? old.diffEquals(that.old) : that.old == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (uniqueKey != null ? uniqueKey.hashCode() : 0);
        result = 31 * result + (now != null ? (now.hashCode() + (int)(now.lastModified + now.fileLength)) : 0);
        result = 31 * result + (old != null ? (old.hashCode() + (int)(old.lastModified + old.fileLength)) : 0);
        return result;
    }
}
