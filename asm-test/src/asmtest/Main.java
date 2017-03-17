package asmtest;

import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;

public class Main implements Opcodes {

    public static void main(String[] args) throws IOException {
        System.out.println("Hello World!");


        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(V1_1, ACC_PUBLIC, "Example", null, "java/lang/Object", null);

        // creates a MethodWriter for the (implicit) constructor
        MethodVisitor mw = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null,
                null);
        // pushes the 'this' variable
        mw.visitVarInsn(ALOAD, 0);
        // invokes the super class constructor
        mw.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V",
                false);
        mw.visitInsn(RETURN);
        // this code uses a maximum of one stack element and one local variable
        mw.visitMaxs(1, 1);
        mw.visitEnd();

        // creates a MethodWriter for the 'main' method
        mw = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main",
                "([Ljava/lang/String;)V", null, null);
        // pushes the 'out' field (of type PrintStream) of the System class
        mw.visitFieldInsn(GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;");
        // pushes the "Hello World!" String constant
        mw.visitLdcInsn("Hello world!");
        // invokes the 'println' method (defined in the PrintStream class)
        mw.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                "(Ljava/lang/String;)V", false);
        mw.visitInsn(RETURN);
        // this code uses a maximum of two stack elements and two local
        // variables
        mw.visitMaxs(2, 2);
        mw.visitEnd();

        // gets the bytecode of the Example class, and loads it dynamically
        byte[] code = cw.toByteArray();

        ClassReader classReader = new ClassReader(code);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new MyClassVisitor(classWriter);
        classReader.accept(classVisitor, Opcodes.ASM5);

        FileOutputStream fos = new FileOutputStream("Example.class");
        fos.write(classWriter.toByteArray());
        fos.close();
    }

    private static class MyClassVisitor extends ClassVisitor {
        public MyClassVisitor(ClassVisitor classVisitor) {
            super(ASM5, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod( int access,
                                          String name,
                                          String desc,
                                          String signature,
                                          String[] exceptions) {
            if ("<init>".equals(name)) {
                MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);//先得到原始的方法

                System.out.println(name + " | " + desc + " | " + signature);

                MethodVisitor newMethod = null;
                newMethod = new AsmMethodVisit(mv); //访问需要修改的方法
                return newMethod;
            }
            else {
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        }
    }


    static  class AsmMethodVisit extends MethodVisitor {

        public AsmMethodVisit(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            super.visitMethodInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitCode() {
            //此方法在访问方法的头部时被访问到，仅被访问一次
            //此处可插入新的指令
            super.visitCode();
        }

        @Override
        public void visitInsn(int opcode) {
            //此方法可以获取方法中每一条指令的操作类型，被访问多次
            //如应在方法结尾处添加新指令，则应判断：
            if(opcode == Opcodes.RETURN)
            {
                // pushes the 'out' field (of type PrintStream) of the System class
                mv.visitFieldInsn(GETSTATIC,
                        "java/lang/System",
                        "out",
                        "Ljava/io/PrintStream;");
                // pushes the "Hello World!" String constant
                mv.visitLdcInsn("this is a modify method!");
                // invokes the 'println' method (defined in the PrintStream class)
                mv.visitMethodInsn(INVOKEVIRTUAL,
                        "java/io/PrintStream",
                        "println",
                        "(Ljava/lang/String;)V");
//                mv.visitInsn(RETURN);
            }
            super.visitInsn(opcode);
        }
    }
}
