package com.dx168.fastdex.build.snapshoot.string;

import java.io.IOException;
import java.util.Set;

/**
 * Created by tong on 17/3/31.
 */
public class StringSnapshoot extends BaseStringSnapshoot<StringDiffInfo,StringNode> {
    public StringSnapshoot() {
    }

    public StringSnapshoot(BaseStringSnapshoot snapshoot) {
        super(snapshoot);
    }

    public StringSnapshoot(Set<String> strings) throws IOException {
        super(strings);
    }

    public StringSnapshoot(String... strings) throws IOException {
        super(strings);
    }
}
