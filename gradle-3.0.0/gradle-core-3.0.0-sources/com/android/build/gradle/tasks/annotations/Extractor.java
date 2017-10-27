/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks.annotations;

import static com.android.SdkConstants.AMP_ENTITY;
import static com.android.SdkConstants.APOS_ENTITY;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.DOT_ZIP;
import static com.android.SdkConstants.GT_ENTITY;
import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.LT_ENTITY;
import static com.android.SdkConstants.QUOT_ENTITY;
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE;
import static com.android.SdkConstants.TYPE_DEF_VALUE_ATTRIBUTE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.checks.SupportAnnotationDetector.INT_RANGE_ANNOTATION;
import static com.android.tools.lint.detector.api.LintUtils.assertionsEnabled;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.FileUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.xml.XmlEscapers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.javadoc.PsiDocComment;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Annotation extractor which looks for annotations in parsed compilation units and writes
 * the annotations into a format suitable for use by IntelliJ and Android Studio etc;
 * it's basically an XML file, organized by package, which lists the signatures for
 * fields and methods in classes in the given package, and identifiers method parameters
 * by index, and lists the annotations annotated on that element.
 * <p>
 * This is primarily intended for use in Android libraries such as the support library,
 * where you want to use the resource int ({@code StringRes}, {@code DrawableRes}, and so on)
 * annotations to indicate what types of id's are expected, or the {@code IntDef} or
 * {@code StringDef} annotations to record which specific constants are allowed in int and
 * String parameters.
 * <p>
 * However, the code is also used to extract SDK annotations from the platform, where
 * the package names of the annotations differ slightly (and where the nullness annotations
 * do not have class retention for example). Therefore, this code contains some extra
 * support not needed when extracting annotations in an Android library, such as code
 * to skip annotations for any method/field not mentioned in the API database, and code
 * to rewrite the android.jar file to insert annotations in the generated bytecode.
 * <p>
 */
public class Extractor {
    /**
     * If true, remove typedefs (even public ones) if they are marked with {@code @hide}.
     * This is disabled because for some reason, the ECJ nodes do not provide valid
     * contents of javadoc entries for classes.
     */
    public static final boolean REMOVE_HIDDEN_TYPEDEFS = false;

    /** Whether to sort annotation attributes (otherwise their declaration order is used) */
    private final boolean sortAnnotations;

    /**
     * Whether we should include class-retention annotations into the extracted file;
     * we don't need {@code android.support.annotation.Nullable} to be in the extracted XML
     * file since it has class retention and will appear in the compiled .jar version of
     * the library
     */
    private final boolean includeClassRetentionAnnotations;

    /**
     * Whether we should skip nullable annotations in merged in annotations zip files
     * (these are typically from infer nullity, which sometimes is a bit aggressive
     * in assuming something should be marked as nullable; see for example issue #66999
     * or all the manual removals of findViewById @Nullable return value annotations
     */
    private static final boolean INCLUDE_INFERRED_NULLABLE = false;

    public static final String ANDROID_ANNOTATIONS_PREFIX = "android.annotation.";
    public static final String ANDROID_NULLABLE = "android.annotation.Nullable";
    public static final String SUPPORT_NULLABLE = "android.support.annotation.Nullable";
    public static final String SUPPORT_KEEP = "android.support.annotation.Keep";
    public static final String RESOURCE_TYPE_ANNOTATIONS_SUFFIX = "Res";
    public static final String ANDROID_NOTNULL = "android.annotation.NonNull";
    public static final String SUPPORT_NOTNULL = "android.support.annotation.NonNull";
    public static final String ANDROID_INT_DEF = "android.annotation.IntDef";
    public static final String ANDROID_INT_RANGE = "android.annotation.IntRange";
    public static final String ANDROID_STRING_DEF = "android.annotation.StringDef";
    public static final String REQUIRES_PERMISSION = "android.support.annotation.RequiresPermission";
    public static final String ANDROID_REQUIRES_PERMISSION = "android.annotation.RequiresPermission";
    public static final String IDEA_NULLABLE = "org.jetbrains.annotations.Nullable";
    public static final String IDEA_NOTNULL = "org.jetbrains.annotations.NotNull";
    public static final String IDEA_MAGIC = "org.intellij.lang.annotations.MagicConstant";
    public static final String IDEA_CONTRACT = "org.jetbrains.annotations.Contract";
    public static final String IDEA_NON_NLS = "org.jetbrains.annotations.NonNls";
    public static final String ATTR_VAL = "val";
    public static final String ATTR_PURE = "pure";

    @NonNull
    private final Map<String, List<AnnotationData>> types = Maps.newHashMap();

    @NonNull
    private final Set<String> irrelevantAnnotations = Sets.newHashSet();

    private final File classDir;

    /** Map from package to map from class to items */
    @NonNull
    private final Map<String, Map<String, List<Item>>> itemMap = Maps.newHashMap();
    private Map<String, PackageItem> packageMap;

    @Nullable
    private final ApiDatabase apiFilter;

    private final boolean displayInfo;

    private final Map<String,Integer> stats = Maps.newHashMap();
    private int filteredCount;
    private int mergedCount;

    private final Set<String> ignoredAnnotations = Sets.newHashSet();
    private boolean listIgnored;
    private List<String> typedefsToRemove;
    private Map<String,Boolean> sourceRetention;
    private final List<Item> keepItems = Lists.newArrayList();

    public static List<PsiJavaFile> createUnitsForFiles(@NonNull Project project,
            @NonNull List<File> specificSources) {
        List<PsiJavaFile> units = Lists.newArrayListWithCapacity(specificSources.size());
        VirtualFileSystem fileSystem = StandardFileSystems.local();
        PsiManager manager = PsiManager.getInstance(project);

        for (File source : specificSources) {
            VirtualFile virtualFile = fileSystem.findFileByPath(source.getPath());
            if (virtualFile != null) {
                PsiFile psiFile = manager.findFile(virtualFile);
                if (psiFile instanceof PsiJavaFile) {
                    units.add((PsiJavaFile) psiFile);
                }
            }
        }
        return units;
    }

    public static List<PsiJavaFile> createUnitsInDirectories(@NonNull Project project,
            @NonNull List<File> sourceDirs) {
        return createUnitsForFiles(project, gatherJavaSources(sourceDirs));
    }

    private static void addJavaSources(List<File> list, File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    addJavaSources(list, child);
                }
            }
        } else {
            if (file.isFile() && file.getName().endsWith(DOT_JAVA)) {
                list.add(file);
            }
        }
    }

    private static List<File> gatherJavaSources(List<File> sourcePath) {
        List<File> sources = Lists.newArrayList();
        for (File file : sourcePath) {
            addJavaSources(sources, file);
        }
        return sources;
    }

    public Extractor(@Nullable ApiDatabase apiFilter, @Nullable File classDir, boolean displayInfo,
            boolean includeClassRetentionAnnotations, boolean sortAnnotations) {
        this.apiFilter = apiFilter;
        this.listIgnored = apiFilter != null;
        this.classDir = classDir;
        this.displayInfo = displayInfo;
        this.includeClassRetentionAnnotations = includeClassRetentionAnnotations;
        this.sortAnnotations = sortAnnotations;
    }

    public void extractFromProjectSource(List<PsiJavaFile> units) {
        AnnotationVisitor visitor = new AnnotationVisitor(false, true);

        for (PsiJavaFile unit : units) {
            unit.accept(visitor);
        }

        typedefsToRemove = visitor.getPrivateTypedefClasses();
    }

    public void removeTypedefClasses() {
        if (classDir != null && typedefsToRemove != null && !typedefsToRemove.isEmpty()) {
            // Perform immediately
            boolean quiet = false;
            boolean verbose = false;
            boolean dryRun = false;
            //noinspection ConstantConditions
            TypedefRemover remover = new TypedefRemover(this, quiet, verbose, dryRun);
            remover.remove(classDir, typedefsToRemove);
        }
    }

    public void writeTypedefFile(@NonNull File file) throws IOException {
        // Write typedef recipe file for later processing
        String desc = "";
        if (typedefsToRemove != null) {
            Collections.sort(typedefsToRemove);
            StringBuilder sb = new StringBuilder(typedefsToRemove.size() * 100);
            for (String cls : typedefsToRemove) {
                sb.append("D ");
                sb.append(cls);
                sb.append("\n");
            }
            desc = sb.toString();
        }
        FileUtils.deleteIfExists(file);
        Files.createParentDirs(file);
        Files.write(desc, file, Charsets.UTF_8);
    }

    public static void removeTypedefClasses(@NonNull File classDir, @NonNull File typedefFile) {
        // Perform immediately
        boolean quiet = false;
        boolean verbose = false;
        boolean dryRun = false;
        //noinspection ConstantConditions
        TypedefRemover remover = new TypedefRemover(null, quiet, verbose, dryRun);
        remover.removeFromTypedefFile(classDir, typedefFile);
    }

    public void export(@Nullable File annotationsZip, @Nullable File proguardCfg)
            throws IOException {
        if (proguardCfg != null) {
            if (keepItems.isEmpty()) {
                if (proguardCfg.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    proguardCfg.delete();
                }
            } else if (writeKeepRules(proguardCfg)) {
                info("ProGuard keep rules written to " + proguardCfg);
            }
        }

        if (annotationsZip != null) {
            if (itemMap.isEmpty() && packageMap == null) {
                FileUtils.deleteIfExists(annotationsZip);
            } else {
                writeExternalAnnotations(annotationsZip);
                writeStats();
                info("Annotations written to " + annotationsZip);
            }
        }
    }

    public void writeStats() {
        if (!displayInfo) {
            return;
        }

        if (!stats.isEmpty()) {
            List<String> annotations = Lists.newArrayList(stats.keySet());
            annotations.sort((s1, s2) -> {
                int frequency1 = stats.get(s1);
                int frequency2 = stats.get(s2);
                int delta = frequency2 - frequency1;
                if (delta != 0) {
                    return delta;
                }
                return s1.compareTo(s2);
            });
            Map<String,String> fqnToName = Maps.newHashMap();
            int max = 0;
            int count = 0;
            for (String fqn : annotations) {
                String name = fqn.substring(fqn.lastIndexOf('.') + 1);
                fqnToName.put(fqn, name);
                max = Math.max(max, name.length());
                count += stats.get(fqn);
            }

            StringBuilder sb = new StringBuilder(200);
            sb.append("Extracted ").append(count).append(" Annotations:");
            for (String fqn : annotations) {
                sb.append('\n');
                String name = fqnToName.get(fqn);
                for (int i = 0, n = max - name.length() + 1; i < n; i++) {
                    sb.append(' ');
                }
                sb.append('@');
                sb.append(name);
                sb.append(':').append(' ');
                sb.append(Integer.toString(stats.get(fqn)));
            }
            if (sb.length() > 0) {
                info(sb.toString());
            }
        }

        if (filteredCount > 0) {
            info(filteredCount + " of these were filtered out (not in API database file)");
        }
        if (mergedCount > 0) {
            info(mergedCount + " additional annotations were merged in");
        }
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    void info(final String message) {
        if (displayInfo) {
            System.out.println(message);
        }
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static void error(String message) {
        System.err.println("Error: " + message);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static void warning(String message) {
        System.out.println("Warning: " + message);
    }

    @Nullable
    private static String getFqn(@NonNull PsiAnnotation annotation) {
        return annotation.getQualifiedName();
    }

    private PsiClass lastClass; // Cache for getFqn(PsiClass)
    private String lastFqn; // Cache for getFqn(PsiClass)

    @Nullable
    private String getFqn(@Nullable PsiClass cls) {
        if (cls != null) {
            if (cls.equals(lastClass)) {
                return lastFqn;
            }
            lastClass = cls;
            lastFqn = cls.getQualifiedName();
            return lastFqn;
        }

        return null;
    }

    private boolean hasSourceRetention(@NonNull String fqn, @Nullable PsiAnnotation annotation) {
        if (annotation == null) {
            return false;
        }

        if (sourceRetention == null) {
            sourceRetention = Maps.newHashMapWithExpectedSize(20);
            // The @IntDef and @String annotations have always had source retention,
            // and always must (because we can't express fully qualified field references
            // in a .class file.)
            sourceRetention.put(INT_DEF_ANNOTATION, true);
            sourceRetention.put(STRING_DEF_ANNOTATION, true);
            // The @Nullable and @NonNull annotations have always had class retention
            sourceRetention.put(SUPPORT_NOTNULL, false);
            sourceRetention.put(SUPPORT_NULLABLE, false);
        }

        Boolean source = sourceRetention.get(fqn);

        if (source != null) {
            return source;
        }

        boolean hasSourceRetention = false;
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        if (ref != null) {
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiClass) {
                hasSourceRetention = hasSourceRetention((PsiClass) resolved);
            }
        }
        sourceRetention.put(fqn, hasSourceRetention);

        return hasSourceRetention;
    }

    static boolean hasSourceRetention(@NonNull PsiAnnotation psiAnnotation) {
        if ("java.lang.annotation.Retention".equals(psiAnnotation.getQualifiedName())) {
            PsiNameValuePair[] attributes = psiAnnotation.getParameterList()
                    .getAttributes();
            if (attributes.length != 1) {
                error("Expected exactly one parameter passed to @Retention");
                return false;
            }
            PsiAnnotationMemberValue value = attributes[0].getValue();
            if (value instanceof PsiReferenceExpression) {
                PsiReferenceExpression expression = (PsiReferenceExpression) value;
                PsiElement element = expression.resolve();
                if (element instanceof PsiField) {
                    PsiField field = (PsiField) element;
                    if ("SOURCE".equals(field.getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    static boolean hasSourceRetention(PsiClass cls) {
        PsiModifierList modifierList = cls.getModifierList();
        if (modifierList != null) {
            for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
                if (hasSourceRetention(psiAnnotation)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addAnnotations(@Nullable PsiModifierList modifierList, @NonNull Item item) {
        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                if (isRelevantAnnotation(annotation)) {
                    String fqn = getFqn(annotation);
                    if (SUPPORT_KEEP.equals(fqn)) {
                        // Put keep rules in a different place; we don't want to write
                        // these out into the external annotations database, they go
                        // into a special proguard file
                        keepItems.add(item);
                    } else {
                        addAnnotation(annotation, fqn, item.annotations);
                    }
                }
            }
        }
    }

    private void addAnnotation(@NonNull PsiAnnotation annotation, @Nullable String fqn,
            @NonNull List<AnnotationData> list) {
        if (fqn == null) {
            return;
        }

        if (fqn.equals(ANDROID_NULLABLE) || fqn.equals(SUPPORT_NULLABLE)) {
            recordStats(fqn);
            list.add(new AnnotationData(SUPPORT_NULLABLE));
            return;
        }

        if (fqn.equals(ANDROID_NOTNULL) || fqn.equals(SUPPORT_NOTNULL)) {
            recordStats(fqn);
            list.add(new AnnotationData(SUPPORT_NOTNULL));
            return;
        }

        if (fqn.startsWith(SUPPORT_ANNOTATIONS_PREFIX)
                && fqn.endsWith(RESOURCE_TYPE_ANNOTATIONS_SUFFIX)) {
            recordStats(fqn);
            list.add(new AnnotationData(fqn));
            return;
        }

        if (fqn.startsWith(ANDROID_ANNOTATIONS_PREFIX)) {
            // System annotations: translate to support library annotations
            if (fqn.endsWith(RESOURCE_TYPE_ANNOTATIONS_SUFFIX)) {
                // Translate e.g. android.annotation.DrawableRes to
                //    android.support.annotation.DrawableRes
                String resAnnotation = SUPPORT_ANNOTATIONS_PREFIX +
                        fqn.substring(ANDROID_ANNOTATIONS_PREFIX.length());
                if (!includeClassRetentionAnnotations
                        && !hasSourceRetention(resAnnotation, null)) {
                    return;
                }
                recordStats(resAnnotation);
                list.add(new AnnotationData(resAnnotation));
                return;
            } else if (isRelevantFrameworkAnnotation(fqn)) {
                // Translate other android.annotation annotations into corresponding
                // support annotations
                String supportAnnotation = SUPPORT_ANNOTATIONS_PREFIX +
                        fqn.substring(ANDROID_ANNOTATIONS_PREFIX.length());
                if (!includeClassRetentionAnnotations
                        && !hasSourceRetention(supportAnnotation, null)) {
                    return;
                }
                recordStats(supportAnnotation);
                list.add(createData(supportAnnotation, annotation));
            }
        }

        if (fqn.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
            recordStats(fqn);
            list.add(createData(fqn, annotation));
            return;
        }

        if (isMagicConstant(annotation, fqn)) {
            List<AnnotationData> indirect = types.get(fqn);
            if (indirect != null) {
                list.addAll(indirect);
            }
        }
    }

    private void recordStats(String fqn) {
        Integer count = stats.get(fqn);
        if (count == null) {
            count = 0;
        }
        stats.put(fqn, count + 1);
    }

    private boolean hasRelevantAnnotations(@Nullable PsiModifierList modifiers) {
        if (modifiers != null) {
            for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                if (isRelevantAnnotation(annotation)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isRelevantAnnotation(@NonNull PsiAnnotation annotation) {
        PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
        if (nameReferenceElement != null) {
            // Common case: Don't even look up qualified name for the common @Override
            // annotation.
            String name = nameReferenceElement.getReferenceName();
            if ("Override".equals(name) || "SuppressWarnings".equals(name)) {
                return false;
            }
        }

        String fqn = getFqn(annotation);
        if (fqn == null || fqn.startsWith("java.lang.")) {
            return false;
        }
        if (fqn.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
            if (fqn.equals(SUPPORT_KEEP)) {
                return true; // even with class file retention we want to process these
            }

            //noinspection PointlessBooleanExpression,ConstantConditions,RedundantIfStatement
            if (!includeClassRetentionAnnotations && !hasSourceRetention(fqn, annotation)) {
                return false;
            }

            return true;
        } else if (fqn.startsWith(ANDROID_ANNOTATIONS_PREFIX)) {
            return isRelevantFrameworkAnnotation(fqn);
        }
        if (fqn.equals(ANDROID_NULLABLE) || fqn.equals(ANDROID_NOTNULL)
                || isMagicConstant(annotation, fqn)) {
            return true;
        } else if (fqn.equals(IDEA_CONTRACT)) {
            return true;
        }

        return false;
    }

    private static boolean isRelevantFrameworkAnnotation(@NonNull String fqn) {
        return fqn.startsWith(ANDROID_ANNOTATIONS_PREFIX)
                && !fqn.endsWith(".Widget")
                && !fqn.endsWith(".TargetApi")
                && !fqn.endsWith(".SystemApi")
                && !fqn.endsWith(".TestApi")
                && !fqn.endsWith(".SuppressAutoDoc")
                && !fqn.endsWith(".SuppressLint")
                && !fqn.endsWith(".SdkConstant");
    }

    boolean isMagicConstant(@NonNull PsiAnnotation annotation, @NonNull String typeName) {
        if (irrelevantAnnotations.contains(typeName)
                || typeName.startsWith("java.lang.")) { // @Override, @SuppressWarnings, etc.
            return false;
        }
        if (types.containsKey(typeName)) {
            return true;
        }
        switch (typeName) {
            case INT_DEF_ANNOTATION:
            case STRING_DEF_ANNOTATION:
            case INT_RANGE_ANNOTATION:
            case ANDROID_INT_RANGE:
            case ANDROID_INT_DEF:
            case ANDROID_STRING_DEF:
                return true;
        }

        // See if this annotation is itself annotated.
        // We only support a single level of IntDef type annotations, not arbitrary nesting
        PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
        if (referenceElement != null) {
            PsiElement resolved = referenceElement.resolve();
            if (resolved instanceof PsiClass) {
                PsiModifierList modifierList = ((PsiClass) resolved).getModifierList();
                if (modifierList != null) {
                    boolean match = false;
                    for (PsiAnnotation a : modifierList.getAnnotations()) {
                        String fqn = a.getQualifiedName();
                        if (isNestedAnnotation(fqn)) {
                            List<AnnotationData> list = types.get(typeName);
                            if (list == null) {
                                list = new ArrayList<>(2);
                                types.put(typeName, list);
                            }
                            addAnnotation(a, fqn, list);
                            match = true;
                        }
                    }
                    if (match) {
                        return true;
                    }
                }
            }
        }

        irrelevantAnnotations.add(typeName);

        return false;
    }

    static boolean isNestedAnnotation(@Nullable String fqn) {
        return (fqn != null &&
                (fqn.equals(INT_DEF_ANNOTATION) ||
                        fqn.equals(STRING_DEF_ANNOTATION) ||
                        fqn.equals(REQUIRES_PERMISSION) ||
                        fqn.equals(ANDROID_REQUIRES_PERMISSION) ||
                        fqn.equals(INT_RANGE_ANNOTATION) ||
                        fqn.equals(ANDROID_INT_RANGE) ||
                        fqn.equals(ANDROID_INT_DEF) ||
                        fqn.equals(ANDROID_STRING_DEF)));
    }

    private boolean writeKeepRules(@NonNull File proguardCfg) {
        if (!keepItems.isEmpty()) {
            try {
                try (Writer writer = new BufferedWriter(new FileWriter(proguardCfg))) {
                    Collections.sort(keepItems);
                    for (Item item : keepItems) {
                        writer.write(item.getKeepRule());
                        writer.write('\n');
                    }
                }
            } catch (IOException ioe) {
                error(ioe.toString());
                return true;
            }

            // Now that we've handled these items, remove them from the list
            // such that we don't accidentally also emit them into the annotations.zip
            // file, where they are not needed
            for (Item item : keepItems) {
                removeItem(item.getQualifiedClassName(), item);
            }
        } else if (proguardCfg.exists()) {
            //noinspection ResultOfMethodCallIgnored
            proguardCfg.delete();
        }
        return false;
    }

    private void writeExternalAnnotations(@NonNull File annotationsZip) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(annotationsZip);
                JarOutputStream zos =
                        new JarOutputStream(new BufferedOutputStream(fileOutputStream))) {
                List<String> sortedPackages = new ArrayList<>(itemMap.keySet());

                if (packageMap != null) {
                    for (String pkg : packageMap.keySet()) {
                        if (!itemMap.containsKey(pkg)) {
                            sortedPackages.add(pkg);
                        }
                    }
                }

                Collections.sort(sortedPackages);
                for (String pkg : sortedPackages) {
                    // Note: Using / rather than File.separator: jar lib requires it
                    String name = pkg.replace('.', '/') + "/annotations.xml";

                    JarEntry outEntry = new JarEntry(name);
                    zos.putNextEntry(outEntry);

                    try (StringPrintWriter writer = StringPrintWriter.create()) {
                        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<root>");

                        Map<String, List<Item>> classMap = itemMap.get(pkg);

                        if (classMap == null) {
                            // package only contains package-info.java annotations
                            classMap = Collections.emptyMap();
                        }

                        // Export package items first
                        if (packageMap != null) {
                            PackageItem item = packageMap.get(pkg);
                            if (item != null) {
                                item.write(writer);
                            }
                        }

                        List<String> classes = new ArrayList<>(classMap.keySet());
                        Collections.sort(classes);
                        for (String cls : classes) {
                            List<Item> items = classMap.get(cls);
                            Collections.sort(items);
                            for (Item item : items) {
                                item.write(writer);
                            }
                        }

                        writer.println("</root>\n");
                        writer.close();
                        String xml = writer.getContents();

                        // Validate
                        if (assertionsEnabled()) {
                            Document document = checkDocument(pkg, xml, false);
                            if (document == null) {
                                error("Could not parse XML document back in for entry " + name
                                        + ": invalid XML?\n\"\"\"\n" + xml + "\n\"\"\"\n");
                            }
                        }
                        byte[] bytes = xml.getBytes(Charsets.UTF_8);
                        zos.write(bytes);
                        zos.closeEntry();
                    }
                }
            }
    }

    private void addPackage(@NonNull String pkg, @NonNull PackageItem item) {
        // Not part of the API?
        if (apiFilter != null && item.isFiltered(apiFilter)) {
            if (isListIgnored()) {
                info("Skipping API because it is not part of the API file: " + item);
            }

            filteredCount++;
            return;
        }

        if (packageMap == null) {
            packageMap = Maps.newHashMap();
        }

        packageMap.put(pkg, item);
    }

    private void addItem(@NonNull String fqn, @NonNull Item item) {
        // Not part of the API?
        if (apiFilter != null && item.isFiltered(apiFilter)) {
            if (isListIgnored()) {
                info("Skipping API because it is not part of the API file: " + item);
            }

            filteredCount++;
            return;
        }

        addItemUnconditionally(fqn, item);
    }

    private void addItemUnconditionally(@NonNull String fqn, @NonNull Item item) {
        String pkg = getPackage(fqn);
        Map<String, List<Item>> classMap = itemMap.get(pkg);
        if (classMap == null) {
            classMap = Maps.newHashMapWithExpectedSize(100);
            itemMap.put(pkg, classMap);
        }
        List<Item> items = classMap.get(fqn);
        if (items == null) {
            items = Lists.newArrayList();
            classMap.put(fqn, items);
        }

        items.add(item);
    }

    private void removeItem(@NonNull String classFqn, @NonNull Item item) {
        String pkg = getPackage(classFqn);
        Map<String, List<Item>> classMap = itemMap.get(pkg);
        if (classMap != null) {
            List<Item> items = classMap.get(classFqn);
            if (items != null) {
                items.remove(item);
                if (items.isEmpty()) {
                    classMap.remove(classFqn);
                    if (classMap.isEmpty()) {
                        itemMap.remove(pkg);
                    }
                }
            }
        }
    }

    @Nullable
    private Item findItem(@NonNull String fqn, @NonNull Item item) {
        String pkg = getPackage(fqn);
        Map<String, List<Item>> classMap = itemMap.get(pkg);
        if (classMap == null) {
            return null;
        }
        List<Item> items = classMap.get(fqn);
        if (items == null) {
            return null;
        }
        for (Item existing : items) {
            if (existing.equals(item)) {
                return existing;
            }
        }

        return null;
    }

    @Nullable
    private static Document checkDocument(@NonNull String pkg, @NonNull String xml,
            boolean namespaceAware) {
        try {
            return XmlUtils.parseDocument(xml, namespaceAware);
        } catch (SAXException sax) {
            warning("Failed to parse document for package " + pkg + ": " + sax.toString());
        } catch (Exception e) {
            // pass
            // This method is deliberately silent; will return null
        }

        return null;
    }

    public void mergeExisting(@NonNull File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    mergeExisting(child);
                }
            }
        } else if (file.isFile()) {
            if (file.getPath().endsWith(DOT_JAR) || file.getPath().endsWith(DOT_ZIP)) {
                mergeFromJar(file);
            } else if (file.getPath().endsWith(DOT_XML)) {
                try {
                    String xml = Files.toString(file, Charsets.UTF_8);
                    mergeAnnotationsXml(file.getPath(), xml);
                } catch (IOException e) {
                    error("Aborting: I/O problem during transform: " + e.toString());
                }
            }
        }
    }

    private void mergeFromJar(@NonNull File jar) {
        // Reads in an existing annotations jar and merges in entries found there
        // with the annotations analyzed from source.
        JarInputStream zis = null;
        try {
            @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
            FileInputStream fis = new FileInputStream(jar);
            zis = new JarInputStream(fis);
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (entry.getName().endsWith(".xml")) {
                    byte[] bytes = ByteStreams.toByteArray(zis);
                    String xml = new String(bytes, Charsets.UTF_8);
                    mergeAnnotationsXml(jar.getPath() + ": " + entry, xml);
                }
                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            error("Aborting: I/O problem during transform: " + e.toString());
        } finally {
            //noinspection deprecation
            try {
                Closeables.close(zis, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
        }
    }

    private void mergeAnnotationsXml(@NonNull String path, @NonNull String xml) {
        try {
            Document document = XmlUtils.parseDocument(xml, false);
            mergeDocument(document);
        } catch (Exception e) {
            String message = "Failed to merge " + path + ": " + e.toString();
            if (e instanceof SAXParseException) {
                SAXParseException spe = (SAXParseException)e;
                message = "Line " + spe.getLineNumber() + ":" + spe.getColumnNumber() + ": " + message;
            }
            error(message);
            if (!(e instanceof IOException)) {
                e.printStackTrace();
            }
        }
    }

    private void mergeDocument(@NonNull Document document) {
        @SuppressWarnings("SpellCheckingInspection")
        final Pattern XML_SIGNATURE = Pattern.compile(
                // Class (FieldName | Type? Name(ArgList) Argnum?)
                //"(\\S+) (\\S+|(.*)\\s+(\\S+)\\((.*)\\)( \\d+)?)");
                "(\\S+) (\\S+|((.*)\\s+)?(\\S+)\\((.*)\\)( \\d+)?)");

        Element root = document.getDocumentElement();
        String rootTag = root.getTagName();
        assert rootTag.equals("root") : rootTag;

        for (Element item : getChildren(root)) {
            String signature = item.getAttribute(ATTR_NAME);
            if (signature == null || signature.equals("null")) {
                continue; // malformed item
            }

            if (!hasRelevantAnnotations(item)) {
                continue;
            }

            signature = unescapeXml(signature);
            if (signature.equals("java.util.Calendar int get(int)")) {
                // https://youtrack.jetbrains.com/issue/IDEA-137385
                continue;
            } else if (signature.equals("java.util.Calendar void set(int, int, int) 1")
                    || signature.equals("java.util.Calendar void set(int, int, int, int, int) 1")
                    || signature.equals("java.util.Calendar void set(int, int, int, int, int, int) 1")) {
                // http://b.android.com/76090
                continue;
            }

            Matcher matcher = XML_SIGNATURE.matcher(signature);
            if (matcher.matches()) {
                String containingClass = matcher.group(1);
                if (containingClass == null) {
                    warning("Could not find class for " + signature);
                }
                String methodName = matcher.group(5);
                if (methodName != null) {
                    String type = matcher.group(4);
                    boolean isConstructor = type == null;
                    String parameters = matcher.group(6);
                    mergeMethodOrParameter(item, matcher, containingClass, methodName, type,
                            isConstructor, parameters);
                } else {
                    String fieldName = matcher.group(2);
                    mergeField(item, containingClass, fieldName);
                }
            } else {
                if (signature.indexOf(' ') != -1 || signature.indexOf('.') == -1) {
                    warning("No merge match for signature " + signature);
                } // else: probably just a class signature, e.g. for @NonNls
            }
        }
    }

    @NonNull
    private static String unescapeXml(@NonNull String escaped) {
        String workingString = escaped.replace(QUOT_ENTITY, "\"");
        workingString = workingString.replace(LT_ENTITY, "<");
        workingString = workingString.replace(GT_ENTITY, ">");
        workingString = workingString.replace(APOS_ENTITY, "'");
        workingString = workingString.replace(AMP_ENTITY, "&");

        return workingString;
    }

    @NonNull
    private static String escapeXml(@NonNull String unescaped) {
        return XmlEscapers.xmlAttributeEscaper().escape(unescaped);
    }

    /**
     * Returns true if this XML entry contains historic metadata, e.g. has
     * an api attribute which designates that this API may no longer be in the SDK,
     * but the annotations should be preserved for older API levels
     */
    private static boolean hasHistoricData(@NonNull Element item) {
       Node curr = item.getFirstChild();
       while (curr != null) {
           // Example:
           // <item name="android.provider.Browser BOOKMARKS_URI">
           //   <annotation name="android.support.annotation.RequiresPermission.Read">
           //     <val name="value" val="&quot;com.android.browser.permission.READ_HISTORY_BOOKMARKS&quot;" />
           //     <val name="apis" val="&quot;..22&quot;" />
           //   </annotation>
           //   ..
           if (curr.getNodeType() == Node.ELEMENT_NODE &&
                   "annotation".equals(curr.getNodeName())) {
               Node inner = curr.getFirstChild();
               while (inner != null) {
                   if (inner.getNodeType() == Node.ELEMENT_NODE &&
                           "val".equals(inner.getNodeName()) &&
                           "apis".equals(((Element) inner).getAttribute("name"))) {
                       return true;
                   }
                   inner = inner.getNextSibling();
               }
           }
           curr = curr.getNextSibling();
       }

       return false;
    }

    private void mergeField(Element item, String containingClass, String fieldName) {
        if (apiFilter != null &&
                !hasHistoricData(item) &&
                !apiFilter.hasField(containingClass, fieldName)) {
            if (isListIgnored()) {
                info("Skipping imported element because it is not part of the API file: "
                        + containingClass + "#" + fieldName);
            }
            filteredCount++;
        } else {
            FieldItem fieldItem = new FieldItem(null, containingClass, fieldName, null);
            Item existing = findItem(containingClass, fieldItem);
            if (existing != null) {
                mergedCount += mergeAnnotations(item, existing);
            } else {
                addItemUnconditionally(containingClass, fieldItem);
                mergedCount += addAnnotations(item, fieldItem);
            }
        }
    }

    private void mergeMethodOrParameter(Element item, Matcher matcher, String containingClass,
            String methodName, String type, boolean constructor, String parameters) {
        parameters = fixParameterString(parameters);

        if (apiFilter != null &&
                !hasHistoricData(item) &&
                !apiFilter.hasMethod(containingClass, methodName, parameters)) {
            if (isListIgnored()) {
                info("Skipping imported element because it is not part of the API file: "
                        + containingClass + "#" + methodName + "(" + parameters + ")");
            }
            filteredCount++;
            return;
        }

        String argNum = matcher.group(7);
        if (argNum != null) {
            argNum = argNum.trim();
            ParameterItem parameterItem = new ParameterItem(null, containingClass, type,
                    methodName, parameters, constructor, argNum);
            Item existing = findItem(containingClass, parameterItem);

            if ("java.util.Calendar".equals(containingClass) && "set".equals(methodName)
                    && Integer.parseInt(argNum) > 0) {
                // Skip the metadata for Calendar.set(int, int, int+); see
                // https://code.google.com/p/android/issues/detail?id=73982
                return;
            }

            if (existing != null) {
                mergedCount += mergeAnnotations(item, existing);
            } else {
                addItemUnconditionally(containingClass, parameterItem);
                mergedCount += addAnnotations(item, parameterItem);
            }
        } else {
            MethodItem methodItem = new MethodItem(null, containingClass, type,
                    methodName, parameters, constructor);
            Item existing = findItem(containingClass, methodItem);
            if (existing != null) {
                mergedCount += mergeAnnotations(item, existing);
            } else {
                addItemUnconditionally(containingClass, methodItem);
                mergedCount += addAnnotations(item, methodItem);
            }
        }
    }

    // The parameter declaration used in XML files should not have duplicated spaces,
    // and there should be no space after commas (we can't however strip out all spaces,
    // since for example the spaces around the "extends" keyword needs to be there in
    // types like Map<String,? extends Number>
    private static String fixParameterString(String parameters) {
        return parameters.replace("  ", " ").
                replace(", ", ",").
                replace("?super","? super ").
                replace("?extends","? extends ");
    }

    private boolean hasRelevantAnnotations(Element item) {
        for (Element annotationElement : getChildren(item)) {
            if (isRelevantAnnotation(annotationElement)) {
                return true;
            }
        }

        return false;
    }

    private boolean isRelevantAnnotation(Element annotationElement) {
        AnnotationData annotation = createAnnotation(annotationElement);
        if (annotation == null) {
            // Unsupported annotation in import
            return false;
        }
        if (isNullable(annotation.name) || isNonNull(annotation.name)
                || annotation.name.startsWith(ANDROID_ANNOTATIONS_PREFIX)
                || annotation.name.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
            return true;
        } else if (annotation.name.equals(IDEA_CONTRACT)) {
            return true;
        } else if (annotation.name.equals(IDEA_NON_NLS)) {
            return false;
        } else {
            if (!ignoredAnnotations.contains(annotation.name)) {
                ignoredAnnotations.add(annotation.name);
                if (isListIgnored()) {
                    info("(Ignoring merge annotation " + annotation.name + ")");
                }
            }
        }

        return false;
    }

    @NonNull
    private static List<Element> getChildren(@NonNull Element element) {
        NodeList itemList = element.getChildNodes();
        int length = itemList.getLength();
        List<Element> result = new ArrayList<>(Math.max(5, length / 2 + 1));
        for (int i = 0; i < length; i++) {
            Node node = itemList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            result.add((Element) node);
        }

        return result;
    }

    private int addAnnotations(Element itemElement, Item item) {
        int count = 0;
        for (Element annotationElement : getChildren(itemElement)) {
            if (!isRelevantAnnotation(annotationElement)) {
                continue;
            }
            AnnotationData annotation = createAnnotation(annotationElement);
            item.annotations.add(annotation);
            count++;
        }
        return count;
    }

    private int mergeAnnotations(Element itemElement, Item item) {
        int count = 0;
        loop:
        for (Element annotationElement : getChildren(itemElement)) {
            if (!isRelevantAnnotation(annotationElement)) {
                continue;
            }
            AnnotationData annotation = createAnnotation(annotationElement);
            if (annotation == null) {
                continue;
            }
            boolean haveNullable = false;
            boolean haveNotNull = false;
            for (AnnotationData existing : item.annotations) {
                if (isNonNull(existing.name)) {
                    haveNotNull = true;
                }
                if (isNullable(existing.name)) {
                    haveNullable = true;
                }
                if (existing.equals(annotation)) {
                    continue loop;
                }
            }

            // Make sure we don't have a conflict between nullable and not nullable
            if (isNonNull(annotation.name) && haveNullable ||
                    isNullable(annotation.name) && haveNotNull) {
                warning("Found both @Nullable and @NonNull after import for " + item);
                continue;
            }

            item.annotations.add(annotation);
            count++;
        }

        return count;
    }

    private static boolean isNonNull(String name) {
        return name.equals(IDEA_NOTNULL)
                || name.equals(ANDROID_NOTNULL)
                || name.equals(SUPPORT_NOTNULL);
    }

    private static boolean isNullable(String name) {
        return name.equals(IDEA_NULLABLE)
                || name.equals(ANDROID_NULLABLE)
                || name.equals(SUPPORT_NULLABLE);
    }

    private AnnotationData createAnnotation(Element annotationElement) {
        String tagName = annotationElement.getTagName();
        assert tagName.equals("annotation") : tagName;
        String name = annotationElement.getAttribute(ATTR_NAME);
        assert name != null && !name.isEmpty();
        AnnotationData annotation;
        if (IDEA_MAGIC.equals(name)) {
            List<Element> children = getChildren(annotationElement);
            assert children.size() == 1 : children.size();
            Element valueElement = children.get(0);
            String valName = valueElement.getAttribute(ATTR_NAME);
            String value = valueElement.getAttribute(ATTR_VAL);
            boolean flagsFromClass = valName.equals("flagsFromClass");
            boolean flag = valName.equals("flags") || flagsFromClass;
            if (valName.equals("valuesFromClass") || flagsFromClass) {
                // Not supported
                boolean found = false;
                if (value.endsWith(DOT_CLASS)) {
                    String clsName = value.substring(0, value.length() - DOT_CLASS.length());
                    StringBuilder sb = new StringBuilder();
                    sb.append('{');

                    Field[] reflectionFields = null;
                    try {
                        Class<?> cls = Class.forName(clsName);
                        reflectionFields = cls.getDeclaredFields();
                    } catch (Exception ignore) {
                        // Class not available: not a problem. We'll rely on API filter.
                        // It's mainly used for sorting anyway.
                    }
                    if (apiFilter != null) {
                        // Search in API database
                        Set<String> fields = apiFilter.getDeclaredIntFields(clsName);
                        if ("java.util.zip.ZipEntry".equals(clsName)) {
                            // The metadata says valuesFromClass ZipEntry, and unfortunately
                            // that class implements ZipConstants and therefore imports a large
                            // number of irrelevant constants that aren't valid here. Instead,
                            // only allow these two:
                            fields = Sets.newHashSet("STORED", "DEFLATED");
                        }

                        if (fields != null) {
                            List<String> sorted = Lists.newArrayList(fields);
                            Collections.sort(sorted);
                            if (reflectionFields != null) {
                                final Map<String,Integer> rank = Maps.newHashMap();
                                for (int i = 0, n = sorted.size(); i < n; i++) {
                                    rank.put(sorted.get(i), reflectionFields.length + i);

                                }
                                for (int i = 0, n = reflectionFields.length; i < n; i++) {
                                    rank.put(reflectionFields[i].getName(), i);
                                }
                                sorted.sort((o1, o2) -> {
                                    int rank1 = rank.get(o1);
                                    int rank2 = rank.get(o2);
                                    int delta = rank1 - rank2;
                                    if (delta != 0) {
                                        return delta;
                                    }
                                    return o1.compareTo(o2);
                                });
                            }
                            boolean first = true;
                            for (String field : sorted) {
                                if (first) {
                                    first = false;
                                } else {
                                    sb.append(',').append(' ');
                                }
                                sb.append(clsName).append('.').append(field);
                            }
                            found = true;
                        }
                    }
                    // Attempt to sort in reflection order
                    if (!found && reflectionFields != null && (apiFilter == null || apiFilter.hasClass(clsName))) {
                        // Attempt with reflection
                        boolean first = true;
                        for (Field field : reflectionFields) {
                            if (field.getType() == Integer.TYPE ||
                                    field.getType() == int.class) {
                                if (first) {
                                    first = false;
                                } else {
                                    sb.append(',').append(' ');
                                }
                                sb.append(clsName).append('.').append(field.getName());
                            }
                        }
                    }
                    sb.append('}');
                    value = sb.toString();
                    if (sb.length() > 2) { // 2: { }
                        found = true;
                    }
                }

                if (!found) {
                    return null;
                }
            }

            //noinspection VariableNotUsedInsideIf
            if (apiFilter != null) {
                value = removeFiltered(value);
                while (value.contains(", ,")) {
                    value = value.replace(", ,",",");
                }
                if (value.startsWith(", ")) {
                    value = value.substring(2);
                }
            }

            annotation = new AnnotationData(
                    valName.equals("stringValues") ? STRING_DEF_ANNOTATION : INT_DEF_ANNOTATION,
                    new String[] {
                            TYPE_DEF_VALUE_ATTRIBUTE, value,
                            flag ? TYPE_DEF_FLAG_ATTRIBUTE : null, flag ? VALUE_TRUE : null });
        } else if (STRING_DEF_ANNOTATION.equals(name) || ANDROID_STRING_DEF.equals(name) ||
                INT_DEF_ANNOTATION.equals(name) || ANDROID_INT_DEF.equals(name)) {
            List<Element> children = getChildren(annotationElement);
            Element valueElement = children.get(0);
            String valName = valueElement.getAttribute(ATTR_NAME);
            assert TYPE_DEF_VALUE_ATTRIBUTE.equals(valName);
            String value = valueElement.getAttribute(ATTR_VAL);
            boolean flag = false;
            if (children.size() == 2) {
                valueElement = children.get(1);
                assert TYPE_DEF_FLAG_ATTRIBUTE.equals(valueElement.getAttribute(ATTR_NAME));
                flag = VALUE_TRUE.equals(valueElement.getAttribute(ATTR_VAL));
            }
            boolean intDef = INT_DEF_ANNOTATION.equals(name) || ANDROID_INT_DEF.equals(name);
            annotation = new AnnotationData(
                    intDef ? INT_DEF_ANNOTATION : STRING_DEF_ANNOTATION,
                    new String[] { TYPE_DEF_VALUE_ATTRIBUTE, value,
                    flag ? TYPE_DEF_FLAG_ATTRIBUTE : null, flag ? VALUE_TRUE : null});
        } else if (IDEA_CONTRACT.equals(name)) {
            List<Element> children = getChildren(annotationElement);
            Element valueElement = children.get(0);
            String value = valueElement.getAttribute(ATTR_VAL);
            String pure = valueElement.getAttribute(ATTR_PURE);
            if (pure != null && !pure.isEmpty()) {
                annotation = new AnnotationData(name,
                        new String[]{
                                TYPE_DEF_VALUE_ATTRIBUTE, value,
                                ATTR_PURE, pure});
            } else {
                annotation = new AnnotationData(name,
                        new String[]{TYPE_DEF_VALUE_ATTRIBUTE, value});
            }
        } else if (isNonNull(name)) {
            annotation = new AnnotationData(SUPPORT_NOTNULL);
        } else if (isNullable(name)) {
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (!INCLUDE_INFERRED_NULLABLE && IDEA_NULLABLE.equals(name)) {
                return null;
            }
            annotation = new AnnotationData(SUPPORT_NULLABLE);
        } else {
            List<Element> children = getChildren(annotationElement);
            if (children.isEmpty()) {
                return new AnnotationData(name);
            }
            List<String> attributeStrings = Lists.newArrayList();
            for (Element valueElement : children) {
                attributeStrings.add(valueElement.getAttribute(ATTR_NAME));
                attributeStrings.add(valueElement.getAttribute(ATTR_VAL));
            }
            annotation = new AnnotationData(name, attributeStrings.toArray(
                    new String[attributeStrings.size()]));
        }
        return annotation;
    }

    private String removeFiltered(String value) {
        assert apiFilter != null;
        if (value.startsWith("{")) {
            value = value.substring(1);
        }
        if (value.endsWith("}")) {
            value = value.substring(0, value.length() - 1);
        }
        value = value.trim();
        StringBuilder sb = new StringBuilder(value.length());
        sb.append('{');
        for (String fqn : Splitter.on(',').omitEmptyStrings().trimResults().split(value)) {
            fqn = unescapeXml(fqn);
            if (fqn.startsWith("\"")) {
                continue;
            }
            int index = fqn.lastIndexOf('.');
            String cls = fqn.substring(0, index);
            String field = fqn.substring(index + 1);
            if (apiFilter.hasField(cls, field)) {
                if (sb.length() > 1) { // 0: '{'
                    sb.append(", ");
                }
                sb.append(fqn);
            } else if (isListIgnored()) {
                info("Skipping constant from typedef because it is not part of the SDK: " + fqn);
            }
        }
        sb.append('}');
        return escapeXml(sb.toString());
    }

    private static String getPackage(String fqn) {
        // Extract package from the given fqn. Attempts to handle inner classes;
        // e.g.  "foo.bar.Foo.Bar will return "foo.bar".
        int index = 0;
        int last = 0;
        while (true) {
            index = fqn.indexOf('.', index);
            if (index == -1) {
                break;
            }
            last = index;
            if (index < fqn.length() - 1) {
                char next = fqn.charAt(index + 1);
                if (Character.isUpperCase(next)) {
                    break;
                }
            }
            index++;
        }

        return fqn.substring(0, last);
    }

    public void setListIgnored(boolean listIgnored) {
        this.listIgnored = listIgnored;
    }

    public boolean isListIgnored() {
        return listIgnored;
    }

    public AnnotationData createData(@NonNull String name, @NonNull PsiAnnotation annotation) {
        PsiNameValuePair[] pairs = annotation.getParameterList().getAttributes();
        if (pairs.length == 0) {
            return new AnnotationData(name);
        }
        return new AnnotationData(name, pairs);
    }

    /**
     * A writer which stores all its contents into a string and has the ability to mark
     * a certain freeze point and then reset back to it
     */
    private static class StringPrintWriter extends PrintWriter {
        private final StringWriter stringWriter;
        private int mark;

        private StringPrintWriter(@NonNull StringWriter stringWriter) {
            super(stringWriter);
            this.stringWriter = stringWriter;
        }

        public void mark() {
            flush();
            mark = stringWriter.getBuffer().length();
        }

        public void reset() {
            stringWriter.getBuffer().setLength(mark);
        }

        @NonNull
        public String getContents() {
            return stringWriter.toString();
        }

        @Override
        public String toString() {
            return getContents();
        }

        public static StringPrintWriter create() {
             return new StringPrintWriter(new StringWriter(1000));
        }
    }

    private class AnnotationData {
        @NonNull
        public final String name;

        @Nullable
        public String[] attributeStrings;

        @Nullable
        public PsiNameValuePair[] attributes;

        private AnnotationData(@NonNull String name) {
            this.name = name;
        }

        private AnnotationData(@NonNull String name, @Nullable PsiNameValuePair[] pairs) {
            this(name);
            attributes = pairs;
            assert attributes == null || attributes.length > 0;
        }

        private AnnotationData(@NonNull String name, @Nullable String[] attributeStrings) {
            this(name);
            this.attributeStrings = attributeStrings;
            assert attributeStrings != null && attributeStrings.length > 0;
        }

        void write(StringPrintWriter writer) {
            writer.mark();
            writer.print("    <annotation name=\"");
            writer.print(name);

            if (attributes != null) {
                writer.print("\">");
                writer.println();
                //noinspection PointlessBooleanExpression,ConstantConditions
                if (attributes.length > 1 && sortAnnotations) {
                    // Ensure that the value attribute is written first
                    Arrays.sort(attributes, new Comparator<PsiNameValuePair>() {
                        private String getName(PsiNameValuePair pair) {
                            String name = pair.getName();
                            if (name == null) {
                                return ATTR_VALUE;
                            } else {
                                return name;
                            }
                        }

                        private int rank(PsiNameValuePair pair) {
                            return ATTR_VALUE.equals(getName(pair)) ? -1 : 0;
                        }

                        @Override
                        public int compare(PsiNameValuePair o1, PsiNameValuePair o2) {
                            int r1 = rank(o1);
                            int r2 = rank(o2);
                            int delta = r1 - r2;
                            if (delta != 0) {
                                return delta;
                            }
                            return getName(o1).compareTo(getName(o2));
                        }
                    });
                }

                PsiNameValuePair[] attributes = this.attributes;

                if (attributes.length == 1
                        && name.startsWith(REQUIRES_PERMISSION)
                        && name.length() > REQUIRES_PERMISSION.length()
                        && attributes[0].getValue() instanceof PsiAnnotation) {
                    // The external annotations format does not allow for nested/complex annotations.
                    // However, these special annotations (@RequiresPermission.Read,
                    // @RequiresPermission.Write, etc) are known to only be simple containers with a
                    // single permission child, so instead we "inline" the content:
                    //  @Read(@RequiresPermission(allOf={P1,P2},conditional=true)
                    //     =>
                    //      @RequiresPermission.Read(allOf({P1,P2},conditional=true)
                    // That's setting attributes that don't actually exist on the container permission,
                    // but we'll counteract that on the read-annotations side.
                    PsiAnnotation annotation = (PsiAnnotation) attributes[0].getValue();
                    attributes = annotation.getParameterList().getAttributes();
                }

                boolean empty = true;
                for (PsiNameValuePair pair : attributes) {
                    String value = attributeString(pair.getValue());
                    if (value == null) {
                        continue;
                    }
                    empty = false;
                    String name = pair.getName();
                    if (name == null) {
                        name = ATTR_VALUE; // default name
                    }

                    // Platform typedef annotations now declare a prefix attribute for
                    // documentation generation purposes; this should not be part of the
                    // extracted metadata.
                    if ("prefix".equals(name) && INT_DEF_ANNOTATION.equals(this.name)) {
                        continue;
                    }

                    writer.print("      <val name=\"");
                    writer.print(name);
                    writer.print("\" val=\"");
                    writer.print(escapeXml(value));
                    writer.println("\" />");
                }

                if (empty) {
                    // All items were filtered out: don't write the annotation at all
                    writer.reset();
                    return;
                }

                writer.println("    </annotation>");

            } else if (attributeStrings != null) {
                writer.print("\">");
                writer.println();
                for (int i = 0; i < attributeStrings.length; i += 2) {
                    String name = attributeStrings[i];
                    String value = attributeStrings[i + 1];
                    if (name == null) {
                        continue;
                    }
                    writer.print("      <val name=\"");
                    writer.print(name);
                    writer.print("\" val=\"");
                    writer.print(escapeXml(value));
                    writer.println("\" />");
                }
                writer.println("    </annotation>");
            } else {
                writer.println("\" />");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AnnotationData that = (AnnotationData) o;

            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Nullable
        private String attributeString(@Nullable PsiAnnotationMemberValue value) {
            StringBuilder sb = new StringBuilder();
            if (value != null && appendExpression(sb, value)) {
                return sb.toString();
            } else {
                return null;
            }
        }

        private boolean appendExpression(@NonNull StringBuilder sb,
                @NonNull PsiAnnotationMemberValue expression) {
            if (expression instanceof PsiArrayInitializerMemberValue) {
                sb.append('{');
                PsiArrayInitializerMemberValue initializer
                        = (PsiArrayInitializerMemberValue) expression;
                boolean first = true;
                int initialLength = sb.length();
                for (PsiAnnotationMemberValue e : initializer.getInitializers()) {
                    int length = sb.length();
                    if (first) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    boolean appended = appendExpression(sb, e);
                    if (!appended) {
                        // trunk off comma if it bailed for some reason (e.g. constant
                        // filtered out by API etc)
                        sb.setLength(length);
                        if (length == initialLength) {
                            first = true;
                        }
                    }
                }
                sb.append('}');
                if (sb.length() == 2) {
                    // All values filtered out
                    return false;
                }
                return true;
            } else if (expression instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
                PsiElement resolved = referenceExpression.resolve();
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    if (!isInlinedConstant()) {
                        // Inline constants
                        Object value = field.computeConstantValue();
                        if (appendLiteralValue(sb, value)) {
                            return true;
                        }
                    }

                    PsiClass declaringClass = field.getContainingClass();
                    if (declaringClass == null) {
                        error("No containing class found for " + field.getName());
                        return false;
                    }
                    String qualifiedName = declaringClass.getQualifiedName();
                    String fieldName = field.getName();
                    if (qualifiedName != null && fieldName != null) {
                        if (apiFilter != null &&
                                !apiFilter.hasField(qualifiedName, fieldName)) {
                            if (isListIgnored()) {
                                info("Filtering out typedef constant "
                                        + qualifiedName + "."
                                        + fieldName + "");
                            }
                            return false;
                        }
                        sb.append(qualifiedName);
                        sb.append('.');
                        sb.append(fieldName);
                        return true;
                    }
                    return false;
                } else {
                    warning("Unexpected reference to " + resolved);
                    return false;
                }
            } else if (expression instanceof PsiLiteral) {
                PsiLiteral literal = (PsiLiteral) expression;
                Object literalValue = literal.getValue();
                if (appendLiteralValue(sb, literalValue)) {
                    return true;
                }
            } else if (expression instanceof PsiExpression) {
                // For example, binary expressions like 3 + 4
                Object literalValue = JavaConstantExpressionEvaluator.computeConstantExpression(
                        (PsiExpression) expression, false);
                if (appendLiteralValue(sb, literalValue)) {
                    return true;
                }
            }

            warning("Unexpected annotation expression of type " + expression.getClass()
                    + " and is " + expression);

            return false;
        }

        private boolean isInlinedConstant() {
            return name.equals(INT_DEF_ANNOTATION) ||
                    name.equals(STRING_DEF_ANNOTATION) ||
                    name.equals("android.support.annotation.SystemService");
        }
    }

    private static boolean appendLiteralValue(
            @NonNull StringBuilder sb,
            @Nullable Object literalValue) {
        if (literalValue instanceof Number || literalValue instanceof Boolean) {
            sb.append(literalValue.toString());
            return true;
        } else if (literalValue instanceof String || literalValue instanceof Character) {
            sb.append('"');
            XmlUtils.appendXmlAttributeValue(sb, literalValue.toString());
            sb.append('"');
            return true;
        }
        return false;
    }

    public enum ClassKind {
        CLASS,
        INTERFACE,
        ENUM,
        ANNOTATION;

        @NonNull
        public static ClassKind forClass(@Nullable PsiClass declaration) {
            if (declaration == null) {
                return CLASS;
            }
            if (declaration.isEnum()) {
                return ENUM;
            } else if (declaration.isAnnotationType()) {
                return ANNOTATION;
            } else if (declaration.isInterface()) {
                return INTERFACE;
            } else {
                return CLASS;
            }
        }

        public String getKeepType() {
            // See http://proguard.sourceforge.net/manual/usage.html#classspecification
            switch (this) {
                case INTERFACE:
                    return "interface";
                case ENUM:
                    return "enum";

                case ANNOTATION:
                case CLASS:
                default:
                    return "class";
            }
        }


        @Override
        public String toString() {
            return getKeepType();
        }
    }

    /**
     * An item in the XML file: this corresponds to a method, a field, or a method parameter, and
     * has an associated set of annotations
     */
    private abstract static class Item implements Comparable<Item> {
        @NonNull public final String containingClass;
        @Nullable public final PsiClass psiClass;

        public Item(
                @Nullable PsiClass psiClass,
                @NonNull String containingClass) {
            this.psiClass = psiClass;
            this.containingClass = containingClass;
        }

        public final List<AnnotationData> annotations = Lists.newArrayList();

        void write(StringPrintWriter writer) {
            if (annotations.isEmpty()) {
                return;
            }
            writer.print("  <item name=\"");
            writer.print(getSignature());
            writer.println("\">");

            for (AnnotationData annotation : annotations) {
                annotation.write(writer);
            }
            writer.print("  </item>");
            writer.println();
        }

        abstract boolean isFiltered(@NonNull ApiDatabase database);

        @NonNull
        abstract String getSignature();

        @Override
        public int compareTo(@SuppressWarnings("NullableProblems") @NonNull Item item) {
            String signature1 = getSignature();
            String signature2 = item.getSignature();

            // IntelliJ's sorting order is not on the escaped HTML but the original
            // signatures, which means android.os.AsyncTask<Params,Progress,Result>
            // should appear *after* android.os.AsyncTask.Status, which when the <'s are
            // escaped it does not
            signature1 = signature1.replace('&', '.');
            signature2 = signature2.replace('&', '.');

            return signature1.compareTo(signature2);
        }

        @NonNull
        public abstract String getKeepRule();

        @NonNull
        public abstract String getQualifiedClassName();
    }

    private static class ClassItem extends Item {
        private ClassItem(@Nullable PsiClass psiClass, @NonNull String containingClass) {
            super(psiClass, containingClass);
        }

        @NonNull
        static ClassItem create(@Nullable PsiClass psiClass, @NonNull String classFqn) {
            return new ClassItem(psiClass, classFqn);
        }

        @Override
        boolean isFiltered(@NonNull ApiDatabase database) {
            return !database.hasClass(containingClass);
        }

        @NonNull
        @Override
        String getSignature() {
            return escapeXml(containingClass);
        }

        @NonNull
        @Override
        public String getKeepRule() {
            // See http://proguard.sourceforge.net/manual/usage.html#classspecification
            return "-keep " + ClassKind.forClass(psiClass).getKeepType() + " " +
                    containingClass + "\n";
        }

        @NonNull
        @Override
        public String getQualifiedClassName() {
            return containingClass;
        }

        @Override
        public String toString() {
            return "Class " + containingClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ClassItem that = (ClassItem) o;

            return containingClass.equals(that.containingClass);
        }

        @Override
        public int hashCode() {
            return containingClass.hashCode();
        }
    }

    private static class PackageItem extends Item {
        private PackageItem(@NonNull String containingClass) {
            super(null, containingClass);
        }

        @NonNull
        static PackageItem create(@NonNull String fqn) {
            return new PackageItem(fqn);
        }

        @Override
        boolean isFiltered(@NonNull ApiDatabase database) {
            return !database.hasPackage(containingClass);
        }

        @NonNull
        @Override
        String getSignature() {
            return escapeXml(containingClass);
        }

        @NonNull
        @Override
        public String getKeepRule() {
            return "";
        }

        @NonNull
        @Override
        public String getQualifiedClassName() {
            return containingClass;
        }

        @Override
        public String toString() {
            return "Package " + containingClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PackageItem that = (PackageItem) o;

            return containingClass.equals(that.containingClass);
        }

        @Override
        public int hashCode() {
            return containingClass.hashCode();
        }
    }

    private static class FieldItem extends Item {

        @NonNull
        public final String fieldName;

        @Nullable
        public final String fieldType;

        private FieldItem(@Nullable PsiClass psiClass, @NonNull String containingClass,
                @NonNull String fieldName, @Nullable String fieldType) {
            super(psiClass, containingClass);
            this.fieldName = fieldName;
            this.fieldType = fieldType;
        }

        @Nullable
        static FieldItem create(@Nullable PsiClass psiClass, @Nullable String classFqn,
                @NonNull PsiField field) {
            if (classFqn == null) {
                return null;
            }
            String name = field.getName();
            String type = getVariableType(field);
            if (name != null && type != null) {
                return new FieldItem(psiClass, classFqn, name, type);
            }
            return null;
        }

        @Override
        boolean isFiltered(@NonNull ApiDatabase database) {
            return !database.hasField(containingClass, fieldName);
        }

        @NonNull
        @Override
        String getSignature() {
            return escapeXml(containingClass) + ' ' + fieldName;
        }

        @NonNull
        @Override
        public String getKeepRule() {
            if (fieldType == null) {
                return ""; // imported item; these can't have keep rules
            }
            // See http://proguard.sourceforge.net/manual/usage.html#classspecification
            return "-keep " + ClassKind.forClass(psiClass).getKeepType() + " " + containingClass +
                    " {\n    " + fieldType + " " + fieldName + "\n}\n";
        }

        @NonNull
        @Override
        public String getQualifiedClassName() {
            return containingClass;
        }

        @Override
        public String toString() {
            return "Field " + containingClass + "#" + fieldName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FieldItem that = (FieldItem) o;

            return containingClass.equals(that.containingClass) &&
                    fieldName.equals(that.fieldName);
        }

        @Override
        public int hashCode() {
            int result = fieldName.hashCode();
            result = 31 * result + containingClass.hashCode();
            return result;
        }
    }

    private static class MethodItem extends Item {

        @NonNull
        public final String methodName;

        @NonNull
        public final String parameterList;

        @Nullable
        public final String returnType;

        public final boolean isConstructor;

        private MethodItem(
                @Nullable PsiClass psiClass,
                @NonNull String containingClass,
                @Nullable String returnType,
                @NonNull String methodName,
                @NonNull String parameterList,
                boolean isConstructor) {
            super(psiClass, containingClass);
            this.returnType = returnType;
            this.methodName = methodName;
            this.parameterList = parameterList;
            this.isConstructor = isConstructor;
        }

        @NonNull
        public String getName() {
            return methodName;
        }

        @Nullable
        static MethodItem create(
                @Nullable PsiClass psiClass,
                @Nullable String classFqn,
                @NonNull PsiMethod declaration) {
            if (classFqn == null) {
                return null;
            }
            String returnType = getReturnType(declaration);
            String methodName = getMethodName(declaration);
            if (returnType == null) {
                return null;
            }
            String parameterList = getParameterList(declaration);

            return new MethodItem(psiClass, classFqn, returnType,
                    methodName, parameterList, declaration.isConstructor());
        }

        @NonNull
        @Override
        String getSignature() {
            StringBuilder sb = new StringBuilder(100);
            sb.append(escapeXml(containingClass));
            sb.append(' ');

            if (isConstructor) {
                sb.append(escapeXml(methodName));
            } else {
                assert returnType != null;
                sb.append(escapeXml(returnType));
                sb.append(' ');
                sb.append(escapeXml(methodName));
            }

            sb.append('(');

            // The signature must match *exactly* the formatting used by IDEA,
            // since it looks up external annotations in a map by this key.
            // Therefore, it is vital that the parameter list uses exactly one
            // space after each comma between parameters, and *no* spaces between
            // generics variables, e.g. foo(Map<A,B>, int)

            // Insert spaces between commas, but not in generics signatures
            int balance = 0;
            for (int i = 0, n = parameterList.length(); i < n; i++) {
                char c = parameterList.charAt(i);
                if (c == '<') {
                    balance++;
                    sb.append("&lt;");
                } else if (c == '>') {
                    balance--;
                    sb.append("&gt;");
                } else if (c == ',') {
                    sb.append(',');
                    if (balance == 0) {
                        sb.append(' ');
                    }
                } else {
                    sb.append(c);
                }
            }
            sb.append(')');
            return sb.toString();
        }

        @Override
        boolean isFiltered(@NonNull ApiDatabase database) {
            return !database.hasMethod(containingClass, methodName, parameterList);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MethodItem that = (MethodItem) o;

            return isConstructor == that.isConstructor && containingClass
                    .equals(that.containingClass) && methodName.equals(that.methodName)
                    && parameterList.equals(that.parameterList);

        }

        @Override
        public int hashCode() {
            int result = methodName.hashCode();
            result = 31 * result + containingClass.hashCode();
            result = 31 * result + parameterList.hashCode();
            result = 31 * result + (returnType != null ? returnType.hashCode() : 0);
            result = 31 * result + (isConstructor ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Method " + containingClass + "#" + methodName;
        }

        @NonNull
        @Override
        public String getKeepRule() {
            // See http://proguard.sourceforge.net/manual/usage.html#classspecification
            StringBuilder sb = new StringBuilder();
            sb.append("-keep ");
            sb.append(ClassKind.forClass(psiClass).getKeepType());
            sb.append(" ");
            sb.append(containingClass);
            sb.append(" {\n");
            sb.append("    ");
            if (isConstructor) {
                sb.append("<init>");
            } else {
                sb.append(returnType);
                sb.append(" ");
                sb.append(methodName);
            }
            sb.append("(");
            sb.append(parameterList);
            sb.append(")\n");
            sb.append("}\n");

            return sb.toString();
        }

        @NonNull
        @Override
        public String getQualifiedClassName() {
            return containingClass;
        }
    }

    @Nullable
    private static String getReturnType(PsiMethod method) {
        if (method.isConstructor()) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                return containingClass.getName();
            }
        } else {
            PsiType returnType = method.getReturnType();
            if (returnType != null) {
                return returnType.getCanonicalText();
            }
        }

        return null;
    }

    @Nullable
    private static String getVariableType(PsiVariable variable) {
        PsiType type = variable.getType();
        return type.getCanonicalText();
    }

    private static String getMethodName(@NonNull PsiMethod method) {
        if (method.isConstructor()) {
            return method.getName();
        }

        return method.getName();
    }

    @NonNull
    private static String getParameterList(PsiMethod method) {
        // Create compact type signature (no spaces around commas or generics arguments)
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        PsiParameterList parameterList = method.getParameterList();
        for (PsiParameter parameter : parameterList.getParameters()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(',');
            }

            PsiType type = parameter.getType();
            sb.append(type.getCanonicalText());
        }
        return sb.toString();
    }


    private static class ParameterItem extends MethodItem {
        @NonNull
        public final String argIndex;

        private ParameterItem(
                @Nullable PsiClass psiClass,
                @NonNull String containingClass,
                @Nullable String returnType,
                @NonNull String methodName,
                @NonNull String parameterList,
                boolean isConstructor,
                @NonNull String argIndex) {
            super(psiClass, containingClass, returnType, methodName, parameterList, isConstructor);
            this.argIndex = argIndex;
        }

        @Nullable
        static ParameterItem create(
                @Nullable PsiClass psiClass,
                @Nullable String classFqn,
                @NonNull PsiMethod method,
                @NonNull PsiParameter parameter,
                int index) {
            if (classFqn == null) {
                return null;
            }

            String methodName = getMethodName(method);
            String returnType = getReturnType(method);
            if (methodName == null || returnType == null) {
                return null;
            }
            String parameterList = getParameterList(method);
            String argNum = Integer.toString(index);

            return new ParameterItem(psiClass, classFqn, returnType, methodName, parameterList,
                    method.isConstructor(), argNum);
        }

        @NonNull
        @Override
        String getSignature() {
            return super.getSignature() + ' ' + argIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            ParameterItem that = (ParameterItem) o;

            return argIndex.equals(that.argIndex);

        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + argIndex.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Parameter #" + argIndex + " in " + super.toString();
        }

        @NonNull
        @Override
        public String getKeepRule() {
            return "";
        }
    }

    /**
     * Returns true if the given javadoc contains a {@code @hide} marker
     *
     * @param docComment the javadoc
     * @return true if the javadoc contains a hide marker
     */
    private static boolean javadocContainsHide(@Nullable PsiDocComment docComment) {
        if (docComment != null) {
            String text = docComment.getText();
            if (text.contains("@hide")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if this type declaration for a typedef is hidden (e.g. should not
     * be extracted into an external annotation database)
     *
     * @param declaration the type declaration
     * @return true if the type is hidden
     */
    @SuppressWarnings("RedundantIfStatement")
    public static boolean isHiddenTypeDef(@NonNull PsiClass declaration) {
        PsiModifierList modifierList = declaration.getModifierList();
        if (modifierList == null || !modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return true;
        }

        if (REMOVE_HIDDEN_TYPEDEFS && javadocContainsHide(declaration.getDocComment())) {
            return true;
        }

        return false;
    }

    private class AnnotationVisitor extends JavaRecursiveElementVisitor {
        private List<String> privateTypedefs = Lists.newArrayList();
        private final boolean requireHide;
        private final boolean requireSourceRetention;

        public AnnotationVisitor(boolean requireHide, boolean requireSourceRetention) {
            this.requireHide = requireHide;
            this.requireSourceRetention = requireSourceRetention;
        }

        public List<String> getPrivateTypedefClasses() {
            return privateTypedefs;
        }

        @Override
        public void visitMethod(@NonNull PsiMethod method) {
            // Not calling super: don't recurse inside methods

            PsiModifierList modifierList = method.getModifierList();
            PsiClass containingClass = method.getContainingClass();
            if (hasRelevantAnnotations(modifierList)) {
                String fqn = getFqn(containingClass);
                MethodItem item = MethodItem.create(containingClass, fqn, method);
                if (item != null) {
                    addItem(fqn, item);

                    // Deliberately skip findViewById()'s return nullability
                    // for now; it's true that findViewById can return null,
                    // but that means all code which does findViewById(R.id.foo).something()
                    // will be flagged as potentially throwing an NPE, and many developers
                    // will do this when they *know* that the id exists (in which case
                    // the method won't return null.)
                    boolean skipReturnAnnotations = false;
                    if ("findViewById".equals(item.getName())) {
                        skipReturnAnnotations = true;
                        if (item.annotations.isEmpty()) {
                            // No other annotations so far: just remove it
                            removeItem(fqn, item);
                        }
                    }

                    if (!skipReturnAnnotations) {
                        addAnnotations(modifierList, item);
                    }
                }
            }

            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            int index = 0;
            for (PsiParameter parameter : parameters) {
                PsiModifierList parameterModifierList = parameter.getModifierList();
                if (hasRelevantAnnotations(parameterModifierList)) {
                    String fqn = getFqn(containingClass);
                    Item item = ParameterItem.create(containingClass, fqn, method, parameter,
                            index);
                    if (item != null) {
                        addItem(fqn, item);
                        addAnnotations(parameterModifierList, item);
                    }
                }
                index++;
            }
        }

        @Override
        public void visitField(PsiField field) {
            // Not calling super: don't recurse inside field (e.g. field initializer)
            //super.visitField(field);
            PsiModifierList modifierList = field.getModifierList();
            if (hasRelevantAnnotations(modifierList)) {
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null) {
                    String fqn = getFqn(containingClass);
                    Item item = FieldItem.create(containingClass, fqn, field);
                    if (item != null) {
                        addItem(fqn, item);
                        addAnnotations(modifierList, item);
                    }
                }
            }
        }

        @Override
        public void visitClassInitializer(PsiClassInitializer initializer) {
            // Don't look inside
        }

        @Override
        public void visitImportList(PsiImportList list) {
            // Don't look inside
        }

        @Override
        public void visitPackageStatement(PsiPackageStatement statement) {
            // Extract package. PSI doesn't expose the fact that for a package-info
            // the modifier list is one of the children of the package statement
            PsiElement curr = statement.getFirstChild();
            if (curr instanceof PsiModifierList) {
                // Is it a package-info.java file?
                PsiModifierList modifierList = (PsiModifierList) curr;
                if (hasRelevantAnnotations(modifierList)) {
                    String fqn = statement.getPackageName();
                    if (fqn != null) {
                        PackageItem item = PackageItem.create(fqn);
                        addPackage(fqn, item);
                        addAnnotations(modifierList, item);
                    }
                }
            }
        }

        @Override
        public void visitDocComment(PsiDocComment comment) {
            // Don't look inside
        }

        @Override
        public void visitClass(PsiClass aClass) {
            super.visitClass(aClass);

            if (aClass instanceof PsiAnonymousClass) {
                return;
            }

            if (aClass.isAnnotationType()) {
                // Let's see if it's a typedef
                PsiModifierList modifierList = aClass.getModifierList();
                if (modifierList != null) {
                    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                        String fqn = annotation.getQualifiedName();
                        if (isNestedAnnotation(fqn)) {
                            if (requireHide && !javadocContainsHide(aClass.getDocComment())) {
                                Extractor.warning(aClass.getQualifiedName()
                                        + ": This typedef annotation should specify @hide in a "
                                        + "doc comment");
                            }
                            if (requireSourceRetention
                                    && !hasSourceRetention(aClass)) {
                                Extractor.warning(aClass.getQualifiedName()
                                        + ": The typedef annotation should have "
                                        + "@Retention(RetentionPolicy.SOURCE)");
                            }
                            if (isHiddenTypeDef(aClass)) {
                                String cls = LintUtils.getInternalName(aClass);
                                if (!privateTypedefs.contains(cls)) {
                                    privateTypedefs.add(cls);
                                }
                            }

                            break;
                        }
                    }
                }
            }
            if (aClass.isAnnotationType()
                    // Public typedef annotation need to be kept; they're not
                    // removed by TypedefCollector#recordTypedefs so users may
                    // end up referencing the typedef annotation itself
                    && isHiddenTypeDef(aClass)) {
                return;
            }

            PsiModifierList modifierList = aClass.getModifierList();
            if (hasRelevantAnnotations(modifierList)) {
                String fqn = getFqn(aClass);
                if (fqn != null) {
                    Item item = ClassItem.create(aClass, fqn);
                    addItem(fqn, item);
                    addAnnotations(modifierList, item);
                }
            }
        }
    }
}
