package com.dx168.fastdex.build.snapshoot.api;

import com.dx168.fastdex.build.snapshoot.utils.SerializeUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tong on 17/3/30.
 */
public class ResultSet<T extends DiffInfo> implements STSerializable {
    private Set<T> changedDiffInfos = new HashSet<T>();
    private Set<T> nochangedDiffInfos = new HashSet<T>();

    public ResultSet() {
    }

    public ResultSet(ResultSet resultSet) {
        changedDiffInfos.addAll(resultSet.changedDiffInfos);
        nochangedDiffInfos.addAll(resultSet.nochangedDiffInfos);
    }

    /**
     * 添加对比信息
     * @param diffInfo
     * @return
     */
    public boolean add(T diffInfo) {
        if (diffInfo == null) {
            return false;
        }
        if (diffInfo.status == Status.NOCHANGED) {
            if (nochangedDiffInfos == null) {
                nochangedDiffInfos = new HashSet<T>();
            }
            return nochangedDiffInfos.add(diffInfo);
        }
        if (changedDiffInfos == null) {
            changedDiffInfos = new HashSet<T>();
        }
        return changedDiffInfos.add(diffInfo);
    }

    /**
     * 合并结果集
     * @param resultSet
     */
    public void merge(ResultSet<T> resultSet) {
        if (changedDiffInfos == null) {
            changedDiffInfos = new HashSet<T>();
        }
        changedDiffInfos.addAll(resultSet.changedDiffInfos);
    }

    /**
     * 获取所有发生变化的结果集
     * @return
     */
    public Set<T> getAllChangedDiffInfos() {
        HashSet set =  new HashSet<>();
        set.addAll(changedDiffInfos);
        return set;
    }

    /**
     * 获取所有发生变化的结果集
     * @return
     */
    public Set<T> getAllNochangedDiffInfos() {
        HashSet set =  new HashSet<>();
        set.addAll(nochangedDiffInfos);
        return set;
    }

    public Set<T> getDiffInfos(Status ...statuses) {
        if (statuses == null || statuses.length == 0) {
            HashSet hashSet = new HashSet();
            hashSet.addAll(changedDiffInfos);
            hashSet.addAll(nochangedDiffInfos);
            return hashSet;
        }
        Set<T> set = new HashSet();
        for (T diffInfo : changedDiffInfos) {
            bb : for (Status status : statuses) {
                if (diffInfo.status == status) {
                    set.add(diffInfo);
                    break bb;
                }
            }
        }

        boolean containNochangedStatus = false;
        for (Status status : statuses) {
            if (status == Status.NOCHANGED) {
                containNochangedStatus = true;
                break;
            }
        }
        if (containNochangedStatus) {
            set.addAll(nochangedDiffInfos);
        }
        return changedDiffInfos;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResultSet<?> resultSet = (ResultSet<?>) o;

        return changedDiffInfos != null ? changedDiffInfos.equals(resultSet.changedDiffInfos) : resultSet.changedDiffInfos == null;

    }

    @Override
    public int hashCode() {
        return changedDiffInfos != null ? changedDiffInfos.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ResultSet{" +
                "changedDiffInfos=" + changedDiffInfos +
                '}';
    }

    @Override
    public void serializeTo(OutputStream outputStream) throws IOException {
        SerializeUtils.serializeTo(outputStream,this);
    }

    public static ResultSet load(InputStream inputStream, Class type) throws Exception {
        ResultSet resultSet = (ResultSet) SerializeUtils.load(inputStream,type);
        if (resultSet != null) {
            Constructor constructor = type.getConstructor(resultSet.getClass());
            resultSet = (ResultSet) constructor.newInstance(resultSet);
        }
        return resultSet;
    }
}
