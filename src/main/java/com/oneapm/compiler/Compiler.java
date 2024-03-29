package com.oneapm.compiler;

import com.oneapm.util.Messages;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Compiler {
    private final CompilerHelper compilerHelper;
    // null means no preprocessing isf done.
    public List<String> includeDirs;
    private StandardJavaFileManager stdManager;
    private String packExtension = "class";

    public Compiler(String includePath, boolean generatePack) {
        if (includePath != null) {
            includeDirs = new ArrayList<>();
            String[] paths = includePath.split(File.pathSeparator);
            includeDirs.addAll(Arrays.asList(paths));
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        this.stdManager = compiler.getStandardFileManager(null, null, null);
        compilerHelper = new CompilerHelper(compiler, generatePack);
    }

    public Compiler(String includePath) {
        this(includePath, false);
    }

    public Compiler(boolean generatePack) {
        this(null, generatePack);
    }

    public Compiler() {
        this(null);
    }

    private static void usage(String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    private static void usage() {
        usage(Messages.get("btracec.usage"));
    }

    // simple test main
    @SuppressWarnings("DefaultCharset")
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
        }

        String classPath = ".";
        String outputDir = ".";
        String includePath = null;
        boolean trusted = false;
        boolean generatePack = true;
        String packExtension = null;
        int count = 0;
        boolean classPathDefined = false;
        boolean outputDirDefined = false;
        boolean includePathDefined = false;
        boolean trustedDefined = false;

        for (; ; ) {
            if (args[count].charAt(0) == '-') {
                if (args.length <= count + 1) {
                    usage();
                }
                if ((args[count].equals("-cp") ||
                        args[count].equals("-classpath")) && !classPathDefined) {
                    classPath = args[++count];
                    classPathDefined = true;
                } else if (args[count].equals("-d") && !outputDirDefined) {
                    outputDir = args[++count];
                    outputDirDefined = true;
                } else if (args[count].equals("-I") && !includePathDefined) {
                    includePath = args[++count];
                    includePathDefined = true;
                } else if ((args[count].equals("-unsafe") || args[count].equals("-trusted")) && !trustedDefined) {
                    trusted = true;
                    trustedDefined = true;
                } else if (args[count].equals("-nopack")) {
                    generatePack = false;
                } else if (args[count].equals("-packext")) {
                    packExtension = args[++count];
                } else {
                    usage();
                }
                count++;
                if (count >= args.length) {
                    break;
                }
            } else {
                break;
            }
        }

        if (args.length <= count) {
            usage();
        }

        if (!generatePack && packExtension != null) {
            usage("Can not specify pack extension if not using packs (-nopack)");
        }

        File[] files = new File[args.length - count];
        for (int i = 0; i < files.length; i++) {
            files[i] = new File(args[i + count]);
            if (!files[i].exists()) {
                usage("File not found: " + files[i]);
            }
        }

        Compiler compiler = new Compiler(includePath, generatePack);
        classPath += File.pathSeparator + System.getProperty("java.class.path");
        Map<String, byte[]> classes = compiler.compile(files,
                new PrintWriter(System.err), ".", classPath);
        if (classes != null) {
            // write .class files.
            for (Map.Entry<String, byte[]> c : classes.entrySet()) {
                String name = c.getKey().replace(".", File.separator);
                int index = name.lastIndexOf(File.separatorChar);
                String dir = outputDir + File.separator;
                if (index != -1) {
                    dir += name.substring(0, index);
                }
                new File(dir).mkdirs();
                String file;
                if (index != -1) {
                    file = name.substring(index + 1);
                } else {
                    file = name;
                }
                file += "." + (packExtension != null ? packExtension : "class");
                File out = new File(dir, file);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(c.getValue());
                }
            }
        } else {
            // fail
            System.exit(1);
        }
    }

    public Map<String, byte[]> compile(String fileName, String source,
                                       Writer err, String sourcePath, String classPath) {
        // create a new memory JavaFileManager
        MemoryJavaFileManager manager = new MemoryJavaFileManager(stdManager, includeDirs);

        // prepare the compilation unit
        List<JavaFileObject> compUnits = new ArrayList<>(1);
        compUnits.add(MemoryJavaFileManager.makeStringSource(fileName, source, includeDirs));
        return compile(manager, compUnits, err, sourcePath, classPath);
    }

    public Map<String, byte[]> compile(File file,
                                       Writer err, String sourcePath, String classPath) {
        File[] files = new File[1];
        files[0] = file;
        return compile(files, err, sourcePath, classPath);
    }

    public Map<String, byte[]> compile(File[] files,
                                       Writer err, String sourcePath, String classPath) {
        Iterable<? extends JavaFileObject> compUnits =
                stdManager.getJavaFileObjects(files);
        List<JavaFileObject> preprocessedCompUnits = new ArrayList<>();
        try {
            for (JavaFileObject jfo : compUnits) {
                preprocessedCompUnits.add(MemoryJavaFileManager.preprocessedFileObject(jfo, includeDirs));
            }
        } catch (IOException ioExp) {
            throw new RuntimeException(ioExp);
        }
        return compile(preprocessedCompUnits, err, sourcePath, classPath);
    }

    public Map<String, byte[]> compile(
            Iterable<? extends JavaFileObject> compUnits,
            Writer err, String sourcePath, String classPath) {
        // create a new memory JavaFileManager
        MemoryJavaFileManager manager = new MemoryJavaFileManager(stdManager, includeDirs);
        return compilerHelper.compile(manager, compUnits, err, sourcePath, classPath);
    }

    private Map<String, byte[]> compile(MemoryJavaFileManager manager,
                                        Iterable<? extends JavaFileObject> compUnits,
                                        Writer err, String sourcePath, final String classPath) {
        return compilerHelper.compile(manager, compUnits, err, sourcePath, classPath);
    }
}
