package com.dx168.fastdex.build.snapshoot.string;

import com.dx168.fastdex.build.snapshoot.api.*;
import java.io.IOException;
import java.util.Set;

/**
 * Created by tong on 17/3/31.
 */
public class BaseStringSnapshoot<DIFF_INFO extends StringDiffInfo,NODE extends StringNode> extends Snapshoot<DIFF_INFO,NODE> {

    public BaseStringSnapshoot() {
    }

    public BaseStringSnapshoot(BaseStringSnapshoot snapshoot) {
        super(snapshoot);
    }

    public BaseStringSnapshoot(Set<String> strings) throws IOException {
        for (String str : strings) {
            addNode((NODE) StringNode.create(str));
        }
    }

    public BaseStringSnapshoot(String ...strings) throws IOException {
        for (String str : strings) {
            addNode((NODE) StringNode.create(str));
        }
    }

    @Override
    protected DiffInfo createEmptyDiffInfo() {
        return new StringDiffInfo();
    }

    @Override
    protected void diffNode(ResultSet<DIFF_INFO> diffInfos, Snapshoot<DIFF_INFO, NODE> otherSnapshoot, NODE now, NODE old) {
        addDiffInfo(diffInfos,createDiffInfo(Status.NOCHANGED,now,old));
    }
}
