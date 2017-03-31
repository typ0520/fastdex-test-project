package com.dx168.fastdex.build.snapshoot.string;

import com.dx168.fastdex.build.snapshoot.api.DiffInfo;
import com.dx168.fastdex.build.snapshoot.api.Node;
import com.dx168.fastdex.build.snapshoot.api.Snapshoot;
import java.io.IOException;
import java.util.Set;

/**
 * Created by tong on 17/3/31.
 */
public class BaseStringSnapshoot<DIFF_INFO extends StringDiffInfo,NODE extends StringNode> extends Snapshoot<DIFF_INFO,Node> {

    public BaseStringSnapshoot() {
    }

    public BaseStringSnapshoot(BaseStringSnapshoot snapshoot) {
        super(snapshoot);
    }

    public BaseStringSnapshoot(Set<String> strings) throws IOException {
        for (String str : strings) {
            addNode(StringNode.create(str));
        }
    }

    public BaseStringSnapshoot(String ...strings) throws IOException {
        for (String str : strings) {
            addNode(StringNode.create(str));
        }
    }

    @Override
    protected DiffInfo createEmptyDiffInfo() {
        return new StringDiffInfo();
    }
}
