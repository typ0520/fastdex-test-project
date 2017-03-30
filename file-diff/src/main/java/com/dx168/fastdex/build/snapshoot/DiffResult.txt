package com.dx168.fastdex.build.snapshoot;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by tong on 17/3/29.
 */
public class DiffResult extends HashSet<DiffInfo> {
    public final Set<SourceSetInfo> deletedSourceSetInfos;
    public final Set<SourceSetInfo> increasedSourceSetInfos;

    public DiffResult(Set<SourceSetInfo> deletedSourceSetInfos, Set<SourceSetInfo> increasedSourceSetInfos) {
        this.deletedSourceSetInfos = deletedSourceSetInfos;
        this.increasedSourceSetInfos = increasedSourceSetInfos;
    }

    /**
     * sourceSet个数是否发生变化
     * @return
     */
    public boolean isSourceSetChanged() {
        return (deletedSourceSetInfos != null && !deletedSourceSetInfos.isEmpty())
                || (increasedSourceSetInfos != null && !increasedSourceSetInfos.isEmpty());
    }

    /**
     * 把删除的sourceSet下的所有文件当成删除处理
     * 把新增的sourceSet下的所有文件当成新增处理
     */
    public void scanFromDeletedAndIncreased() {
        //TODO

        if (deletedSourceSetInfos != null) {

        }
    }
}
