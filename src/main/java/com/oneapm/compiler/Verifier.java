package com.oneapm.compiler;

import com.oneapm.util.Messages;
import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An annotation processor that validates a BTrace program.
 * Safety rules (such as no loops, no new/throw etc.) are
 * enforced. This uses javac's Tree API in addition to JSR 269.
 *
 * @author A. Sundararajan
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class Verifier extends AbstractProcessor
                            implements TaskListener {
    private final List<String> classNames = new ArrayList<>();
    private Trees treeUtils;
    private final List<CompilationUnitTree> compUnits = new ArrayList<>();
    private ClassTree currentClass;
    private final AttributionTaskListener listener = new AttributionTaskListener();

    @Override
    public synchronized void init(ProcessingEnvironment pe) {
        super.init(pe);
        treeUtils = Trees.instance(pe);
        prepareContext(((JavacProcessingEnvironment)pe).getContext());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        return true;
    }

    @Override
    public void started(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.ENTER) {
            CompilationUnitTree ct = e.getCompilationUnit();
            if (ct != null) {
                compUnits.add(ct);
            }
        }
    }

    @Override
    public void finished(TaskEvent e) {
        if (e.getKind() != TaskEvent.Kind.ANALYZE) return;
        if (processingEnv == null) {
            return;
        }
        TypeElement elem = e.getTypeElement();
        for(Tree t : e.getCompilationUnit().getTypeDecls()) {
            if (t.getKind() == Tree.Kind.CLASS) {
                if (((JCClassDecl)t).sym.equals(elem)) {
                    currentClass = (ClassTree)t;
                    break;
                }
            }
        }
        if (currentClass != null) {
            verify(currentClass, elem);
        }
    }

    List<String> getClassNames() {
        return classNames;
    }

    CompilationUnitTree getCompilationUnit() {
        for (CompilationUnitTree ct : compUnits) {
            for (Tree clazz : ct.getTypeDecls()) {
                if (clazz.equals(currentClass)) {
                    return ct;
                }
            }
        }
        return null;
    }

    Trees getTreeUtils() {
        return treeUtils;
    }

    SourcePositions getSourcePositions() {
        return treeUtils.getSourcePositions();
    }

    ProcessingEnvironment getProcessingEnvironment() {
        return processingEnv;
    }

    Messager getMessager() {
        return processingEnv.getMessager();
    }

    Elements getElementUtils() {
        return processingEnv.getElementUtils();
    }

    Types getTypeUtils() {
        return processingEnv.getTypeUtils();
    }

    Locale getLocale() {
        return processingEnv.getLocale();
    }

    // verify each BTrace class
    private boolean verify(ClassTree ct, Element topElement) {
        currentClass = ct;
        CompilationUnitTree cut = getCompilationUnit();
        String className = ct.getSimpleName().toString();
        ExpressionTree pkgName = cut.getPackageName();
        if (pkgName != null) {
            className = pkgName + "." + className;
        }
        classNames.add(className);
        if (hasTrustedAnnotation(ct, topElement)) {
            return true;
        }
        Boolean value = ct.accept(new VerifierVisitor(this, topElement), null);
        return value == null? true : value;
    }

    /** Detects if the class is annotated as @BTrace(trusted=true). */
    private boolean hasTrustedAnnotation(ClassTree ct, Element topElement) {
        for (AnnotationTree at : ct.getModifiers().getAnnotations()) {
            String annFqn = ((JCTree)at.getAnnotationType()).type.tsym.getQualifiedName().toString();
//            if (!annFqn.equals(BTrace.class.getName())) {
//                continue;
//            }
            // now we have @BTrace, look for unsafe = xxx or trusted = xxx
            for (ExpressionTree ext : at.getArguments()) {
                if (!(ext instanceof JCAssign)) {
                    continue;
                }
                JCAssign assign = (JCAssign) ext;
                String name = ((JCIdent)assign.lhs).name.toString();
                if (!"unsafe".equals(name) && !"trusted".equals(name)) {
                    continue;
                }
                // now rhs is the value of @BTrace.unsafe.
                // The value can be complex (!!true, 1 == 2, etc.) - we support only booleans
                String val = assign.rhs.toString();
                if ("true".equals(val)) {
                    return true;  // bingo!
                } else if (!"false".equals(val)) {
                    processingEnv.getMessager().printMessage(Kind.WARNING,
                            Messages.get("no.complex.unsafe.value"), topElement);
                }
            }
        }
        return false;
    }

    /**
     * adds a listener for attribution.
     */
    private void prepareContext(Context context) {
        TaskListener otherListener = context.get(TaskListener.class);
        if (otherListener == null) {
            context.put(TaskListener.class, listener);
        } else {
            // handle cases of multiple listeners
            context.put(TaskListener.class, (TaskListener)null);
            TaskListeners listeners = new TaskListeners();
            listeners.add(otherListener);
            listeners.add(listener);
            context.put(TaskListener.class, listeners);
        }
    }

    /**
     * A task listener that invokes the processor whenever a class is fully
     * analyzed.
     */
    private final class AttributionTaskListener implements TaskListener {

        @Override
        public void finished(TaskEvent e) {
            if (e.getKind() != TaskEvent.Kind.ANALYZE) return;
            TypeElement elem = e.getTypeElement();
            for(Tree t : e.getCompilationUnit().getTypeDecls()) {
                if (t.getKind() == Tree.Kind.CLASS) {
                    if (((JCClassDecl)t).sym.equals(elem)) {
                        currentClass = (ClassTree)t;
                        break;
                    }
                }
            }
            if (currentClass != null) {
                verify(currentClass, elem);
            }
        }

        @Override
        public void started(TaskEvent e) { }

    }

    /**
     * A task listener multiplexer.
     */
    private static class TaskListeners implements TaskListener {
        private final List<TaskListener> listeners = new ArrayList<>();

        public void add(TaskListener listener) {
            listeners.add(listener);
        }

        public void remove(TaskListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void finished(TaskEvent e) {
            for (TaskListener listener : listeners)
                listener.finished(e);
        }

        @Override
        public void started(TaskEvent e) {
            for (TaskListener listener : listeners)
                listener.started(e);
        }
    }
}