package com.dx168.fastdex.build.snapshoot.diff;

/**
 * Created by tong on 17/3/29.
 */
public abstract class ItemInfo {
    public abstract String getUniqueKey();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ItemInfo itemInfo = (ItemInfo) o;

        String uniqueKey = getUniqueKey();
        String anUniqueKey = itemInfo.getUniqueKey();
        return uniqueKey != null ? uniqueKey.equals(anUniqueKey) : anUniqueKey == null;

    }

    @Override
    public int hashCode() {
        String uniqueKey = getUniqueKey();
        return uniqueKey != null ? uniqueKey.hashCode() : 0;
    }
}
