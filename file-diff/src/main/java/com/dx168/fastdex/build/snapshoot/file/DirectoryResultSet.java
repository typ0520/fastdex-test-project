package com.dx168.fastdex.build.snapshoot.file;

import com.dx168.fastdex.build.snapshoot.api.ResultSet;

/**
 * Created by tong on 17/3/29.
 */
public class DirectoryResultSet extends ResultSet<FileDiffInfo> {
    public DirectoryResultSet() {
    }

    public DirectoryResultSet(DirectoryResultSet resultSet) {
        super(resultSet);
    }
}
