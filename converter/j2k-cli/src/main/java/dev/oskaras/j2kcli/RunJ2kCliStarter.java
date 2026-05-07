package dev.oskaras.j2kcli;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class RunJ2kCliStarter implements ApplicationStarter {
    @Override
    public String getCommandName() {
        return "run-j2k-cli";
    }

    @Override
    public void main(List<String> args) {
        int code = run(args);
        System.exit(code);
    }

    private int run(List<String> args) {
        try {
            CliOptions options = CliOptions.parse(args);
            Files.createDirectories(options.outputDir);

            List<Path> javaFiles = collectJavaFiles(options.inputDir);
            int total = javaFiles.size();
            int success = 0;
            int failed = 0;
            List<String> failedFiles = new ArrayList<>();

            Project project = pickAnyOpenProject();
            if (project == null) {
                throw new IllegalStateException("No IntelliJ project is available for conversion context.");
            }

            Object converter = buildConverter(project);
            PsiManager psiManager = PsiManager.getInstance(project);

            for (Path javaFile : javaFiles) {
                Path relative = options.inputDir.relativize(javaFile);
                Path outputFile = options.outputDir.resolve(relative.toString().replaceAll("\\.java$", ".kt"));
                Files.createDirectories(outputFile.getParent());

                try {
                    String kotlinCode = convertSingleFile(converter, psiManager, javaFile, project);
                    Files.writeString(outputFile, kotlinCode, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    success++;
                } catch (Exception ex) {
                    failed++;
                    failedFiles.add(relative.toString());
                    Files.writeString(
                        outputFile,
                        "// J2K_CONVERSION_FAILED: " + relative + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    );
                    System.err.println("Failed converting " + relative + ": " + ex.getMessage());
                }
            }

            writeManifest(options.outputDir, total, success, failed, failedFiles);
            System.out.println("J2K CLI finished: total=" + total + ", success=" + success + ", failed=" + failed);
            return 0;
        } catch (Exception e) {
            System.err.println("J2K CLI failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }

    private static Project pickAnyOpenProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length > 0) {
            return projects[0];
        }
        return ProjectManager.getInstance().getDefaultProject();
    }

    private static List<Path> collectJavaFiles(Path inputDir) throws IOException {
        try (Stream<Path> stream = Files.walk(inputDir)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .toList();
        }
    }

    private static Object buildConverter(Project project) throws Exception {
        ClassLoader cl = RunJ2kCliStarter.class.getClassLoader();

        Class<?> settingsClass = Class.forName("org.jetbrains.kotlin.j2k.ConverterSettings", true, cl);
        Object settings = tryBuildWithCompanionDefault(settingsClass);
        if (settings == null) {
            settings = settingsClass.getDeclaredConstructor().newInstance();
        }

        Object referenceSearcher = tryInstantiate("org.jetbrains.kotlin.idea.j2k.IdeaReferenceSearcher", project);
        Object resolver = tryInstantiate("org.jetbrains.kotlin.idea.j2k.IdeaResolverForConverter", project);

        Class<?> converterClass = Class.forName("org.jetbrains.kotlin.j2k.JavaToKotlinConverter", true, cl);
        for (Constructor<?> ctor : converterClass.getConstructors()) {
            Object[] values = tryBuildConstructorArgs(ctor.getParameterTypes(), project, settings, referenceSearcher, resolver);
            if (values != null) {
                return ctor.newInstance(values);
            }
        }

        throw new IllegalStateException("Could not instantiate JavaToKotlinConverter with available constructors.");
    }

    private static String convertSingleFile(Object converter, PsiManager psiManager, Path javaFile, Project project) throws Exception {
        PsiFile psiJavaFile = loadPsiJavaFile(psiManager, javaFile);
        Method elementsToKotlin = converter.getClass().getMethod("elementsToKotlin", List.class);
        Object result = ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<Object>) () -> {
            try {
                return elementsToKotlin.invoke(converter, List.of(psiJavaFile));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        List<?> converted = readResultsList(result);
        if (converted.isEmpty()) {
            throw new IllegalStateException("No converted result returned by JavaToKotlinConverter.");
        }

        Object first = converted.get(0);
        String text = tryReadString(first, "getText");
        if (text == null) {
            text = tryReadFieldString(first, "text");
        }
        if (text != null) {
            return text;
        }

        String fallback = tryTranslatorFallback(javaFile, project);
        if (fallback != null) {
            return fallback;
        }

        throw new IllegalStateException("Could not extract converted Kotlin text from conversion result.");
    }

    private static PsiFile loadPsiJavaFile(PsiManager psiManager, Path javaFile) {
        PsiFile psi = ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<PsiFile>) () -> {
            var virtual = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(javaFile);
            if (virtual == null) {
                return null;
            }
            return psiManager.findFile(virtual);
        });
        if (psi == null) {
            throw new IllegalStateException("Unable to read PsiFile for " + javaFile);
        }
        return psi;
    }

    private static List<?> readResultsList(Object result) throws Exception {
        Method getResults = result.getClass().getMethod("getResults");
        Object list = getResults.invoke(result);
        if (list instanceof List<?>) {
            return (List<?>) list;
        }
        return List.of();
    }

    private static Object tryBuildWithCompanionDefault(Class<?> settingsClass) {
        try {
            Field companionField = settingsClass.getField("Companion");
            Object companion = companionField.get(null);
            for (Method method : companion.getClass().getMethods()) {
                if (method.getName().toLowerCase(Locale.ROOT).contains("default")
                    && settingsClass.isAssignableFrom(method.getReturnType())
                    && method.getParameterCount() == 0) {
                    return method.invoke(companion);
                }
            }
        } catch (Exception ignored) {
            // Fall through to default constructor.
        }
        return null;
    }

    private static Object tryInstantiate(String className, Project project) {
        try {
            Class<?> clazz = Class.forName(className);
            for (Constructor<?> ctor : clazz.getConstructors()) {
                if (ctor.getParameterCount() == 1 && Project.class.isAssignableFrom(ctor.getParameterTypes()[0])) {
                    return ctor.newInstance(project);
                }
            }
        } catch (Exception ignored) {
            // Best-effort, class might move between plugin versions.
        }
        return null;
    }

    private static Object[] tryBuildConstructorArgs(
        Class<?>[] parameterTypes,
        Project project,
        Object settings,
        Object referenceSearcher,
        Object resolver
    ) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.isAssignableFrom(project.getClass()) || parameterType == Project.class) {
                args[i] = project;
            } else if (parameterType.isInstance(settings)) {
                args[i] = settings;
            } else if (referenceSearcher != null && parameterType.isInstance(referenceSearcher)) {
                args[i] = referenceSearcher;
            } else if (resolver != null && parameterType.isInstance(resolver)) {
                args[i] = resolver;
            } else if (!parameterType.isPrimitive()) {
                args[i] = null;
            } else {
                return null;
            }
        }
        return args;
    }

    private static String tryReadString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof String ? (String) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String tryReadFieldString(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof String ? (String) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String tryTranslatorFallback(Path javaFile, Project project) {
        try {
            Class<?> translator = Class.forName("org.jetbrains.kotlin.j2k.JavaToKotlinTranslator");
            String source = FileUtil.loadFile(javaFile.toFile(), true);
            for (Method method : translator.getMethods()) {
                if (method.getName().toLowerCase(Locale.ROOT).contains("translate")
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && Project.class.isAssignableFrom(method.getParameterTypes()[1])) {
                    Object result = method.invoke(null, source, project);
                    if (result instanceof String) {
                        return (String) result;
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void writeManifest(Path outputDir, int total, int success, int failed, List<String> failedFiles) throws IOException {
        Path manifest = outputDir.resolve(".j2k_manifest.txt");
        List<String> lines = new ArrayList<>();
        lines.add("total=" + total);
        lines.add("success=" + success);
        lines.add("failed=" + failed);
        for (String file : failedFiles) {
            lines.add("failed_file=" + file);
        }
        Files.write(manifest, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private record CliOptions(Path inputDir, Path outputDir) {
        static CliOptions parse(List<String> args) {
            Map<String, String> parsed = new HashMap<>();
            for (int i = 0; i < args.size(); i++) {
                String token = args.get(i);
                if (token.startsWith("--")) {
                    if (i + 1 >= args.size()) {
                        throw new IllegalArgumentException("Missing value for " + token);
                    }
                    parsed.put(token, args.get(++i));
                }
            }
            String input = parsed.get("--input");
            String output = parsed.get("--output");
            if (input == null || output == null) {
                throw new IllegalArgumentException("Usage: idea.sh run-j2k-cli --input <java-dir> --output <kotlin-dir>");
            }
            return new CliOptions(Path.of(input).toAbsolutePath().normalize(), Path.of(output).toAbsolutePath().normalize());
        }
    }
}
