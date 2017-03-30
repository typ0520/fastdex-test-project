package com.dx168.fastdex.build.snapshoot.sourceset;

import com.dx168.fastdex.build.snapshoot.api.Snapshoot;
import com.dx168.fastdex.build.snapshoot.file.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Created by tong on 17/3/30.
 */
public class JavaDirectorySnapshoot extends DirectorySnapshoot {
    private static final FileSuffixFilter JAVA_SUFFIX_FILTER = new FileSuffixFilter(".java");

    public JavaDirectorySnapshoot() {
    }

    public JavaDirectorySnapshoot(File directory) throws IOException {
        super(directory, JAVA_SUFFIX_FILTER);
    }

    @Override
    protected Collection<FileDiffInfo> createEmptyDiffInfos() {
        return (Collection<FileDiffInfo>) new JavaDirectoryDiffResult();
    }

    @Override
    public DirectoryDiffResult diff(File old, ScanFilter scanFilter) throws IOException {
        return super.diff(old, JAVA_SUFFIX_FILTER);
    }

    @Override
    public DirectoryDiffResult diff(Snapshoot<FileDiffInfo, FileItemInfo> otherSnapshoot) {
        return super.diff(otherSnapshoot);
    }
}
