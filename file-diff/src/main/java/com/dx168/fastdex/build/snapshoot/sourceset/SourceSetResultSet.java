package com.dx168.fastdex.build.snapshoot.sourceset;

import com.dx168.fastdex.build.snapshoot.api.ResultSet;
import com.dx168.fastdex.build.snapshoot.api.Status;
import com.dx168.fastdex.build.snapshoot.string.StringDiffInfo;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tong on 17/3/31.
 */
public class SourceSetResultSet extends ResultSet<StringDiffInfo> {
    private final Set<String> addOrModifiedClassPatterns = new HashSet<>();

    public Set<JavaFileDiffInfo> changedJavaFileDiffInfos = new HashSet<JavaFileDiffInfo>();

    public SourceSetResultSet() {

    }

    public boolean isSourceSetChanged() {
        return !getDiffInfos(Status.ADDED,Status.DELETEED).isEmpty();
    }

    public void addJavaFileDiffInfo(JavaFileDiffInfo diffInfo) {
        if (diffInfo.status != Status.NOCHANGED) {
            this.changedJavaFileDiffInfos.add(diffInfo);
        }
    }

    public void mergeJavaDirectoryResultSet(JavaDirectoryResultSet javaDirectoryResultSet) {
        this.addOrModifiedClassPatterns.addAll(javaDirectoryResultSet.getAddOrModifiedClassPatterns());
        this.changedJavaFileDiffInfos.addAll(javaDirectoryResultSet.changedDiffInfos);
    }

    public Set<JavaFileDiffInfo> getJavaFileDiffInfos(Status ...statuses) {
        Set<JavaFileDiffInfo> result = new HashSet<JavaFileDiffInfo>();
        for (JavaFileDiffInfo diffInfo : changedJavaFileDiffInfos) {
            bb : for (Status status : statuses) {
                if (diffInfo.status == status) {
                    result.add(diffInfo);
                    break bb;
                }
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SourceSetResultSet that = (SourceSetResultSet) o;

        return changedJavaFileDiffInfos != null ? changedJavaFileDiffInfos.equals(that.changedJavaFileDiffInfos) : that.changedJavaFileDiffInfos == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (changedJavaFileDiffInfos != null ? changedJavaFileDiffInfos.hashCode() : 0);
        return result;
    }
}
