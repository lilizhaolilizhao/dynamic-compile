package com.oneapm.compiler;

import java.io.*;
import java.util.Map;

public class CompilerTest {
    public static void main(String[] args) {
        //测试单个文件
        String includePath = null;
        String thirdJarPath = "/Users/oneapm/.m2/repository/com/alibaba/fastjson/1.2.30/fastjson-1.2.30.jar";
        String classPath = ".";
        File[] files = new File[] {
                new File("/Users/oneapm/Desktop/HelloTest.java"),
                new File("/Users/oneapm/Desktop/Bean.java"),
        };

        try {
            Compiler compiler = new Compiler(includePath);
            classPath += File.pathSeparator + System.getProperty("java.class.path") + File.pathSeparator + thirdJarPath;
            Map<String, byte[]> classes = compiler.compile(files, new PrintWriter(System.err), ".", classPath);

            classes.forEach((x, y) -> {
                System.out.println(x);
                dump(x, y);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * dump文件
     * @param name
     * @param code
     */
    private static void dump(String name, byte[] code) {
        OutputStream os = null;
        try {
            name = name.replace(".", "_") + ".class";
            File f = new File("/tmp/" + name);
            if (!f.exists()) {
                f.getParentFile().createNewFile();
            }
            os = new FileOutputStream(f);
            os.write(code);
        } catch (IOException e) {
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
