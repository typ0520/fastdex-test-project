package com.dx168.fastdex.build.snapshoot.sourceset;

import java.util.Set;

/**
 * Created by tong on 17/3/31.
 */
public class SourceSetsResultSet extends JavaDirectoryResultSet {
    public SourceSetsResultSet() {
    }

    public SourceSetsResultSet(Set<JavaDirectoryResultSet> resultSets) {
        for (JavaDirectoryResultSet resultSet : resultSets) {
            merge(resultSet);
        }
    }
}
