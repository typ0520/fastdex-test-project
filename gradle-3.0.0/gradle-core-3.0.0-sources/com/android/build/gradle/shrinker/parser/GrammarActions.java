/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.shrinker.parser;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Grammar actions for the ProGuard config files parser, forked from Jack. */
@SuppressWarnings("unused") // These methods are called by the ANTLR-generated parser.
public class GrammarActions {
    private static Logger logger = LoggerFactory.getLogger(GrammarActions.class);

    enum FilterSeparator {
        GENERAL(".", ".*"),
        FILE("[^/]", "[^/]*"),
        CLASS("[^/]", "[^/]*"), // Shrinker works on "internal" class names.
        ATTRIBUTE(".", ".*");

        /** Represents the pattern equivalent to Proguard's "?" */
        @NonNull private final String singleCharWildcard;

        /** Represents the pattern equivalent to Proguard's "*" */
        @NonNull private final String multipleCharWildcard;

        FilterSeparator(@NonNull String singleCharWildcard, @NonNull String multipleCharWildcard) {
            this.singleCharWildcard = singleCharWildcard;
            this.multipleCharWildcard = multipleCharWildcard;
        }
    }

    public static void parse(
            @NonNull File proguardFile,
            @NonNull ProguardFlags flags,
            @NonNull UnsupportedFlagsHandler flagsHandler)
            throws RecognitionException {
        ProguardParser parser = createParserFromFile(proguardFile);
        parser.prog(flags, flagsHandler, proguardFile.getParentFile().getPath());
    }

    public static void parse(
            @NonNull String input,
            @NonNull ProguardFlags flags,
            @NonNull UnsupportedFlagsHandler flagsHandler)
            throws RecognitionException {
        ProguardParser parser = createParserFromString(input);
        parser.prog(flags, flagsHandler, null);
    }

    static void include(
            @NonNull String fileName,
            @NonNull String baseDirectory,
            @NonNull ProguardFlags flags,
            @NonNull UnsupportedFlagsHandler flagsHandler)
            throws RecognitionException {
        parse(getFileFromBaseDir(baseDirectory, fileName), flags, flagsHandler);
    }

    static void dontWarn(
            @NonNull ProguardFlags flags, @NonNull List<FilterSpecification> classSpec) {
        flags.dontWarn(classSpec);
    }

    static void ignoreWarnings(@NonNull ProguardFlags flags) {
        flags.setIgnoreWarnings(true);
    }

    static void addKeepClassMembers(
            @NonNull ProguardFlags flags,
            @NonNull ClassSpecification classSpecification,
            @NonNull KeepModifier keepModifier) {
        classSpecification.setKeepModifier(keepModifier);
        flags.addKeepClassMembers(classSpecification);
    }

    static void addKeepClassSpecification(
            @NonNull ProguardFlags flags,
            @NonNull ClassSpecification classSpecification,
            @NonNull KeepModifier keepModifier) {
        classSpecification.setKeepModifier(keepModifier);
        flags.addKeepClassSpecification(classSpecification);
    }

    static void addKeepClassesWithMembers(
            @NonNull ProguardFlags flags,
            @NonNull ClassSpecification classSpecification,
            @NonNull KeepModifier keepModifier) {
        classSpecification.setKeepModifier(keepModifier);
        flags.addKeepClassesWithMembers(classSpecification);
    }

    static void addAccessFlag(
            @NonNull ModifierSpecification modSpec,
            @NonNull ModifierSpecification.AccessFlag accessFlag,
            boolean hasNegator) {
        modSpec.addAccessFlag(accessFlag, hasNegator);
    }

    static void addModifier(
            @NonNull ModifierSpecification modSpec,
            @NonNull ModifierSpecification.Modifier modifier,
            boolean hasNegator) {
        modSpec.addModifier(modifier, hasNegator);
    }

    @NonNull
    static AnnotationSpecification annotation(
            @NonNull String annotationName, boolean hasNameNegator) {
        NameSpecification name = name(annotationName, FilterSeparator.CLASS);
        name.setNegator(hasNameNegator);
        return new AnnotationSpecification(name);
    }

    @NonNull
    static ClassSpecification classSpec(
            @NonNull List<NameSpecification> classNames,
            @NonNull ClassTypeSpecification classType,
            @Nullable AnnotationSpecification annotation,
            @NonNull ModifierSpecification modifier) {
        ClassSpecification classSpec = new ClassSpecification(classNames, classType, annotation);
        classSpec.setModifier(modifier);
        return classSpec;
    }

    static NameSpecification className(@NonNull String name, boolean hasNameNegator) {
        NameSpecification nameSpec;
        if (name.equals("*")) {
            nameSpec = name("**", FilterSeparator.CLASS);
        } else {
            nameSpec = name(name, FilterSeparator.CLASS);
        }
        nameSpec.setNegator(hasNameNegator);
        return nameSpec;
    }

    @NonNull
    static ClassTypeSpecification classType(int type, boolean hasNegator) {
        ClassTypeSpecification classSpec = new ClassTypeSpecification(type);
        classSpec.setNegator(hasNegator);
        return classSpec;
    }

    @NonNull
    static InheritanceSpecification createInheritance(
            /*@NonNull*/ String className,
            boolean hasNameNegator,
            @NonNull AnnotationSpecification annotationType) {
        NameSpecification nameSpec = name(className, FilterSeparator.CLASS);
        nameSpec.setNegator(hasNameNegator);
        return new InheritanceSpecification(nameSpec, annotationType);
    }

    static void field(
            @NonNull ClassSpecification classSpec,
            @Nullable AnnotationSpecification annotationType,
            @Nullable String typeSignature,
            @NonNull String name,
            @NonNull ModifierSpecification modifier) {
        NameSpecification typeSignatureSpec = null;
        if (typeSignature != null) {
            typeSignatureSpec = name(typeSignature, FilterSeparator.CLASS);
        } else {
            checkState(name.equals("*"), "No type signature, but name is not <fields> or *.");
            name = "*";
        }

        classSpec.add(
                new FieldSpecification(
                        name(name, FilterSeparator.GENERAL),
                        modifier,
                        typeSignatureSpec,
                        annotationType));
    }

    static void fieldOrAnyMember(
            @NonNull ClassSpecification classSpec,
            @Nullable AnnotationSpecification annotationType,
            @Nullable String typeSig,
            @NonNull String name,
            @NonNull ModifierSpecification modifier) {
        if (typeSig == null) {
            assert name.equals("*");
            // This is the "any member" case, we have to handle methods as well.
            method(
                    classSpec,
                    annotationType,
                    getSignature("***", 0),
                    "*",
                    "\\(" + getSignature("...", 0) + "\\)",
                    modifier);
        }
        field(classSpec, annotationType, typeSig, name, modifier);
    }

    static void filter(
            @NonNull List<FilterSpecification> filter,
            boolean negator,
            @NonNull String filterName,
            @NonNull FilterSeparator separator) {
        filter.add(new FilterSpecification(name(filterName, separator), negator));
    }

    @NonNull
    static String getSignature(@NonNull String name, int dim) {
        StringBuilder sig = new StringBuilder();

        for (int i = 0; i < dim; i++) {
            sig.append("\\[");
        }

        // ... matches any number of arguments of any type
        switch (name) {
            case "...":
                sig.append(".*");
                break;
            case "***":
                // *** matches any type (primitive or non-primitive, array or non-array)
                sig.append(".*");
                break;
            case "%":
                sig.append("(B|C|D|F|I|J|S|Z)");
                break;
            case "boolean":
                sig.append("Z");
                break;
            case "byte":
                sig.append("B");
                break;
            case "char":
                sig.append("C");
                break;
            case "short":
                sig.append("S");
                break;
            case "int":
                sig.append("I");
                break;
            case "float":
                sig.append("F");
                break;
            case "double":
                sig.append("D");
                break;
            case "long":
                sig.append("J");
                break;
            case "void":
                sig.append("V");
                break;
            default:
                sig.append("L")
                        .append(convertNameToPattern(name, FilterSeparator.CLASS))
                        .append(";");
                break;
        }

        return sig.toString();
    }

    static void method(
            @NonNull ClassSpecification classSpec,
            @Nullable AnnotationSpecification annotationType,
            @Nullable String typeSig,
            @NonNull String name,
            @NonNull String signature,
            @Nullable ModifierSpecification modifier) {
        String fullName = "^" + convertNameToPattern(name, FilterSeparator.CLASS);
        fullName += ":";
        fullName += signature;
        if (typeSig != null) {
            fullName += typeSig;
        } else {
            fullName += "V";
        }
        fullName += "$";
        Pattern pattern = Pattern.compile(fullName);
        classSpec.add(
                new MethodSpecification(new NameSpecification(pattern), modifier, annotationType));
    }

    @NonNull
    static NameSpecification name(@NonNull String name, @NonNull FilterSeparator filterSeparator) {
        String transformedName = "^" + convertNameToPattern(name, filterSeparator) + "$";

        Pattern pattern = Pattern.compile(transformedName);
        return new NameSpecification(pattern);
    }

    static void unsupportedFlag(String flag) {
        throw new IllegalArgumentException(
                String.format("Flag %s is not supported by the built-in shrinker.", flag));
    }

    static void ignoredFlag(String flag, boolean printWarning) {
        if (printWarning) {
            logger.warn("Flag {} is ignored by the built-in shrinker.", flag);
        }
    }

    static void target(ProguardFlags flags, String target) {
        flags.target(target);
    }

    static void whyAreYouKeeping(ProguardFlags flags, ClassSpecification classSpecification) {
        flags.whyAreYouKeeping(classSpecification);
    }

    static void dontOptimize(ProguardFlags flags) {
        flags.setDontOptimize(true);
    }

    static void dontShrink(ProguardFlags flags) {
        flags.setDontShrink(true);
    }

    static void dontObfuscate(ProguardFlags flags) {
        flags.setDontObfuscate(true);
    }

    @NonNull
    private static String convertNameToPattern(
            @NonNull String name, @NonNull FilterSeparator separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            switch (c) {
                case '.':
                    if (separator == FilterSeparator.CLASS) {
                        sb.append('/');
                    } else {
                        sb.append('.');
                    }
                    break;
                case '?':
                    // ? matches any single character in a name
                    // but not the package separator
                    sb.append(separator.singleCharWildcard);
                    break;
                case '*':
                    int j = i + 1;
                    if (j < name.length() && name.charAt(j) == '*') {
                        // ** matches any part of a name, possibly containing
                        // any number of package separators or directory separators
                        sb.append(".*");
                        i++;
                    } else {
                        // * matches any part of a name not containing
                        // the package separator or directory separator
                        sb.append(separator.multipleCharWildcard);
                    }
                    break;
                case '$':
                    sb.append("\\$");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    @NonNull
    private static ProguardParser createParserCommon(@NonNull CharStream stream) {
        ProguardLexer lexer = new ProguardLexer(stream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new ProguardParser(tokens);
    }

    @NonNull
    private static ProguardParser createParserFromFile(@NonNull File file) {
        try {
            return createParserCommon(new ANTLRFileStream(file.getPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static ProguardParser createParserFromString(@NonNull String input) {
        return createParserCommon(new ANTLRStringStream(input));
    }

    @NonNull
    private static File getFileFromBaseDir(@NonNull String baseDir, @NonNull String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(baseDir, path);
        }
        return file;
    }
}
