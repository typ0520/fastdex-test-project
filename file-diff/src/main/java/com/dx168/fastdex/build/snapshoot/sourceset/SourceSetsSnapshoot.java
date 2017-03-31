package com.dx168.fastdex.build.snapshoot.sourceset;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tong on 17/3/31.
 */
public class SourceSetsSnapshoot {
    private Set<JavaDirectorySnapshoot> directorySnapshootSet = new HashSet<>();

    public SourceSetsSnapshoot(Set<File> sourceSets) {
        for (File sourceSet : sourceSets) {
            try {
                directorySnapshootSet.add(new JavaDirectorySnapshoot(sourceSet));
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public SourceSetsResultSet diff(SourceSetsSnapshoot snapshoot) {
        SourceSetsResultSet resultSet = new SourceSetsResultSet();

        return null;
    }
}
