package com.dx168.fastdex.build.snapshoot.sourceset;

import com.dx168.fastdex.build.snapshoot.api.Status;
import com.dx168.fastdex.build.snapshoot.file.DirectoryDiffResult;
import com.dx168.fastdex.build.snapshoot.file.FileDiffInfo;
import com.google.gson.annotations.Expose;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tong on 17/3/29.
 */
public class JavaDirectoryDiffResult extends DirectoryDiffResult {
    @Expose
    public final Set<String> addOrModifiedClassPatterns = new HashSet<>();

    @Override
    public boolean add(FileDiffInfo fileDiffInfo) {
        if (fileDiffInfo.status == Status.ADD
                || fileDiffInfo.status == Status.MODIFIED) {
            String classRelativePath = fileDiffInfo.now.relativePath.substring(0, fileDiffInfo.now.relativePath.length() - ".java".length());

            addOrModifiedClassPatterns.add(classRelativePath + ".class");
            addOrModifiedClassPatterns.add(classRelativePath + "\\$\\S{0,}$.class");
        }
        return super.add(fileDiffInfo);
    }
}
