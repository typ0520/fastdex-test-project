package com.dx168.fastdex.build.snapshoot.diff;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Created by tong on 17/3/29.
 */
public interface Snapshoot<DIFF_INFO extends BaseDiffInfo,ITEM_INFO extends ItemInfo> {
    void addItemInfo(ITEM_INFO itemInfo);

    Collection<ITEM_INFO> getAllItemInfo();

    ITEM_INFO getItemInfoByUniqueKey(String uniqueKey);

    Snapshoot load(InputStream inputStream);

    void serializeTo(OutputStream outputStream) throws IOException;

    Collection<DIFF_INFO> diff(Snapshoot<DIFF_INFO,ITEM_INFO> old);
}
