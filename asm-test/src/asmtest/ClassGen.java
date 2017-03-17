package asmtest;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by tong on 17/3/9.
 */
public class ClassGen implements Opcodes {
    public static void main(String[] args) throws Exception {
        /**
         *

         package pkg;
         public interface Comparable extends Mesurable {
         int LESS = -1;
         int EQUAL = 0;
         int GREATER = 1;

         int compareTo(Object o);
         }

         */
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_5, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
                "pkg/Comparable", null, "java/lang/Object",
                new String[] { "pkg/Mesurable" });
        cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "LESS", "I",
                null, new Integer(-1)).visitEnd();
        cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "EQUAL", "I",
                null, new Integer(0)).visitEnd();
        cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "GREATER", "I",
                null, new Integer(1)).visitEnd();
        cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "compareTo",
                "(Ljava/lang/Object;)I", null, null).visitEnd(); cw.visitEnd();
        byte[] b = cw.toByteArray();


        File file = new File("/Users/tong/Desktop/Mesurable.class");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(b);
        fos.close();

    }
}
