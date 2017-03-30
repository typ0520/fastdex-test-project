package com.dx168.fastdex.build.snapshoot.diff;

/**
 * 目录对比，file.length或者file.lastModified不一样时判定文件发生变化
 * Created by tong on 17/3/29.
 */
public class BaseDiffInfo<T extends ItemInfo> {
    public Status status;
    public String uniqueKey;
    public T now;//如果是删除此值为null
    public T old;

    public BaseDiffInfo() {
    }

    public BaseDiffInfo(Status status, String uniqueKey,T now,T old) {
        this.status = status;
        this.uniqueKey = uniqueKey;

        this.now = now;
        this.old = old;
    }

    @Override
    public String toString() {
        return "BaseDiffInfo{" +
                "status=" + status +
                ", uniqueKey='" + uniqueKey + '\'' +
                ", now=" + now +
                ", old=" + old +
                '}';
    }
}
