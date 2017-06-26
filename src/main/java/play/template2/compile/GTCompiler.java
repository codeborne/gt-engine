package play.template2.compile;

import org.apache.commons.io.FileUtils;
import play.template2.GTTemplateInstanceFactoryLive;
import play.template2.GTTemplateLocation;
import play.template2.GTTemplateRepo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GTCompiler {

    public static File srcDestFolder;
    private final ClassLoader parentClassloader;
    private final GTTemplateRepo templateRepo;
    private final GTPreCompilerFactory preCompilerFactory;
    private final boolean storeSourceToDisk;

    public GTCompiler(ClassLoader parentClassloader, GTTemplateRepo templateRepo, GTPreCompilerFactory preCompilerFactory, boolean storeSourceToDisk) {
        this.parentClassloader = parentClassloader;
        this.templateRepo = templateRepo;
        this.preCompilerFactory = preCompilerFactory;
        this.storeSourceToDisk = storeSourceToDisk;
    }

    public static class CompiledTemplate {
        public final String templateClassName;
        public final GTJavaCompileToClass.CompiledClass[] compiledJavaClasses;

        public CompiledTemplate(String templateClassName, GTJavaCompileToClass.CompiledClass[] compiledJavaClasses) {
            this.templateClassName = templateClassName;
            this.compiledJavaClasses = compiledJavaClasses;
        }


    }

    /**
     * Write String content to a file (always use utf-8)
     * @param content The content to write
     * @param file The file to write
     */
    protected static void writeContent(CharSequence content, File file, String encoding) {
        try {
            FileUtils.write(file, content, encoding);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CompiledTemplate compile( GTTemplateLocation templateLocation) {
        // precompile it
        GTPreCompiler.Output precompiled = preCompilerFactory.createCompiler(templateRepo).compile(templateLocation);

        // compile the java code

        if ( srcDestFolder != null && storeSourceToDisk) {
            // store the generated src to disk
            File folder = new File( srcDestFolder, GTPreCompiler.generatedPackageName.replace('.','/'));
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File file = new File( folder, precompiled.javaClassName+".java");
            writeContent(precompiled.javaCode, file, "utf-8");
            file = new File( folder, precompiled.groovyClassName+".groovy");
            writeContent(precompiled.groovyCode, file, "utf-8");
        }

        // compile groovy
        GTJavaCompileToClass.CompiledClass[] groovyClasses = new GTGroovyCompileToClass(parentClassloader)
            .compileGroovySource( templateLocation, precompiled.groovyLineMapper, precompiled.groovyCode);

        // Create Classloader witch includes our groovy class
        GTTemplateInstanceFactoryLive.CL cl = new GTTemplateInstanceFactoryLive.CL(parentClassloader, groovyClasses);

        GTJavaCompileToClass.CompiledClass[] compiledJavaClasses = new GTJavaCompileToClass(cl).compile(precompiled.javaClassName, precompiled.javaCode);

        List<GTJavaCompileToClass.CompiledClass> allCompiledClasses = new ArrayList<>();
        allCompiledClasses.addAll( Arrays.asList(compiledJavaClasses) );
        allCompiledClasses.addAll( Arrays.asList(groovyClasses));

        return new CompiledTemplate(precompiled.javaClassName, allCompiledClasses.toArray( new GTJavaCompileToClass.CompiledClass[]{}));
    }

}
