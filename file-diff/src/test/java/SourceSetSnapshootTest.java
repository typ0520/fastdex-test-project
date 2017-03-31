import com.dx168.fastdex.build.snapshoot.api.Snapshoot;
import com.dx168.fastdex.build.snapshoot.api.Status;
import com.dx168.fastdex.build.snapshoot.sourceset.SourceSetResultSet;
import com.dx168.fastdex.build.snapshoot.sourceset.SourceSetSnapshoot;
import junit.framework.TestCase;
import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Created by tong on 17/3/31.
 */
public class SourceSetSnapshootTest extends TestCase {
    String workDir;
    String source_set1;
    String source_set2;
    String source_set11;
    String source_set22;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        File currentPath = new File(this.getClass().getResource("/").getPath());
        System.out.println(currentPath);

        workDir = "/Users/tong/Desktop/sourceSetTest";
        source_set1 = workDir + File.separator + "source_set1";
        source_set2 = workDir + File.separator + "source_set2";
        source_set11 = workDir + File.separator + "source_set11";
        source_set22 = workDir + File.separator + "source_set22";
    }

    @Test
    public void testCreate() throws Throwable {
        if (!isDir(source_set1) || !isDir(source_set2) || !isDir(source_set11) || !isDir(source_set22)) {
            System.err.println("Test-env not init!!");
        }

        SourceSetSnapshoot snapshoot = new SourceSetSnapshoot(new File(workDir),source_set1,source_set2);
        assertEquals(snapshoot.directorySnapshootSet.size(),2);
        SourceSetSnapshoot snapshoot2 = new SourceSetSnapshoot(new File(workDir),source_set1,source_set1);
        assertEquals(snapshoot2.directorySnapshootSet.size(),1);
    }

    @Test
    public void testDiffAddOneSourceSet() throws Throwable {
        if (!isDir(source_set1) || !isDir(source_set2) || !isDir(source_set11) || !isDir(source_set22)) {
            System.err.println("Test-env not init!!");
        }
        SourceSetSnapshoot now = new SourceSetSnapshoot(new File(workDir),source_set1,source_set2);
        SourceSetSnapshoot old = new SourceSetSnapshoot(new File(workDir),source_set1);

        SourceSetResultSet sourceSetResultSet = (SourceSetResultSet) now.diff(old);
        assertTrue(sourceSetResultSet.isSourceSetChanged());


        System.out.println(sourceSetResultSet);
    }

    @Test
    public void testSave() throws Throwable {
        if (!isDir(source_set1) || !isDir(source_set2) || !isDir(source_set11) || !isDir(source_set22)) {
            System.err.println("Test-env not init!!");
        }
        SourceSetSnapshoot now = new SourceSetSnapshoot(new File(workDir),source_set1,source_set2);
        now.serializeTo(new FileOutputStream(new File(workDir,"now.json")));
    }

    @Test
    public void testDiff1() throws Throwable {
        if (!isDir(source_set1) || !isDir(source_set2) || !isDir(source_set11) || !isDir(source_set22)) {
            System.err.println("Test-env not init!!");
        }

        SourceSetSnapshoot now = new SourceSetSnapshoot(new File(workDir),source_set1);
        SourceSetSnapshoot old = new SourceSetSnapshoot(new File(workDir),source_set11);

        SourceSetResultSet sourceSetResultSet = (SourceSetResultSet) now.diff(old);
        sourceSetResultSet.serializeTo(new FileOutputStream(new File(workDir,"diff.json")));
    }

    @Test
    public void testDiff2() throws Throwable {
        if (!isDir(source_set1) || !isDir(source_set2) || !isDir(source_set11) || !isDir(source_set22)) {
            System.err.println("Test-env not init!!");
        }

        SourceSetSnapshoot now = new SourceSetSnapshoot(new File(workDir),source_set1);
        SourceSetSnapshoot old = new SourceSetSnapshoot(new File(workDir),source_set1);

        now.serializeTo(new FileOutputStream(new File(workDir,"now.json")));

        SourceSetResultSet sourceSetResultSet = (SourceSetResultSet) now.diff(old);
        SourceSetResultSet sourceSetResultSet2 = (SourceSetResultSet) now.diff(old);
        assertTrue(sourceSetResultSet.equals(sourceSetResultSet2));

        SourceSetSnapshoot now1 = (SourceSetSnapshoot) Snapshoot.load(new FileInputStream(new File(workDir,"now.json")),SourceSetSnapshoot.class);
        SourceSetResultSet sourceSetResultSet3 = (SourceSetResultSet) now1.diff(old);
        assertTrue(sourceSetResultSet.equals(sourceSetResultSet3));



        //now.serializeTo(new FileOutputStream(new File(workDir,"now.json")));

        //SourceSetSnapshoot old = new SourceSetSnapshoot(new File(workDir),source_set11);

//        SourceSetResultSet sourceSetResultSet = (SourceSetResultSet) now.diff(old);
//        sourceSetResultSet.serializeTo(new FileOutputStream(new File(workDir,"diff.json")));
    }

    @Test
    public void testDiff3() throws Throwable {
        if (!isDir(source_set1) || !isDir(source_set2) || !isDir(source_set11) || !isDir(source_set22)) {
            System.err.println("Test-env not init!!");
        }

        SourceSetSnapshoot now = new SourceSetSnapshoot(new File(workDir),source_set1);
        SourceSetSnapshoot old = new SourceSetSnapshoot(new File(workDir),source_set1);

        SourceSetResultSet sourceSetResultSet = (SourceSetResultSet) now.diff(old);
        SourceSetResultSet sourceSetResultSet2 = (SourceSetResultSet) now.diff(old);
        assertTrue(sourceSetResultSet.equals(sourceSetResultSet2));

        SourceSetSnapshoot now1 = (SourceSetSnapshoot) Snapshoot.load(new FileInputStream(new File(workDir,"now.json")),SourceSetSnapshoot.class);
        SourceSetResultSet sourceSetResultSet3 = (SourceSetResultSet) now1.diff(old);
        assertTrue(sourceSetResultSet.equals(sourceSetResultSet3));
    }

    public boolean isDir(File dir) {
        if (dir == null) {
            return false;
        }

        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        return true;
    }

    public boolean isDir(String dir) {
        if (dir == null) {
            return false;
        }
        return isDir(new File(dir));
    }
}
