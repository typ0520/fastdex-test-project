import com.dx168.fastdex.build.snapshoot.api.DiffInfo;
import com.dx168.fastdex.build.snapshoot.api.Node;
import com.dx168.fastdex.build.snapshoot.api.ResultSet;
import com.dx168.fastdex.build.snapshoot.api.Status;
import com.dx168.fastdex.build.snapshoot.string.StringDiffInfo;
import com.dx168.fastdex.build.snapshoot.string.StringSnapshoot;
import junit.framework.TestCase;
import org.junit.Test;
import java.util.*;

/**
 * Created by tong on 17/3/31.
 */
public class StringSnapshootTest extends TestCase {
    @Test
    public void testCreate() throws Throwable {
        Set<String> nodeSet = new HashSet<>();
        nodeSet.add("a");
        nodeSet.add("b");
        nodeSet.add("c");

        StringSnapshoot snapshoot1 = new StringSnapshoot(nodeSet);
        Collection<Node> collection = (Collection) snapshoot1.nodes;
        assertEquals(collection.size(),nodeSet.size());
        Set<String> strings = new HashSet<>();

        for (Node node : collection) {
            strings.add(node.getUniqueKey());
        }

        assertEquals(strings,nodeSet);
    }

    @Test
    public void testEqual() throws Throwable {
        Set<String> nodeSet = new HashSet<>();
        nodeSet.add("a");
        nodeSet.add("b");
        nodeSet.add("c");


        StringSnapshoot snapshoot1 = new StringSnapshoot(nodeSet);
        StringSnapshoot snapshoot2 = new StringSnapshoot(nodeSet);

        ResultSet resultSet = snapshoot1.diff(snapshoot2);

        assertTrue(resultSet.getAllChangedDiffInfos() == null || resultSet.getAllChangedDiffInfos().isEmpty());
    }

    @Test
    public void testAdd() throws Throwable {
        Set<String> nodeSet = new HashSet<>();
        nodeSet.add("a");
        nodeSet.add("b");
        nodeSet.add("c");
        StringSnapshoot now = new StringSnapshoot(nodeSet);

        Set<String> nodeSet2 = new HashSet<>();
        nodeSet2.add("a");
        nodeSet2.add("b");
        StringSnapshoot old = new StringSnapshoot(nodeSet2);

        ResultSet<StringDiffInfo> resultSet = now.diff(old);

        assertEquals(resultSet.getAllChangedDiffInfos().size(),1);


        ArrayList<DiffInfo> ss = new ArrayList<>();
        ss.addAll(resultSet.getAllChangedDiffInfos());
        DiffInfo diffInfo = ss.get(0);

        assertEquals(diffInfo.status, Status.ADDED);
        assertEquals(diffInfo.uniqueKey, "c");
    }

    @Test
    public void testDelete() throws Throwable {
        Set<String> nodeSet = new HashSet<>();
        nodeSet.add("a");
        nodeSet.add("b");

        StringSnapshoot now = new StringSnapshoot(nodeSet);

        Set<String> nodeSet2 = new HashSet<>();
        nodeSet2.add("a");
        nodeSet2.add("b");
        nodeSet2.add("c");
        StringSnapshoot old = new StringSnapshoot(nodeSet2);

        ResultSet<StringDiffInfo> resultSet = now.diff(old);

        assertEquals(resultSet.getAllChangedDiffInfos().size(),1);


        ArrayList<DiffInfo> ss = new ArrayList<>();
        ss.addAll(resultSet.getAllChangedDiffInfos());
        DiffInfo diffInfo = ss.get(0);

        assertEquals(diffInfo.status, Status.DELETEED);
        assertEquals(diffInfo.uniqueKey, "c");
    }
}
