package com.dx168.fastdex.build.snapshoot.api;

/**
 * Created by tong on 17/3/29.
 */
public abstract class Node<T extends Node> {
    /**
     * 获取索引值
     * @return
     */
    public abstract String getUniqueKey();
    /**
     * 如果没有发生变化返回true，反之false
     * @param anItemInfo
     * @return
     */
    public boolean diffEquals(T anItemInfo) {
        return equals(anItemInfo);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        String uniqueKey = getUniqueKey();
        String anUniqueKey = node.getUniqueKey();
        return uniqueKey != null ? uniqueKey.equals(anUniqueKey) : anUniqueKey == null;

    }

    @Override
    public final int hashCode() {
        String uniqueKey = getUniqueKey();
        return uniqueKey != null ? uniqueKey.hashCode() : 0;
    }
}
