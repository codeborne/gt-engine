package play.template2.compile;


import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import play.template2.exceptions.GTCompilationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

public class GTJavaCompileToClass {

    public static String javaVersion = "1.5";

    public static GTTypeResolver typeResolver;

    private final ClassLoader parentClassLoader;

    Map<String, Boolean> packagesCache = new HashMap<String, Boolean>();

    Map<String, String> settings;

    /**
     * Try to guess the magic configuration options
     */
    public GTJavaCompileToClass(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
        this.settings = new HashMap<String, String>();
        this.settings.put(CompilerOptions.OPTION_ReportMissingSerialVersion, CompilerOptions.IGNORE);
        this.settings.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
        this.settings.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);
        this.settings.put(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.IGNORE);
        this.settings.put(CompilerOptions.OPTION_ReportUnusedImport, CompilerOptions.IGNORE);
        this.settings.put(CompilerOptions.OPTION_Encoding, "UTF-8");
        this.settings.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE);
        this.settings.put(CompilerOptions.OPTION_Source, javaVersion);
        this.settings.put(CompilerOptions.OPTION_TargetPlatform, javaVersion);
        this.settings.put(CompilerOptions.OPTION_PreserveUnusedLocal, CompilerOptions.PRESERVE);
        this.settings.put(CompilerOptions.OPTION_Compliance, javaVersion);
    }

    /**
     * Something to compile
     */
    final class CompilationUnit implements ICompilationUnit {

        final private String clazzName;
        final private String fileName;
        final private char[] typeName;
        final private char[][] packageName;
        final private String source;

        CompilationUnit(String pClazzName, String source) {
            clazzName = pClazzName;
            fileName = pClazzName.replaceAll("\\.", "/") + ".java";//bogus
            int dot = pClazzName.lastIndexOf('.');
            if (dot > 0) {
                typeName = pClazzName.substring(dot + 1).toCharArray();
            } else {
                typeName = pClazzName.toCharArray();
            }
            StringTokenizer izer = new StringTokenizer(pClazzName, ".");
            packageName = new char[izer.countTokens() - 1][];
            for (int i = 0; i < packageName.length; i++) {
                packageName[i] = izer.nextToken().toCharArray();
            }

            this.source = source;
        }

        @Override public char[] getFileName() {
            return fileName.toCharArray();
        }

        @Override public char[] getContents() {
            return source.toCharArray();
        }

        @Override public char[] getMainTypeName() {
            return typeName;
        }

        @Override public char[][] getPackageName() {
            return packageName;
        }

        @Override public boolean ignoreOptionalProblems() {
            return false;
        }
    }

    /**
     * Read binary content of a stream (warning does not use on large file !)
     * @param is The stream to read
     * @return The binary data
     */
    protected byte[] readContent(InputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int read = 0;
            byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch(IOException e) {
            throw new GTCompilationException(e);
        }
    }

    /**
         * Compilation result
         */
    public static class MyICompilerRequestor implements ICompilerRequestor {

        public CompiledClass[] compiledClasses;


        @Override public void acceptResult(CompilationResult result) {
            // If error
            if (result.hasErrors()) {
                for (IProblem problem: result.getErrors()) {
                    String className = new String(problem.getOriginatingFileName()).replace("/", ".");
                    className = className.substring(0, className.length() - 5);
                    String message = problem.getMessage();
                    if (problem.getID() == IProblem.CannotImportPackage) {
                        // Non sense !
                        message = problem.getArguments()[0] + " cannot be resolved";
                    }
                    throw new GTCompilationException("Compile error. classname: " + className + ". message: " + message + " ln: " + problem.getSourceLineNumber());
                }
            }
            // Something has been compiled
            compiledClasses = new CompiledClass[result.getClassFiles().length];
            ClassFile[] clazzFiles = result.getClassFiles();
            for (int i = 0; i < clazzFiles.length; i++) {
                final ClassFile clazzFile = clazzFiles[i];
                final char[][] compoundName = clazzFile.getCompoundName();
                final StringBuffer clazzName = new StringBuffer();
                for (int j = 0; j < compoundName.length; j++) {
                    if (j != 0) {
                        clazzName.append('.');
                    }
                    clazzName.append(compoundName[j]);
                }

                compiledClasses[i] = new CompiledClass(clazzName.toString(), clazzFile.getBytes());
            }

        }
    }


    public static class CompiledClass {
        public final String classname;
        public final byte[] bytes;

        public CompiledClass(String classname, byte[] bytes) {
            this.classname = classname;
            this.bytes = bytes;
        }
    }

    /**
     * Please compile this className
     */
    @SuppressWarnings("deprecation")
    public CompiledClass[] compile(String className, String source) {

        ICompilationUnit compilationUnits = new CompilationUnit(className, source);
        IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.exitOnFirstError();
        IProblemFactory problemFactory = new DefaultProblemFactory(Locale.ENGLISH);

        /**
         * To find types ...
         */
        INameEnvironment nameEnvironment = new INameEnvironment() {

            @Override public NameEnvironmentAnswer findType(final char[][] compoundTypeName) {
                final StringBuffer result = new StringBuffer();
                for (int i = 0; i < compoundTypeName.length; i++) {
                    if (i != 0) {
                        result.append('.');
                    }
                    result.append(compoundTypeName[i]);
                }
                return findType(result.toString());
            }

            @Override public NameEnvironmentAnswer findType(final char[] typeName, final char[][] packageName) {
                final StringBuffer result = new StringBuffer();
                for (int i = 0; i < packageName.length; i++) {
                    result.append(packageName[i]);
                    result.append('.');
                }
                result.append(typeName);
                return findType(result.toString());
            }

            private NameEnvironmentAnswer findType(final String name) {
                try {

                    // first let the framework try to resolve
                    byte[] bytes = typeResolver.getTypeBytes(name);
                    if (bytes == null) {
                        // Now look for our own classes
                        String resourceName = name.replace(".", "/") + ".class";
                        InputStream is = parentClassLoader.getResourceAsStream(resourceName);
                        if (is==null) {
                            return null;
                        }
                        bytes = readContent(is);

                        is.close();
                    }

                    ClassFileReader classFileReader = new ClassFileReader(bytes, name.toCharArray(), true);
                    return new NameEnvironmentAnswer(classFileReader, null);

                } catch (Exception e) {
                    // Something very very bad
                    throw new RuntimeException(e);
                }
            }

            @Override public boolean isPackage(char[][] parentPackageName, char[] packageName) {
                // Rebuild something usable
                StringBuilder sb = new StringBuilder();
                if (parentPackageName != null) {
                    for (char[] p : parentPackageName) {
                        sb.append(new String(p));
                        sb.append(".");
                    }
                }
                sb.append(new String(packageName));
                String name = sb.toString();
                if (packagesCache.containsKey(name)) {
                    return packagesCache.get(name).booleanValue();
                }

                // does there exist a class with this name?
                boolean isClass = false;
                try {
                    parentClassLoader.loadClass(name);
                    isClass = true;
                } catch (Exception e) {
                    // nop
                }

                boolean isPackage = !isClass;
                packagesCache.put(name, isPackage);
                return isPackage;
            }

            @Override public void cleanup() {
            }
        };


        MyICompilerRequestor compilerRequestor = new MyICompilerRequestor();


        /**
         * The JDT compiler
         */
        org.eclipse.jdt.internal.compiler.Compiler jdtCompiler = new Compiler(nameEnvironment, policy, settings, compilerRequestor, problemFactory) {

            @Override
            protected void handleInternalException(Throwable e, CompilationUnitDeclaration ud, CompilationResult result) {
            }
        };

        // Go !
        jdtCompiler.compile( new ICompilationUnit[]{compilationUnits});

        return compilerRequestor.compiledClasses;

    }
}
