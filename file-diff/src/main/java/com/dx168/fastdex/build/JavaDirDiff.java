package com.dx168.fastdex.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tong on 17/3/10.
 */
public class JavaDirDiff {
    public static List<String> diff(File newDir, File oldDir, boolean useRelativePath) throws IOException {
        if (newDir == null || oldDir == null) {
            throw new RuntimeException("newDir == null || oldDir == null");
        }
        if (!newDir.isDirectory()) {
            throw new RuntimeException(newDir + " is not dir");
        }
        if (!oldDir.isDirectory()) {
            throw new RuntimeException(oldDir + " is not dir");
        }

        List<String> result = new ArrayList<>();
        Files.walkFileTree(newDir.toPath(),new CompareFileVisitor(newDir.toPath(),oldDir.toPath(),result,useRelativePath));
        return result;
    }

    private static final class CompareFileVisitor extends SimpleFileVisitor<Path> {
        private final Path newDir;
        private final Path oldDir;
        private final List<String> result;
        private final boolean useRelativePath;

        public CompareFileVisitor(Path newDir, Path oldDir, List<String> result,boolean useRelativePath) {
            this.newDir = newDir;
            this.oldDir = oldDir;
            this.result = result;
            this.useRelativePath = useRelativePath;
        }

        @Override
        public FileVisitResult visitFile(Path newPath, BasicFileAttributes attrs) throws IOException {
            if (!newPath.toFile().getName().endsWith(".java")) {
                return FileVisitResult.CONTINUE;
            }
            Path relativePath = newDir.relativize(newPath);
            Path oldPath = oldDir.resolve(relativePath);

            File newFile = newPath.toFile();
            File oldFile = oldPath.toFile();

            if (!oldFile.exists()) {
                result.add(useRelativePath ? relativePath.toString() : newFile.getAbsolutePath());
            }
            else if ((newFile.lastModified() != oldFile.lastModified())
                    || (newFile.length() != oldFile.length())) {
                result.add(useRelativePath ? relativePath.toString() : newFile.getAbsolutePath());
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
