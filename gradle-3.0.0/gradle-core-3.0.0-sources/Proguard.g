grammar Proguard;

options{
  k = 3;
}

tokens {
  NEGATOR = '!';
}

@header {
package com.android.build.gradle.shrinker.parser;
import static com.android.build.gradle.shrinker.parser.ModifierSpecification.*;
import static org.objectweb.asm.Opcodes.*;
import com.android.build.gradle.shrinker.parser.GrammarActions;
import com.android.build.gradle.shrinker.parser.GrammarActions.FilterSeparator;
}

@lexer::header {
package com.android.build.gradle.shrinker.parser;
}

@members {
  @Override
  public void emitErrorMessage(String msg) {
    throw new ProguardParserException(msg);
  }
}

@lexer::members {
  @Override
  public void emitErrorMessage(String msg) {
    throw new ProguardParserException(msg);
  }
}

prog [ProguardFlags flags, UnsupportedFlagsHandler flagsHandler, String baseDirectory]
  :
  (
    ('-basedirectory' baseDir=NAME {baseDirectory=$baseDir.text;})
    | ('-include'|'@') proguardFile=NAME {GrammarActions.include($proguardFile.text, baseDirectory, $flags, $flagsHandler);}
    | ('-keepclassmembers' keepModifier=keepOptionModifier classSpec=classSpecification {GrammarActions.addKeepClassMembers($flags, $classSpec.classSpec, $keepModifier.modifier);})
    | ('-keepclasseswithmembers' keepModifier=keepOptionModifier classSpec=classSpecification {GrammarActions.addKeepClassesWithMembers($flags, $classSpec.classSpec, $keepModifier.modifier);})
    | ('-keep' keepModifier=keepOptionModifier classSpec=classSpecification {GrammarActions.addKeepClassSpecification($flags, $classSpec.classSpec, $keepModifier.modifier);})
    | (unFlag=unsupportedFlag {flagsHandler.unsupportedFlag($unFlag.text);})
    | ('-dontwarn' {List<FilterSpecification> class_filter = new ArrayList<FilterSpecification>();} filter[class_filter, FilterSeparator.CLASS] {GrammarActions.dontWarn($flags, class_filter);})
    | ('-ignorewarnings' {GrammarActions.ignoreWarnings($flags);})
    | ('-target' target=NAME {GrammarActions.target(flags, $target.text);})
    | ('-whyareyoukeeping' classSpec=classSpecification { GrammarActions.whyAreYouKeeping(flags, $classSpec.classSpec); })
    | ('-dontshrink' { GrammarActions.dontShrink(flags); } )
    | ('-dontoptimize' { GrammarActions.dontOptimize(flags); } )
    | ('-dontobfuscate' { GrammarActions.dontObfuscate(flags); })
  )*
  EOF
  ;
  catch [RecognitionException e] {
    throw e;
  }

private unsupportedFlag
  :
  (
      '-allowaccessmodification'
    | '-classobfuscationdictionary' classObfuscationDictionary=NAME
    | '-dontpreverify'
    | '-dontskipnonpubliclibraryclasses'
    | '-dontskipnonpubliclibraryclassmembers'
    | '-dontusemixedcaseclassnames'
    | '-forceprocessing'
    | '-injars' inJars=classpath
    | '-keepparameternames'
    | '-libraryjars' libraryJars=classpath
    | '-mergeinterfacesaggressively'
    | '-microedition'
    | '-obfuscationdictionary' obfuscationDictionary=NAME
    | '-outjars' outJars=classpath
    | '-overloadaggressively'
    | '-packageobfuscationdictionary' packageObfuscationDictionary=NAME
    | '-printmapping' outputMapping=NAME?
    | '-skipnonpubliclibraryclasses'
    | '-useuniqueclassmembernames'
    | '-verbose'
    | ('-adaptclassstrings' {List<FilterSpecification> filter = new ArrayList<FilterSpecification>();} filter[filter, FilterSeparator.GENERAL])
    | ('-adaptresourcefilecontents' {List<FilterSpecification> file_filter = new ArrayList<FilterSpecification>();} filter[file_filter, FilterSeparator.FILE] )
    | ('-adaptresourcefilenames' {List<FilterSpecification> file_filter = new ArrayList<FilterSpecification>();} filter[file_filter, FilterSeparator.FILE] )
    | ('-applymapping' mapping=NAME )
    | ('-assumenosideeffects' classSpecification)
    | ('-dontnote' {List<FilterSpecification> class_filter = new ArrayList<FilterSpecification>();} filter[class_filter, FilterSeparator.CLASS])
    | ('-dump' NAME?) //[filename]
    | ('-flattenpackagehierarchy' ('\'' newPackage=NAME? '\'')? )
    | ('-keepattributes' {List<FilterSpecification> attribute_filter = new ArrayList<FilterSpecification>();} filter[attribute_filter, FilterSeparator.ATTRIBUTE] )
    | ('-keepclasseswithmembernames' keepModifier=keepOptionModifier classSpec=classSpecification  )
    | ('-keepclassmembernames' classSpec=classSpecification  )
    | ('-keepdirectories' {List<FilterSpecification> directory_filter = new ArrayList<FilterSpecification>();} filter[directory_filter, FilterSeparator.FILE])
    | ('-keepnames' classSpec=classSpecification )
    | ('-keeppackagenames' {List<FilterSpecification> package_filter = new ArrayList<FilterSpecification>();} filter[package_filter, FilterSeparator.GENERAL] )
    | ('-optimizationpasses' NAME) //n
    | ('-optimizations' {List<FilterSpecification> optimization_filter = new ArrayList<FilterSpecification>();} filter[optimization_filter, FilterSeparator.GENERAL])
    | ('-printconfiguration' NAME?) //[filename]
    | ('-printseeds' seedOutputFile=NAME? )
    | ('-printusage' NAME) //[filename]
    | ('-renamesourcefileattribute' sourceFile=NAME?)
    | ('-repackageclasses' ('\'' newPackage=NAME? '\'')? )
  )
  ;

private classpath
  :  NAME ((':'|';') classpath)?
  ;

private filter [List<FilterSpecification> filter, FilterSeparator separator]
  :
  nonEmptyFilter[filter, separator]
  | {GrammarActions.filter($filter, false, "**", separator);}
  ;


private nonEmptyFilter [List<FilterSpecification> filter, FilterSeparator separator]
@init {
  boolean negator = false;
}
  :
  ((NEGATOR {negator=true;})? NAME {GrammarActions.filter($filter, negator, $NAME.text, separator);} (',' nonEmptyFilter[filter, separator])?)
  ;

private classSpecification returns [ClassSpecification classSpec]
@init{
  ModifierSpecification modifier = new ModifierSpecification();
  boolean hasNameNegator = false;
}
  :
  (annotation)?
  cType=classModifierAndType[modifier]
  classNames {classSpec = GrammarActions.classSpec($classNames.names, cType, $annotation.annotSpec, modifier);}
  (inheritanceSpec=inheritance {classSpec.setInheritance(inheritanceSpec);})?
  members[classSpec]?
  ;

private classNames returns [List<NameSpecification> names]
@init{
  names = new ArrayList<NameSpecification>();
}
  :
  firstName=className {names.add($firstName.nameSpec);}
  (',' otherName=className {names.add($otherName.nameSpec);} )*
;

private className returns [NameSpecification nameSpec]
@init{
    boolean hasNameNegator = false;
}
  :
  (NEGATOR {hasNameNegator = true;})?
  NAME {nameSpec=GrammarActions.className($NAME.text, hasNameNegator);}
;

private classModifierAndType[ModifierSpecification modifier] returns [ClassTypeSpecification cType]
@init{
  boolean hasNegator = false;
}
  :
  (NEGATOR {hasNegator = true;})?
  (
  'public' {GrammarActions.addAccessFlag(modifier, AccessFlag.PUBLIC, hasNegator);} cmat=classModifierAndType[modifier] {cType = $cmat.cType;}
  | 'abstract' {GrammarActions.addModifier(modifier, Modifier.ABSTRACT, hasNegator);} cmat=classModifierAndType[modifier] {cType = $cmat.cType;}
  | 'final' {GrammarActions.addModifier(modifier, Modifier.FINAL, hasNegator);} cmat=classModifierAndType[modifier] {cType = $cmat.cType;}
  | classType {cType=GrammarActions.classType($classType.type, hasNegator); }
  )
  ;

private classType returns [int type]
@init {
  $type = 0;
}
  :
  ('@' {$type |= ACC_ANNOTATION;})?
  ('interface' {$type |= ACC_INTERFACE;}
  | 'enum' {$type |= ACC_ENUM;}
  | 'class'
  )
  ;

private members [ClassSpecification classSpec]
  :
  '{'
    member[classSpec]*
  '}'
  ;

private member [ClassSpecification classSpec]
  :
    annotation? modifiers
    (
      (typeSig=type)? name=(NAME|'<init>') (signature=arguments {GrammarActions.method(classSpec, $annotation.annotSpec, typeSig, $name.text, signature, $modifiers.modifiers);}
                  | {GrammarActions.fieldOrAnyMember(classSpec, $annotation.annotSpec, typeSig, $name.text, $modifiers.modifiers);})
      | '<methods>' {GrammarActions.method(classSpec, $annotation.annotSpec,
          GrammarActions.getSignature("***", 0), "*", "\\("+ GrammarActions.getSignature("...", 0) + "\\)",
          $modifiers.modifiers);}
      | '<fields>' {GrammarActions.field(classSpec, $annotation.annotSpec, null, "*", $modifiers.modifiers);}
    ) ';'
  ;

private annotation returns [AnnotationSpecification annotSpec]
@init{
  boolean hasNameNegator = false;
}
  :  '@' (NEGATOR {hasNameNegator = true;})? NAME {$annotSpec = GrammarActions.annotation($NAME.text, hasNameNegator);};

private modifiers returns [ModifierSpecification modifiers]
@init{
  modifiers = new ModifierSpecification();
}
  :
  modifier[modifiers]*
  ;

private modifier [ModifierSpecification modifiers]
@init{
  boolean hasNegator = false;
}
  :
  (NEGATOR {hasNegator = true;})?
  (
    'public' {modifiers.addAccessFlag(AccessFlag.PUBLIC, hasNegator);}
    | 'private' {modifiers.addAccessFlag(AccessFlag.PRIVATE, hasNegator);}
    | 'protected' {modifiers.addAccessFlag(AccessFlag.PROTECTED, hasNegator);}
    | 'static' {modifiers.addModifier(Modifier.STATIC, hasNegator);}
    | 'synchronized' {modifiers.addModifier(Modifier.SYNCHRONIZED, hasNegator);}
    | 'volatile' {modifiers.addModifier(Modifier.VOLATILE, hasNegator);}
    | 'native' {modifiers.addModifier(Modifier.NATIVE, hasNegator);}
    | 'abstract' {modifiers.addModifier(Modifier.ABSTRACT, hasNegator);}
    | 'strictfp' {modifiers.addModifier(Modifier.STRICTFP, hasNegator);}
    | 'final' {modifiers.addModifier(Modifier.FINAL, hasNegator);}
    | 'transient' {modifiers.addModifier(Modifier.TRANSIENT, hasNegator);}
    | 'synthetic' {modifiers.addModifier(Modifier.SYNTHETIC, hasNegator);}
    | 'bridge' {modifiers.addModifier(Modifier.BRIDGE, hasNegator);}
    | 'varargs' {modifiers.addModifier(Modifier.VARARGS, hasNegator);}
  )
  ;

private inheritance returns [InheritanceSpecification inheritanceSpec]
@init{
  boolean hasNameNegator = false;
}
  :
  ('extends' | 'implements')
  annotation? (NEGATOR {hasNameNegator = true;})? NAME {inheritanceSpec = GrammarActions.createInheritance($NAME.text, hasNameNegator, $annotation.annotSpec);};

private arguments returns [String signature]
  :
  '(' {signature = "\\(";}
    (
      (
        parameterSig=type {signature += parameterSig;}
        (',' parameterSig=type {signature += parameterSig;})*
        )?
      )
    ')' {signature += "\\)";}
  ;

private type returns [String signature]
@init {
  int dim = 0;
}
  :
  (
    typeName=('%' | NAME) ('[]' {dim++;})* {String sig = $typeName.text; signature = GrammarActions.getSignature(sig == null ? "" : sig, dim);}
  )
  ;

private keepOptionModifier returns [KeepModifier modifier]
@init {
  modifier = new KeepModifier();
}
  : (','
  ('allowshrinking' {modifier.setAllowShrinking();}
  | 'allowoptimization' // Optimizations not supported.
  | 'includedescriptorclasses' // Shrinker does this regardless of the flag.
  | 'allowobfuscation' {modifier.setAllowObfuscation();}))*
  ;

private NAME  : ('a'..'z'|'A'..'Z'|'_'|'0'..'9'|'?'|'$'|'.'|'*'|'/'|'\\'|'-'|'<'|'>')+ ;

LINE_COMMENT
  :  '#' ~( '\r' | '\n' )* {$channel=HIDDEN;}
  ;

private WS  :   ( ' '
        | '\t'
        | '\r'
        | '\n'
        ) {$channel=HIDDEN;}
    ;


