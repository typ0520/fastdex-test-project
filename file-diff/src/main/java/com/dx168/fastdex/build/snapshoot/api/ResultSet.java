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
    public Set<T> diffInfos = new HashSet<T>();

    public ResultSet() {
    }

    public ResultSet(ResultSet resultSet) {
        diffInfos.addAll(resultSet.diffInfos);
    }

    public boolean add(T diffInfo) {
        if (diffInfos == null) {
            diffInfos = new HashSet<T>();
        }
        return diffInfos.add(diffInfo);
    }

    public void merge(ResultSet<T> resultSet) {
        if (diffInfos == null) {
            diffInfos = new HashSet<T>();
        }
        diffInfos.addAll(resultSet.diffInfos);
    }

    public Set<T> getAllDiffInfos() {
        HashSet set =  new HashSet<>();
        set.addAll(diffInfos);
        return set;
    }

    public Set<T> getDiffInfos(Status ...statuses) {
        if (statuses == null || statuses.length == 0) {
            return getAllDiffInfos();
        }
        Set<T> set = new HashSet();
        for (T diffInfo : diffInfos) {
            bb : for (Status status : statuses) {
                if (diffInfo.status == status) {
                    set.add(diffInfo);
                    break bb;
                }
            }
        }
        return diffInfos;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResultSet<?> resultSet = (ResultSet<?>) o;

        return diffInfos != null ? diffInfos.equals(resultSet.diffInfos) : resultSet.diffInfos == null;

    }

    @Override
    public int hashCode() {
        return diffInfos != null ? diffInfos.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ResultSet{" +
                "diffInfos=" + diffInfos +
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
