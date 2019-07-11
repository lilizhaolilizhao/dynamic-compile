package com.oneapm.compiler;

import com.sun.source.util.JavacTask;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class CompilerHelper {
    private final boolean generatePack;

    private final JavaCompiler compiler;

    CompilerHelper(JavaCompiler compiler, boolean generatePack) {
        this.compiler = compiler;
        this.generatePack = generatePack;
    }

    Map<String, byte[]> compile(MemoryJavaFileManager manager,
                                Iterable<? extends JavaFileObject> compUnits,
                                Writer err, String sourcePath, final String classPath) {
        // to collect errors, warnings etc.
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();

        // javac options
        List<String> options = new ArrayList<>();
        options.add("-Xlint:all");
        options.add("-g:lines");
        options.add("-deprecation");
        options.add("-source");
        options.add("1.7");
        options.add("-target");
        options.add("1.7");
        if (sourcePath != null) {
            options.add("-sourcepath");
            options.add(sourcePath);
        }

        if (classPath != null) {
            options.add("-classpath");
            options.add(classPath);
        }

        // create a compilation task
        Map<String, byte[]> result;
        try {
            JavacTask task = (JavacTask) compiler.getTask(err, manager, diagnostics, options, null, compUnits);
            final PrintWriter perr = (err instanceof PrintWriter) ? (PrintWriter) err : new PrintWriter(err);

            // print dignostics messages in case of failures.
            if (task.call() == false || containsErrors(diagnostics)) {
                for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                    printDiagnostic(diagnostic, perr);
                }
                perr.flush();
                return null;
            }

            // collect .class bytes of all compiled classes
            result = manager.getClassBytes();
        } finally {
            try {
                manager.close();
            } catch (IOException exp) {
            }
        }
        return result;
    }

    private void printDiagnostic(Diagnostic diagnostic, final PrintWriter perr) {
        perr.println(diagnostic);
    }

    /**
     * Checks if the list of diagnostic messages contains at least one error. Certain
     * {@link JavacTask} implementations may return success error code even though errors were
     * reported.
     */
    private boolean containsErrors(DiagnosticCollector<?> diagnostics) {
        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Kind.ERROR) {
                return true;
            }
        }
        return false;
    }
}