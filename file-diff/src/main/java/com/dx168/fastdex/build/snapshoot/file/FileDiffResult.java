package com.dx168.fastdex.build.snapshoot.file;

import com.dx168.fastdex.build.snapshoot.api.STSerializable;
import com.dx168.fastdex.build.snapshoot.utils.SerializeUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;

/**
 * Created by tong on 17/3/29.
 */
public class FileDiffResult extends HashSet<FileDiffInfo> implements STSerializable<FileDiffResult> {
    @Override
    public FileDiffResult load(InputStream inputStream) throws IOException {
        return SerializeUtils.load(inputStream,getClass());
    }

    @Override
    public void serializeTo(OutputStream outputStream) throws IOException {
        SerializeUtils.serializeTo(outputStream,this);
    }
}
