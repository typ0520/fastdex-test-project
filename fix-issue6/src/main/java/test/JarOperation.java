package test;

import java.io.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by tong on 17/3/23.
 */
public class JarOperation {
    public static void main(String[] args) throws IOException {
        File combinedJar = new File("/Users/tong/Projects/fastdex-test-project/fix-issue6/combined.jar");
        File transformedJar = new File("/Users/tong/Projects/fastdex-test-project/fix-issue6/transformed-combined.jar");

        Set<String> includePatterns = new HashSet<String>();
        includePatterns.add("com/baidu/platform/comapi/map/a.class");
        includePatterns.add("com/baidu/platform/comapi/map/A.class");
        includePatterns.add("com/baidu/platform/comapi/map/MapController.class");
        includePatterns.add("com/baidu/platform/comapi/map/MapController\\$\\S{0,}.class");

        transformNormalJar(combinedJar,transformedJar,includePatterns);
    }

    /**
     * 转换全量打包的jar包，往所有的项目代码中注入代码
     * @param inputJar                  输入jar
     * @param outputJar                 补丁jar输出位置
     * @param willInjectClassPatterns   所有项目代码的class路径正则列表
     *
     * 例如:
     * com/dx168/fastdex/sample/MainActivity.class
     * com/dx168/fastdex/sample/MainActivity\\$\S{0,}.class
     * @throws IOException
     */
    public static void transformNormalJar(File inputJar, File outputJar, Set<String> willInjectClassPatterns) throws IOException {
        transformJar(inputJar,outputJar,new NormalProcessor(willInjectClassPatterns));
    }

    /**
     * 转换补丁jar包，从jar中移除没有变化的class
     * @param inputJar              输入jar
     * @param outputJar             补丁jar输出位置
     * @param changedClassPatterns  所有变化的class路径正则列表
     *
     * 例如:
     * com/dx168/fastdex/sample/MainActivity.class
     * com/dx168/fastdex/sample/MainActivity\\$\S{0,}.class
     * @throws IOException
     */
    public static void transformPatchJar(File inputJar, File outputJar, Set<String> changedClassPatterns) throws IOException {
        transformJar(inputJar,outputJar,new PatchProcessor(changedClassPatterns));
    }

    /**
     * 转换jar包
     * @param inputJar      输入jar
     * @param outputJar     输出jar的路径
     * @param processor     处理器
     * @throws IOException
     */
    private static void transformJar(File inputJar, File outputJar, Processor processor) throws IOException {
        if (outputJar.exists()) {
            outputJar.delete();
        }

        ZipOutputStream outputJarStream = new ZipOutputStream(new FileOutputStream(outputJar));
        ZipFile zipFile = new ZipFile(inputJar);
        Enumeration enumeration = zipFile.entries();
        try {
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                processor.process(zipFile,entry,outputJarStream);
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
            if (outputJarStream != null) {
                outputJarStream.close();
            }
        }
    }

    private static class PatchProcessor extends NormalProcessor {
        public PatchProcessor(Set<String> changedClassPatterns) {
            super(changedClassPatterns);
        }

        @Override
        public void process(ZipFile zipFile, ZipEntry entry, ZipOutputStream outputJarStream) throws IOException {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(entry.getName()).matches()) {
                    outputJarStream.putNextEntry(new ZipEntry(entry.getName()));
                    System.out.println("==entry: " + entry.getName());

                    byte[] classBytes = readStream(zipFile.getInputStream(entry));
                    outputJarStream.write(classBytes);
                    outputJarStream.closeEntry();
                    break;
                }
            }
        }
    }

    private static class NormalProcessor extends Processor {
        protected Set<Pattern> patterns;

        public NormalProcessor(Set<String> willInjectClassPatterns) {
            patterns = new HashSet<Pattern>();
            if (willInjectClassPatterns != null && !willInjectClassPatterns.isEmpty()) {
                for (String patternStr : willInjectClassPatterns) {
                    patterns.add(Pattern.compile(patternStr));
                }
            }
        }

        @Override
        public void process(ZipFile zipFile,ZipEntry entry, ZipOutputStream outputJarStream) throws IOException {
            outputJarStream.putNextEntry(new ZipEntry(entry.getName()));
            System.out.println("==entry: " + entry.getName());
            byte[] classBytes = readStream(zipFile.getInputStream(entry));
            if (patterns != null) {
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(entry.getName()).matches()) {
                        //TODO inject  classBytes = inject(classBytes)
                        System.out.println("==注入代码: " + entry.getName());
                        break;
                    }
                }
            }
            outputJarStream.write(classBytes);
            outputJarStream.closeEntry();
        }
    }

    private static abstract class Processor {
        public abstract void process(ZipFile zipFile,ZipEntry entry,ZipOutputStream outputJarStream) throws IOException;

        protected byte[] readStream(InputStream is) throws IOException {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final int bufferSize = 1024;
            try {
                final BufferedInputStream bIn = new BufferedInputStream(is);
                int length;
                byte[] buffer = new byte[bufferSize];
                byte[] bufferCopy;
                while ((length = bIn.read(buffer, 0, bufferSize)) != -1) {
                    bufferCopy = new byte[length];
                    System.arraycopy(buffer, 0, bufferCopy, 0, length);
                    output.write(bufferCopy);
                }
                bIn.close();
            } finally {
                output.close();
            }
            return output.toByteArray();
        }
    }
}
