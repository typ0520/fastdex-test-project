package com.dx168.fastdex.build.snapshoot.sourceset;

import com.dx168.fastdex.build.snapshoot.api.ResultSet;
import com.dx168.fastdex.build.snapshoot.api.Status;
import com.dx168.fastdex.build.snapshoot.file.FileNode;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tong on 17/3/29.
 */
public class JavaDirectoryResultSet extends ResultSet<JavaFileDiffInfo> {
    private final Set<String> addOrModifiedClassPatterns = new HashSet<>();

    public JavaDirectoryResultSet() {
    }

    public JavaDirectoryResultSet(JavaDirectoryResultSet resultSet) {
        super(resultSet);
    }

    @Override
    public boolean add(JavaFileDiffInfo diffInfo) {
        if (diffInfo.status == Status.ADDED
                || diffInfo.status == Status.MODIFIED) {
            FileNode fileNode = (FileNode) diffInfo.now;
            String classRelativePath = fileNode.relativePath.substring(0, fileNode.relativePath.length() - ".java".length());

            addOrModifiedClassPatterns.add(classRelativePath + ".class");
            addOrModifiedClassPatterns.add(classRelativePath + "\\$\\S{0,}$.class");
        }
        return super.add(diffInfo);
    }

    @Override
    public void merge(ResultSet<JavaFileDiffInfo> resultSet) {
        addOrModifiedClassPatterns.addAll(((JavaDirectoryResultSet)resultSet).addOrModifiedClassPatterns);
        super.merge(resultSet);
    }

    public Set<String> getAddOrModifiedClassPatterns() {
        return addOrModifiedClassPatterns;
    }
}
