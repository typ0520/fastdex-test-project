package com.dx168.fastdex.build.snapshoot.api;

/**
 * Created by tong on 17/3/29.
 */
public abstract class ItemInfo<T extends ItemInfo> {
    /**
     * 获取索引值
     * @return
     */
    public abstract String getUniqueKey();
    /**
     * 如果发生变化返回true，反之false
     * @param anItemInfo
     * @return
     */
    public boolean diff(T anItemInfo) {
        return !getUniqueKey().equals(anItemInfo.getUniqueKey());
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ItemInfo itemInfo = (ItemInfo) o;

        String uniqueKey = getUniqueKey();
        String anUniqueKey = itemInfo.getUniqueKey();
        return uniqueKey != null ? uniqueKey.equals(anUniqueKey) : anUniqueKey == null;

    }

    @Override
    public final int hashCode() {
        String uniqueKey = getUniqueKey();
        return uniqueKey != null ? uniqueKey.hashCode() : 0;
    }
}
