// $ANTLR 3.5.2 Proguard.g 2017-10-25 00:31:45

package com.android.build.gradle.shrinker.parser;
import static com.android.build.gradle.shrinker.parser.ModifierSpecification.*;
import static org.objectweb.asm.Opcodes.*;
import com.android.build.gradle.shrinker.parser.GrammarActions;
import com.android.build.gradle.shrinker.parser.GrammarActions.FilterSeparator;


import org.antlr.runtime.*;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

@SuppressWarnings("all")
public class ProguardParser extends Parser {
	public static final String[] tokenNames = new String[] {
		"<invalid>", "<EOR>", "<DOWN>", "<UP>", "LINE_COMMENT", "NAME", "NEGATOR", 
		"WS", "'%'", "'('", "')'", "','", "'-adaptclassstrings'", "'-adaptresourcefilecontents'", 
		"'-adaptresourcefilenames'", "'-allowaccessmodification'", "'-applymapping'", 
		"'-assumenosideeffects'", "'-basedirectory'", "'-classobfuscationdictionary'", 
		"'-dontnote'", "'-dontobfuscate'", "'-dontoptimize'", "'-dontpreverify'", 
		"'-dontshrink'", "'-dontskipnonpubliclibraryclasses'", "'-dontskipnonpubliclibraryclassmembers'", 
		"'-dontusemixedcaseclassnames'", "'-dontwarn'", "'-dump'", "'-flattenpackagehierarchy'", 
		"'-forceprocessing'", "'-ignorewarnings'", "'-include'", "'-injars'", 
		"'-keep'", "'-keepattributes'", "'-keepclasseswithmembernames'", "'-keepclasseswithmembers'", 
		"'-keepclassmembernames'", "'-keepclassmembers'", "'-keepdirectories'", 
		"'-keepnames'", "'-keeppackagenames'", "'-keepparameternames'", "'-libraryjars'", 
		"'-mergeinterfacesaggressively'", "'-microedition'", "'-obfuscationdictionary'", 
		"'-optimizationpasses'", "'-optimizations'", "'-outjars'", "'-overloadaggressively'", 
		"'-packageobfuscationdictionary'", "'-printconfiguration'", "'-printmapping'", 
		"'-printseeds'", "'-printusage'", "'-renamesourcefileattribute'", "'-repackageclasses'", 
		"'-skipnonpubliclibraryclasses'", "'-target'", "'-useuniqueclassmembernames'", 
		"'-verbose'", "'-whyareyoukeeping'", "':'", "';'", "'<fields>'", "'<init>'", 
		"'<methods>'", "'@'", "'[]'", "'\\''", "'abstract'", "'allowobfuscation'", 
		"'allowoptimization'", "'allowshrinking'", "'bridge'", "'class'", "'enum'", 
		"'extends'", "'final'", "'implements'", "'includedescriptorclasses'", 
		"'interface'", "'native'", "'private'", "'protected'", "'public'", "'static'", 
		"'strictfp'", "'synchronized'", "'synthetic'", "'transient'", "'varargs'", 
		"'volatile'", "'{'", "'}'"
	};
	public static final int EOF=-1;
	public static final int T__8=8;
	public static final int T__9=9;
	public static final int T__10=10;
	public static final int T__11=11;
	public static final int T__12=12;
	public static final int T__13=13;
	public static final int T__14=14;
	public static final int T__15=15;
	public static final int T__16=16;
	public static final int T__17=17;
	public static final int T__18=18;
	public static final int T__19=19;
	public static final int T__20=20;
	public static final int T__21=21;
	public static final int T__22=22;
	public static final int T__23=23;
	public static final int T__24=24;
	public static final int T__25=25;
	public static final int T__26=26;
	public static final int T__27=27;
	public static final int T__28=28;
	public static final int T__29=29;
	public static final int T__30=30;
	public static final int T__31=31;
	public static final int T__32=32;
	public static final int T__33=33;
	public static final int T__34=34;
	public static final int T__35=35;
	public static final int T__36=36;
	public static final int T__37=37;
	public static final int T__38=38;
	public static final int T__39=39;
	public static final int T__40=40;
	public static final int T__41=41;
	public static final int T__42=42;
	public static final int T__43=43;
	public static final int T__44=44;
	public static final int T__45=45;
	public static final int T__46=46;
	public static final int T__47=47;
	public static final int T__48=48;
	public static final int T__49=49;
	public static final int T__50=50;
	public static final int T__51=51;
	public static final int T__52=52;
	public static final int T__53=53;
	public static final int T__54=54;
	public static final int T__55=55;
	public static final int T__56=56;
	public static final int T__57=57;
	public static final int T__58=58;
	public static final int T__59=59;
	public static final int T__60=60;
	public static final int T__61=61;
	public static final int T__62=62;
	public static final int T__63=63;
	public static final int T__64=64;
	public static final int T__65=65;
	public static final int T__66=66;
	public static final int T__67=67;
	public static final int T__68=68;
	public static final int T__69=69;
	public static final int T__70=70;
	public static final int T__71=71;
	public static final int T__72=72;
	public static final int T__73=73;
	public static final int T__74=74;
	public static final int T__75=75;
	public static final int T__76=76;
	public static final int T__77=77;
	public static final int T__78=78;
	public static final int T__79=79;
	public static final int T__80=80;
	public static final int T__81=81;
	public static final int T__82=82;
	public static final int T__83=83;
	public static final int T__84=84;
	public static final int T__85=85;
	public static final int T__86=86;
	public static final int T__87=87;
	public static final int T__88=88;
	public static final int T__89=89;
	public static final int T__90=90;
	public static final int T__91=91;
	public static final int T__92=92;
	public static final int T__93=93;
	public static final int T__94=94;
	public static final int T__95=95;
	public static final int T__96=96;
	public static final int T__97=97;
	public static final int LINE_COMMENT=4;
	public static final int NAME=5;
	public static final int NEGATOR=6;
	public static final int WS=7;

	// delegates
	public Parser[] getDelegates() {
		return new Parser[] {};
	}

	// delegators


	public ProguardParser(TokenStream input) {
		this(input, new RecognizerSharedState());
	}
	public ProguardParser(TokenStream input, RecognizerSharedState state) {
		super(input, state);
	}

	@Override public String[] getTokenNames() { return ProguardParser.tokenNames; }
	@Override public String getGrammarFileName() { return "Proguard.g"; }


	  @Override
	  public void emitErrorMessage(String msg) {
	    throw new ProguardParserException(msg);
	  }



	// $ANTLR start "prog"
	// Proguard.g:37:1: prog[ProguardFlags flags, UnsupportedFlagsHandler flagsHandler, String baseDirectory] : ( ( '-basedirectory' baseDir= NAME ) | ( '-include' | '@' ) proguardFile= NAME | ( '-keepclassmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclasseswithmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keep' keepModifier= keepOptionModifier classSpec= classSpecification ) | (unFlag= unsupportedFlag ) | ( '-dontwarn' filter[class_filter, FilterSeparator.CLASS] ) | ( '-ignorewarnings' ) | ( '-target' target= NAME ) | ( '-whyareyoukeeping' classSpec= classSpecification ) | ( '-dontshrink' ) | ( '-dontoptimize' ) | ( '-dontobfuscate' ) )* EOF ;
	public final void prog(ProguardFlags flags, UnsupportedFlagsHandler flagsHandler, String baseDirectory) throws RecognitionException {
		Token baseDir=null;
		Token proguardFile=null;
		Token target=null;
		KeepModifier keepModifier =null;
		ClassSpecification classSpec =null;
		ParserRuleReturnScope unFlag =null;

		try {
			// Proguard.g:38:3: ( ( ( '-basedirectory' baseDir= NAME ) | ( '-include' | '@' ) proguardFile= NAME | ( '-keepclassmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclasseswithmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keep' keepModifier= keepOptionModifier classSpec= classSpecification ) | (unFlag= unsupportedFlag ) | ( '-dontwarn' filter[class_filter, FilterSeparator.CLASS] ) | ( '-ignorewarnings' ) | ( '-target' target= NAME ) | ( '-whyareyoukeeping' classSpec= classSpecification ) | ( '-dontshrink' ) | ( '-dontoptimize' ) | ( '-dontobfuscate' ) )* EOF )
			// Proguard.g:39:3: ( ( '-basedirectory' baseDir= NAME ) | ( '-include' | '@' ) proguardFile= NAME | ( '-keepclassmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclasseswithmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keep' keepModifier= keepOptionModifier classSpec= classSpecification ) | (unFlag= unsupportedFlag ) | ( '-dontwarn' filter[class_filter, FilterSeparator.CLASS] ) | ( '-ignorewarnings' ) | ( '-target' target= NAME ) | ( '-whyareyoukeeping' classSpec= classSpecification ) | ( '-dontshrink' ) | ( '-dontoptimize' ) | ( '-dontobfuscate' ) )* EOF
			{
			// Proguard.g:39:3: ( ( '-basedirectory' baseDir= NAME ) | ( '-include' | '@' ) proguardFile= NAME | ( '-keepclassmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclasseswithmembers' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keep' keepModifier= keepOptionModifier classSpec= classSpecification ) | (unFlag= unsupportedFlag ) | ( '-dontwarn' filter[class_filter, FilterSeparator.CLASS] ) | ( '-ignorewarnings' ) | ( '-target' target= NAME ) | ( '-whyareyoukeeping' classSpec= classSpecification ) | ( '-dontshrink' ) | ( '-dontoptimize' ) | ( '-dontobfuscate' ) )*
			loop1:
			while (true) {
				int alt1=14;
				switch ( input.LA(1) ) {
				case 18:
					{
					alt1=1;
					}
					break;
				case 33:
				case 70:
					{
					alt1=2;
					}
					break;
				case 40:
					{
					alt1=3;
					}
					break;
				case 38:
					{
					alt1=4;
					}
					break;
				case 35:
					{
					alt1=5;
					}
					break;
				case 12:
				case 13:
				case 14:
				case 15:
				case 16:
				case 17:
				case 19:
				case 20:
				case 23:
				case 25:
				case 26:
				case 27:
				case 29:
				case 30:
				case 31:
				case 34:
				case 36:
				case 37:
				case 39:
				case 41:
				case 42:
				case 43:
				case 44:
				case 45:
				case 46:
				case 47:
				case 48:
				case 49:
				case 50:
				case 51:
				case 52:
				case 53:
				case 54:
				case 55:
				case 56:
				case 57:
				case 58:
				case 59:
				case 60:
				case 62:
				case 63:
					{
					alt1=6;
					}
					break;
				case 28:
					{
					alt1=7;
					}
					break;
				case 32:
					{
					alt1=8;
					}
					break;
				case 61:
					{
					alt1=9;
					}
					break;
				case 64:
					{
					alt1=10;
					}
					break;
				case 24:
					{
					alt1=11;
					}
					break;
				case 22:
					{
					alt1=12;
					}
					break;
				case 21:
					{
					alt1=13;
					}
					break;
				}
				switch (alt1) {
				case 1 :
					// Proguard.g:40:5: ( '-basedirectory' baseDir= NAME )
					{
					// Proguard.g:40:5: ( '-basedirectory' baseDir= NAME )
					// Proguard.g:40:6: '-basedirectory' baseDir= NAME
					{
					match(input,18,FOLLOW_18_in_prog81); 
					baseDir=(Token)match(input,NAME,FOLLOW_NAME_in_prog85); 
					baseDirectory=(baseDir!=null?baseDir.getText():null);
					}

					}
					break;
				case 2 :
					// Proguard.g:41:7: ( '-include' | '@' ) proguardFile= NAME
					{
					if ( input.LA(1)==33||input.LA(1)==70 ) {
						input.consume();
						state.errorRecovery=false;
					}
					else {
						MismatchedSetException mse = new MismatchedSetException(null,input);
						throw mse;
					}
					proguardFile=(Token)match(input,NAME,FOLLOW_NAME_in_prog104); 
					GrammarActions.include((proguardFile!=null?proguardFile.getText():null), baseDirectory, flags, flagsHandler);
					}
					break;
				case 3 :
					// Proguard.g:42:7: ( '-keepclassmembers' keepModifier= keepOptionModifier classSpec= classSpecification )
					{
					// Proguard.g:42:7: ( '-keepclassmembers' keepModifier= keepOptionModifier classSpec= classSpecification )
					// Proguard.g:42:8: '-keepclassmembers' keepModifier= keepOptionModifier classSpec= classSpecification
					{
					match(input,40,FOLLOW_40_in_prog115); 
					pushFollow(FOLLOW_keepOptionModifier_in_prog119);
					keepModifier=keepOptionModifier();
					state._fsp--;

					pushFollow(FOLLOW_classSpecification_in_prog123);
					classSpec=classSpecification();
					state._fsp--;

					GrammarActions.addKeepClassMembers(flags, classSpec, keepModifier);
					}

					}
					break;
				case 4 :
					// Proguard.g:43:7: ( '-keepclasseswithmembers' keepModifier= keepOptionModifier classSpec= classSpecification )
					{
					// Proguard.g:43:7: ( '-keepclasseswithmembers' keepModifier= keepOptionModifier classSpec= classSpecification )
					// Proguard.g:43:8: '-keepclasseswithmembers' keepModifier= keepOptionModifier classSpec= classSpecification
					{
					match(input,38,FOLLOW_38_in_prog135); 
					pushFollow(FOLLOW_keepOptionModifier_in_prog139);
					keepModifier=keepOptionModifier();
					state._fsp--;

					pushFollow(FOLLOW_classSpecification_in_prog143);
					classSpec=classSpecification();
					state._fsp--;

					GrammarActions.addKeepClassesWithMembers(flags, classSpec, keepModifier);
					}

					}
					break;
				case 5 :
					// Proguard.g:44:7: ( '-keep' keepModifier= keepOptionModifier classSpec= classSpecification )
					{
					// Proguard.g:44:7: ( '-keep' keepModifier= keepOptionModifier classSpec= classSpecification )
					// Proguard.g:44:8: '-keep' keepModifier= keepOptionModifier classSpec= classSpecification
					{
					match(input,35,FOLLOW_35_in_prog155); 
					pushFollow(FOLLOW_keepOptionModifier_in_prog159);
					keepModifier=keepOptionModifier();
					state._fsp--;

					pushFollow(FOLLOW_classSpecification_in_prog163);
					classSpec=classSpecification();
					state._fsp--;

					GrammarActions.addKeepClassSpecification(flags, classSpec, keepModifier);
					}

					}
					break;
				case 6 :
					// Proguard.g:45:7: (unFlag= unsupportedFlag )
					{
					// Proguard.g:45:7: (unFlag= unsupportedFlag )
					// Proguard.g:45:8: unFlag= unsupportedFlag
					{
					pushFollow(FOLLOW_unsupportedFlag_in_prog177);
					unFlag=unsupportedFlag();
					state._fsp--;

					flagsHandler.unsupportedFlag((unFlag!=null?input.toString(unFlag.start,unFlag.stop):null));
					}

					}
					break;
				case 7 :
					// Proguard.g:46:7: ( '-dontwarn' filter[class_filter, FilterSeparator.CLASS] )
					{
					// Proguard.g:46:7: ( '-dontwarn' filter[class_filter, FilterSeparator.CLASS] )
					// Proguard.g:46:8: '-dontwarn' filter[class_filter, FilterSeparator.CLASS]
					{
					match(input,28,FOLLOW_28_in_prog189); 
					List<FilterSpecification> class_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_prog193);
					filter(class_filter, FilterSeparator.CLASS);
					state._fsp--;

					GrammarActions.dontWarn(flags, class_filter);
					}

					}
					break;
				case 8 :
					// Proguard.g:47:7: ( '-ignorewarnings' )
					{
					// Proguard.g:47:7: ( '-ignorewarnings' )
					// Proguard.g:47:8: '-ignorewarnings'
					{
					match(input,32,FOLLOW_32_in_prog206); 
					GrammarActions.ignoreWarnings(flags);
					}

					}
					break;
				case 9 :
					// Proguard.g:48:7: ( '-target' target= NAME )
					{
					// Proguard.g:48:7: ( '-target' target= NAME )
					// Proguard.g:48:8: '-target' target= NAME
					{
					match(input,61,FOLLOW_61_in_prog218); 
					target=(Token)match(input,NAME,FOLLOW_NAME_in_prog222); 
					GrammarActions.target(flags, (target!=null?target.getText():null));
					}

					}
					break;
				case 10 :
					// Proguard.g:49:7: ( '-whyareyoukeeping' classSpec= classSpecification )
					{
					// Proguard.g:49:7: ( '-whyareyoukeeping' classSpec= classSpecification )
					// Proguard.g:49:8: '-whyareyoukeeping' classSpec= classSpecification
					{
					match(input,64,FOLLOW_64_in_prog234); 
					pushFollow(FOLLOW_classSpecification_in_prog238);
					classSpec=classSpecification();
					state._fsp--;

					 GrammarActions.whyAreYouKeeping(flags, classSpec); 
					}

					}
					break;
				case 11 :
					// Proguard.g:50:7: ( '-dontshrink' )
					{
					// Proguard.g:50:7: ( '-dontshrink' )
					// Proguard.g:50:8: '-dontshrink'
					{
					match(input,24,FOLLOW_24_in_prog250); 
					 GrammarActions.dontShrink(flags); 
					}

					}
					break;
				case 12 :
					// Proguard.g:51:7: ( '-dontoptimize' )
					{
					// Proguard.g:51:7: ( '-dontoptimize' )
					// Proguard.g:51:8: '-dontoptimize'
					{
					match(input,22,FOLLOW_22_in_prog263); 
					 GrammarActions.dontOptimize(flags); 
					}

					}
					break;
				case 13 :
					// Proguard.g:52:7: ( '-dontobfuscate' )
					{
					// Proguard.g:52:7: ( '-dontobfuscate' )
					// Proguard.g:52:8: '-dontobfuscate'
					{
					match(input,21,FOLLOW_21_in_prog276); 
					 GrammarActions.dontObfuscate(flags); 
					}

					}
					break;

				default :
					break loop1;
				}
			}

			match(input,EOF,FOLLOW_EOF_in_prog288); 
			}

		}
		catch (RecognitionException e) {

			    throw e;
			  
		}

		finally {
			// do for sure before leaving
		}
	}
	// $ANTLR end "prog"


	public static class unsupportedFlag_return extends ParserRuleReturnScope {
	};


	// $ANTLR start "unsupportedFlag"
	// Proguard.g:60:9: private unsupportedFlag : ( '-allowaccessmodification' | '-classobfuscationdictionary' classObfuscationDictionary= NAME | '-dontpreverify' | '-dontskipnonpubliclibraryclasses' | '-dontskipnonpubliclibraryclassmembers' | '-dontusemixedcaseclassnames' | '-forceprocessing' | '-injars' inJars= classpath | '-keepparameternames' | '-libraryjars' libraryJars= classpath | '-mergeinterfacesaggressively' | '-microedition' | '-obfuscationdictionary' obfuscationDictionary= NAME | '-outjars' outJars= classpath | '-overloadaggressively' | '-packageobfuscationdictionary' packageObfuscationDictionary= NAME | '-printmapping' (outputMapping= NAME )? | '-skipnonpubliclibraryclasses' | '-useuniqueclassmembernames' | '-verbose' | ( '-adaptclassstrings' filter[filter, FilterSeparator.GENERAL] ) | ( '-adaptresourcefilecontents' filter[file_filter, FilterSeparator.FILE] ) | ( '-adaptresourcefilenames' filter[file_filter, FilterSeparator.FILE] ) | ( '-applymapping' mapping= NAME ) | ( '-assumenosideeffects' classSpecification ) | ( '-dontnote' filter[class_filter, FilterSeparator.CLASS] ) | ( '-dump' ( NAME )? ) | ( '-flattenpackagehierarchy' ( '\\'' (newPackage= NAME )? '\\'' )? ) | ( '-keepattributes' filter[attribute_filter, FilterSeparator.ATTRIBUTE] ) | ( '-keepclasseswithmembernames' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclassmembernames' classSpec= classSpecification ) | ( '-keepdirectories' filter[directory_filter, FilterSeparator.FILE] ) | ( '-keepnames' classSpec= classSpecification ) | ( '-keeppackagenames' filter[package_filter, FilterSeparator.GENERAL] ) | ( '-optimizationpasses' NAME ) | ( '-optimizations' filter[optimization_filter, FilterSeparator.GENERAL] ) | ( '-printconfiguration' ( NAME )? ) | ( '-printseeds' (seedOutputFile= NAME )? ) | ( '-printusage' NAME ) | ( '-renamesourcefileattribute' (sourceFile= NAME )? ) | ( '-repackageclasses' ( '\\'' (newPackage= NAME )? '\\'' )? ) ) ;
	public final ProguardParser.unsupportedFlag_return unsupportedFlag() throws RecognitionException {
		ProguardParser.unsupportedFlag_return retval = new ProguardParser.unsupportedFlag_return();
		retval.start = input.LT(1);

		Token classObfuscationDictionary=null;
		Token obfuscationDictionary=null;
		Token packageObfuscationDictionary=null;
		Token outputMapping=null;
		Token mapping=null;
		Token newPackage=null;
		Token seedOutputFile=null;
		Token sourceFile=null;
		KeepModifier keepModifier =null;
		ClassSpecification classSpec =null;

		try {
			// Proguard.g:61:3: ( ( '-allowaccessmodification' | '-classobfuscationdictionary' classObfuscationDictionary= NAME | '-dontpreverify' | '-dontskipnonpubliclibraryclasses' | '-dontskipnonpubliclibraryclassmembers' | '-dontusemixedcaseclassnames' | '-forceprocessing' | '-injars' inJars= classpath | '-keepparameternames' | '-libraryjars' libraryJars= classpath | '-mergeinterfacesaggressively' | '-microedition' | '-obfuscationdictionary' obfuscationDictionary= NAME | '-outjars' outJars= classpath | '-overloadaggressively' | '-packageobfuscationdictionary' packageObfuscationDictionary= NAME | '-printmapping' (outputMapping= NAME )? | '-skipnonpubliclibraryclasses' | '-useuniqueclassmembernames' | '-verbose' | ( '-adaptclassstrings' filter[filter, FilterSeparator.GENERAL] ) | ( '-adaptresourcefilecontents' filter[file_filter, FilterSeparator.FILE] ) | ( '-adaptresourcefilenames' filter[file_filter, FilterSeparator.FILE] ) | ( '-applymapping' mapping= NAME ) | ( '-assumenosideeffects' classSpecification ) | ( '-dontnote' filter[class_filter, FilterSeparator.CLASS] ) | ( '-dump' ( NAME )? ) | ( '-flattenpackagehierarchy' ( '\\'' (newPackage= NAME )? '\\'' )? ) | ( '-keepattributes' filter[attribute_filter, FilterSeparator.ATTRIBUTE] ) | ( '-keepclasseswithmembernames' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclassmembernames' classSpec= classSpecification ) | ( '-keepdirectories' filter[directory_filter, FilterSeparator.FILE] ) | ( '-keepnames' classSpec= classSpecification ) | ( '-keeppackagenames' filter[package_filter, FilterSeparator.GENERAL] ) | ( '-optimizationpasses' NAME ) | ( '-optimizations' filter[optimization_filter, FilterSeparator.GENERAL] ) | ( '-printconfiguration' ( NAME )? ) | ( '-printseeds' (seedOutputFile= NAME )? ) | ( '-printusage' NAME ) | ( '-renamesourcefileattribute' (sourceFile= NAME )? ) | ( '-repackageclasses' ( '\\'' (newPackage= NAME )? '\\'' )? ) ) )
			// Proguard.g:62:3: ( '-allowaccessmodification' | '-classobfuscationdictionary' classObfuscationDictionary= NAME | '-dontpreverify' | '-dontskipnonpubliclibraryclasses' | '-dontskipnonpubliclibraryclassmembers' | '-dontusemixedcaseclassnames' | '-forceprocessing' | '-injars' inJars= classpath | '-keepparameternames' | '-libraryjars' libraryJars= classpath | '-mergeinterfacesaggressively' | '-microedition' | '-obfuscationdictionary' obfuscationDictionary= NAME | '-outjars' outJars= classpath | '-overloadaggressively' | '-packageobfuscationdictionary' packageObfuscationDictionary= NAME | '-printmapping' (outputMapping= NAME )? | '-skipnonpubliclibraryclasses' | '-useuniqueclassmembernames' | '-verbose' | ( '-adaptclassstrings' filter[filter, FilterSeparator.GENERAL] ) | ( '-adaptresourcefilecontents' filter[file_filter, FilterSeparator.FILE] ) | ( '-adaptresourcefilenames' filter[file_filter, FilterSeparator.FILE] ) | ( '-applymapping' mapping= NAME ) | ( '-assumenosideeffects' classSpecification ) | ( '-dontnote' filter[class_filter, FilterSeparator.CLASS] ) | ( '-dump' ( NAME )? ) | ( '-flattenpackagehierarchy' ( '\\'' (newPackage= NAME )? '\\'' )? ) | ( '-keepattributes' filter[attribute_filter, FilterSeparator.ATTRIBUTE] ) | ( '-keepclasseswithmembernames' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclassmembernames' classSpec= classSpecification ) | ( '-keepdirectories' filter[directory_filter, FilterSeparator.FILE] ) | ( '-keepnames' classSpec= classSpecification ) | ( '-keeppackagenames' filter[package_filter, FilterSeparator.GENERAL] ) | ( '-optimizationpasses' NAME ) | ( '-optimizations' filter[optimization_filter, FilterSeparator.GENERAL] ) | ( '-printconfiguration' ( NAME )? ) | ( '-printseeds' (seedOutputFile= NAME )? ) | ( '-printusage' NAME ) | ( '-renamesourcefileattribute' (sourceFile= NAME )? ) | ( '-repackageclasses' ( '\\'' (newPackage= NAME )? '\\'' )? ) )
			{
			// Proguard.g:62:3: ( '-allowaccessmodification' | '-classobfuscationdictionary' classObfuscationDictionary= NAME | '-dontpreverify' | '-dontskipnonpubliclibraryclasses' | '-dontskipnonpubliclibraryclassmembers' | '-dontusemixedcaseclassnames' | '-forceprocessing' | '-injars' inJars= classpath | '-keepparameternames' | '-libraryjars' libraryJars= classpath | '-mergeinterfacesaggressively' | '-microedition' | '-obfuscationdictionary' obfuscationDictionary= NAME | '-outjars' outJars= classpath | '-overloadaggressively' | '-packageobfuscationdictionary' packageObfuscationDictionary= NAME | '-printmapping' (outputMapping= NAME )? | '-skipnonpubliclibraryclasses' | '-useuniqueclassmembernames' | '-verbose' | ( '-adaptclassstrings' filter[filter, FilterSeparator.GENERAL] ) | ( '-adaptresourcefilecontents' filter[file_filter, FilterSeparator.FILE] ) | ( '-adaptresourcefilenames' filter[file_filter, FilterSeparator.FILE] ) | ( '-applymapping' mapping= NAME ) | ( '-assumenosideeffects' classSpecification ) | ( '-dontnote' filter[class_filter, FilterSeparator.CLASS] ) | ( '-dump' ( NAME )? ) | ( '-flattenpackagehierarchy' ( '\\'' (newPackage= NAME )? '\\'' )? ) | ( '-keepattributes' filter[attribute_filter, FilterSeparator.ATTRIBUTE] ) | ( '-keepclasseswithmembernames' keepModifier= keepOptionModifier classSpec= classSpecification ) | ( '-keepclassmembernames' classSpec= classSpecification ) | ( '-keepdirectories' filter[directory_filter, FilterSeparator.FILE] ) | ( '-keepnames' classSpec= classSpecification ) | ( '-keeppackagenames' filter[package_filter, FilterSeparator.GENERAL] ) | ( '-optimizationpasses' NAME ) | ( '-optimizations' filter[optimization_filter, FilterSeparator.GENERAL] ) | ( '-printconfiguration' ( NAME )? ) | ( '-printseeds' (seedOutputFile= NAME )? ) | ( '-printusage' NAME ) | ( '-renamesourcefileattribute' (sourceFile= NAME )? ) | ( '-repackageclasses' ( '\\'' (newPackage= NAME )? '\\'' )? ) )
			int alt11=41;
			switch ( input.LA(1) ) {
			case 15:
				{
				alt11=1;
				}
				break;
			case 19:
				{
				alt11=2;
				}
				break;
			case 23:
				{
				alt11=3;
				}
				break;
			case 25:
				{
				alt11=4;
				}
				break;
			case 26:
				{
				alt11=5;
				}
				break;
			case 27:
				{
				alt11=6;
				}
				break;
			case 31:
				{
				alt11=7;
				}
				break;
			case 34:
				{
				alt11=8;
				}
				break;
			case 44:
				{
				alt11=9;
				}
				break;
			case 45:
				{
				alt11=10;
				}
				break;
			case 46:
				{
				alt11=11;
				}
				break;
			case 47:
				{
				alt11=12;
				}
				break;
			case 48:
				{
				alt11=13;
				}
				break;
			case 51:
				{
				alt11=14;
				}
				break;
			case 52:
				{
				alt11=15;
				}
				break;
			case 53:
				{
				alt11=16;
				}
				break;
			case 55:
				{
				alt11=17;
				}
				break;
			case 60:
				{
				alt11=18;
				}
				break;
			case 62:
				{
				alt11=19;
				}
				break;
			case 63:
				{
				alt11=20;
				}
				break;
			case 12:
				{
				alt11=21;
				}
				break;
			case 13:
				{
				alt11=22;
				}
				break;
			case 14:
				{
				alt11=23;
				}
				break;
			case 16:
				{
				alt11=24;
				}
				break;
			case 17:
				{
				alt11=25;
				}
				break;
			case 20:
				{
				alt11=26;
				}
				break;
			case 29:
				{
				alt11=27;
				}
				break;
			case 30:
				{
				alt11=28;
				}
				break;
			case 36:
				{
				alt11=29;
				}
				break;
			case 37:
				{
				alt11=30;
				}
				break;
			case 39:
				{
				alt11=31;
				}
				break;
			case 41:
				{
				alt11=32;
				}
				break;
			case 42:
				{
				alt11=33;
				}
				break;
			case 43:
				{
				alt11=34;
				}
				break;
			case 49:
				{
				alt11=35;
				}
				break;
			case 50:
				{
				alt11=36;
				}
				break;
			case 54:
				{
				alt11=37;
				}
				break;
			case 56:
				{
				alt11=38;
				}
				break;
			case 57:
				{
				alt11=39;
				}
				break;
			case 58:
				{
				alt11=40;
				}
				break;
			case 59:
				{
				alt11=41;
				}
				break;
			default:
				NoViableAltException nvae =
					new NoViableAltException("", 11, 0, input);
				throw nvae;
			}
			switch (alt11) {
				case 1 :
					// Proguard.g:63:7: '-allowaccessmodification'
					{
					match(input,15,FOLLOW_15_in_unsupportedFlag321); 
					}
					break;
				case 2 :
					// Proguard.g:64:7: '-classobfuscationdictionary' classObfuscationDictionary= NAME
					{
					match(input,19,FOLLOW_19_in_unsupportedFlag329); 
					classObfuscationDictionary=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag333); 
					}
					break;
				case 3 :
					// Proguard.g:65:7: '-dontpreverify'
					{
					match(input,23,FOLLOW_23_in_unsupportedFlag341); 
					}
					break;
				case 4 :
					// Proguard.g:66:7: '-dontskipnonpubliclibraryclasses'
					{
					match(input,25,FOLLOW_25_in_unsupportedFlag349); 
					}
					break;
				case 5 :
					// Proguard.g:67:7: '-dontskipnonpubliclibraryclassmembers'
					{
					match(input,26,FOLLOW_26_in_unsupportedFlag357); 
					}
					break;
				case 6 :
					// Proguard.g:68:7: '-dontusemixedcaseclassnames'
					{
					match(input,27,FOLLOW_27_in_unsupportedFlag365); 
					}
					break;
				case 7 :
					// Proguard.g:69:7: '-forceprocessing'
					{
					match(input,31,FOLLOW_31_in_unsupportedFlag373); 
					}
					break;
				case 8 :
					// Proguard.g:70:7: '-injars' inJars= classpath
					{
					match(input,34,FOLLOW_34_in_unsupportedFlag381); 
					pushFollow(FOLLOW_classpath_in_unsupportedFlag385);
					classpath();
					state._fsp--;

					}
					break;
				case 9 :
					// Proguard.g:71:7: '-keepparameternames'
					{
					match(input,44,FOLLOW_44_in_unsupportedFlag393); 
					}
					break;
				case 10 :
					// Proguard.g:72:7: '-libraryjars' libraryJars= classpath
					{
					match(input,45,FOLLOW_45_in_unsupportedFlag401); 
					pushFollow(FOLLOW_classpath_in_unsupportedFlag405);
					classpath();
					state._fsp--;

					}
					break;
				case 11 :
					// Proguard.g:73:7: '-mergeinterfacesaggressively'
					{
					match(input,46,FOLLOW_46_in_unsupportedFlag413); 
					}
					break;
				case 12 :
					// Proguard.g:74:7: '-microedition'
					{
					match(input,47,FOLLOW_47_in_unsupportedFlag421); 
					}
					break;
				case 13 :
					// Proguard.g:75:7: '-obfuscationdictionary' obfuscationDictionary= NAME
					{
					match(input,48,FOLLOW_48_in_unsupportedFlag429); 
					obfuscationDictionary=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag433); 
					}
					break;
				case 14 :
					// Proguard.g:76:7: '-outjars' outJars= classpath
					{
					match(input,51,FOLLOW_51_in_unsupportedFlag441); 
					pushFollow(FOLLOW_classpath_in_unsupportedFlag445);
					classpath();
					state._fsp--;

					}
					break;
				case 15 :
					// Proguard.g:77:7: '-overloadaggressively'
					{
					match(input,52,FOLLOW_52_in_unsupportedFlag453); 
					}
					break;
				case 16 :
					// Proguard.g:78:7: '-packageobfuscationdictionary' packageObfuscationDictionary= NAME
					{
					match(input,53,FOLLOW_53_in_unsupportedFlag461); 
					packageObfuscationDictionary=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag465); 
					}
					break;
				case 17 :
					// Proguard.g:79:7: '-printmapping' (outputMapping= NAME )?
					{
					match(input,55,FOLLOW_55_in_unsupportedFlag473); 
					// Proguard.g:79:36: (outputMapping= NAME )?
					int alt2=2;
					int LA2_0 = input.LA(1);
					if ( (LA2_0==NAME) ) {
						alt2=1;
					}
					switch (alt2) {
						case 1 :
							// Proguard.g:79:36: outputMapping= NAME
							{
							outputMapping=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag477); 
							}
							break;

					}

					}
					break;
				case 18 :
					// Proguard.g:80:7: '-skipnonpubliclibraryclasses'
					{
					match(input,60,FOLLOW_60_in_unsupportedFlag486); 
					}
					break;
				case 19 :
					// Proguard.g:81:7: '-useuniqueclassmembernames'
					{
					match(input,62,FOLLOW_62_in_unsupportedFlag494); 
					}
					break;
				case 20 :
					// Proguard.g:82:7: '-verbose'
					{
					match(input,63,FOLLOW_63_in_unsupportedFlag502); 
					}
					break;
				case 21 :
					// Proguard.g:83:7: ( '-adaptclassstrings' filter[filter, FilterSeparator.GENERAL] )
					{
					// Proguard.g:83:7: ( '-adaptclassstrings' filter[filter, FilterSeparator.GENERAL] )
					// Proguard.g:83:8: '-adaptclassstrings' filter[filter, FilterSeparator.GENERAL]
					{
					match(input,12,FOLLOW_12_in_unsupportedFlag511); 
					List<FilterSpecification> filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag515);
					filter(filter, FilterSeparator.GENERAL);
					state._fsp--;

					}

					}
					break;
				case 22 :
					// Proguard.g:84:7: ( '-adaptresourcefilecontents' filter[file_filter, FilterSeparator.FILE] )
					{
					// Proguard.g:84:7: ( '-adaptresourcefilecontents' filter[file_filter, FilterSeparator.FILE] )
					// Proguard.g:84:8: '-adaptresourcefilecontents' filter[file_filter, FilterSeparator.FILE]
					{
					match(input,13,FOLLOW_13_in_unsupportedFlag526); 
					List<FilterSpecification> file_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag530);
					filter(file_filter, FilterSeparator.FILE);
					state._fsp--;

					}

					}
					break;
				case 23 :
					// Proguard.g:85:7: ( '-adaptresourcefilenames' filter[file_filter, FilterSeparator.FILE] )
					{
					// Proguard.g:85:7: ( '-adaptresourcefilenames' filter[file_filter, FilterSeparator.FILE] )
					// Proguard.g:85:8: '-adaptresourcefilenames' filter[file_filter, FilterSeparator.FILE]
					{
					match(input,14,FOLLOW_14_in_unsupportedFlag542); 
					List<FilterSpecification> file_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag546);
					filter(file_filter, FilterSeparator.FILE);
					state._fsp--;

					}

					}
					break;
				case 24 :
					// Proguard.g:86:7: ( '-applymapping' mapping= NAME )
					{
					// Proguard.g:86:7: ( '-applymapping' mapping= NAME )
					// Proguard.g:86:8: '-applymapping' mapping= NAME
					{
					match(input,16,FOLLOW_16_in_unsupportedFlag558); 
					mapping=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag562); 
					}

					}
					break;
				case 25 :
					// Proguard.g:87:7: ( '-assumenosideeffects' classSpecification )
					{
					// Proguard.g:87:7: ( '-assumenosideeffects' classSpecification )
					// Proguard.g:87:8: '-assumenosideeffects' classSpecification
					{
					match(input,17,FOLLOW_17_in_unsupportedFlag573); 
					pushFollow(FOLLOW_classSpecification_in_unsupportedFlag575);
					classSpecification();
					state._fsp--;

					}

					}
					break;
				case 26 :
					// Proguard.g:88:7: ( '-dontnote' filter[class_filter, FilterSeparator.CLASS] )
					{
					// Proguard.g:88:7: ( '-dontnote' filter[class_filter, FilterSeparator.CLASS] )
					// Proguard.g:88:8: '-dontnote' filter[class_filter, FilterSeparator.CLASS]
					{
					match(input,20,FOLLOW_20_in_unsupportedFlag585); 
					List<FilterSpecification> class_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag589);
					filter(class_filter, FilterSeparator.CLASS);
					state._fsp--;

					}

					}
					break;
				case 27 :
					// Proguard.g:89:7: ( '-dump' ( NAME )? )
					{
					// Proguard.g:89:7: ( '-dump' ( NAME )? )
					// Proguard.g:89:8: '-dump' ( NAME )?
					{
					match(input,29,FOLLOW_29_in_unsupportedFlag600); 
					// Proguard.g:89:16: ( NAME )?
					int alt3=2;
					int LA3_0 = input.LA(1);
					if ( (LA3_0==NAME) ) {
						alt3=1;
					}
					switch (alt3) {
						case 1 :
							// Proguard.g:89:16: NAME
							{
							match(input,NAME,FOLLOW_NAME_in_unsupportedFlag602); 
							}
							break;

					}

					}

					}
					break;
				case 28 :
					// Proguard.g:90:7: ( '-flattenpackagehierarchy' ( '\\'' (newPackage= NAME )? '\\'' )? )
					{
					// Proguard.g:90:7: ( '-flattenpackagehierarchy' ( '\\'' (newPackage= NAME )? '\\'' )? )
					// Proguard.g:90:8: '-flattenpackagehierarchy' ( '\\'' (newPackage= NAME )? '\\'' )?
					{
					match(input,30,FOLLOW_30_in_unsupportedFlag614); 
					// Proguard.g:90:35: ( '\\'' (newPackage= NAME )? '\\'' )?
					int alt5=2;
					int LA5_0 = input.LA(1);
					if ( (LA5_0==72) ) {
						alt5=1;
					}
					switch (alt5) {
						case 1 :
							// Proguard.g:90:36: '\\'' (newPackage= NAME )? '\\''
							{
							match(input,72,FOLLOW_72_in_unsupportedFlag617); 
							// Proguard.g:90:51: (newPackage= NAME )?
							int alt4=2;
							int LA4_0 = input.LA(1);
							if ( (LA4_0==NAME) ) {
								alt4=1;
							}
							switch (alt4) {
								case 1 :
									// Proguard.g:90:51: newPackage= NAME
									{
									newPackage=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag621); 
									}
									break;

							}

							match(input,72,FOLLOW_72_in_unsupportedFlag624); 
							}
							break;

					}

					}

					}
					break;
				case 29 :
					// Proguard.g:91:7: ( '-keepattributes' filter[attribute_filter, FilterSeparator.ATTRIBUTE] )
					{
					// Proguard.g:91:7: ( '-keepattributes' filter[attribute_filter, FilterSeparator.ATTRIBUTE] )
					// Proguard.g:91:8: '-keepattributes' filter[attribute_filter, FilterSeparator.ATTRIBUTE]
					{
					match(input,36,FOLLOW_36_in_unsupportedFlag637); 
					List<FilterSpecification> attribute_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag641);
					filter(attribute_filter, FilterSeparator.ATTRIBUTE);
					state._fsp--;

					}

					}
					break;
				case 30 :
					// Proguard.g:92:7: ( '-keepclasseswithmembernames' keepModifier= keepOptionModifier classSpec= classSpecification )
					{
					// Proguard.g:92:7: ( '-keepclasseswithmembernames' keepModifier= keepOptionModifier classSpec= classSpecification )
					// Proguard.g:92:8: '-keepclasseswithmembernames' keepModifier= keepOptionModifier classSpec= classSpecification
					{
					match(input,37,FOLLOW_37_in_unsupportedFlag653); 
					pushFollow(FOLLOW_keepOptionModifier_in_unsupportedFlag657);
					keepModifier=keepOptionModifier();
					state._fsp--;

					pushFollow(FOLLOW_classSpecification_in_unsupportedFlag661);
					classSpec=classSpecification();
					state._fsp--;

					}

					}
					break;
				case 31 :
					// Proguard.g:93:7: ( '-keepclassmembernames' classSpec= classSpecification )
					{
					// Proguard.g:93:7: ( '-keepclassmembernames' classSpec= classSpecification )
					// Proguard.g:93:8: '-keepclassmembernames' classSpec= classSpecification
					{
					match(input,39,FOLLOW_39_in_unsupportedFlag673); 
					pushFollow(FOLLOW_classSpecification_in_unsupportedFlag677);
					classSpec=classSpecification();
					state._fsp--;

					}

					}
					break;
				case 32 :
					// Proguard.g:94:7: ( '-keepdirectories' filter[directory_filter, FilterSeparator.FILE] )
					{
					// Proguard.g:94:7: ( '-keepdirectories' filter[directory_filter, FilterSeparator.FILE] )
					// Proguard.g:94:8: '-keepdirectories' filter[directory_filter, FilterSeparator.FILE]
					{
					match(input,41,FOLLOW_41_in_unsupportedFlag689); 
					List<FilterSpecification> directory_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag693);
					filter(directory_filter, FilterSeparator.FILE);
					state._fsp--;

					}

					}
					break;
				case 33 :
					// Proguard.g:95:7: ( '-keepnames' classSpec= classSpecification )
					{
					// Proguard.g:95:7: ( '-keepnames' classSpec= classSpecification )
					// Proguard.g:95:8: '-keepnames' classSpec= classSpecification
					{
					match(input,42,FOLLOW_42_in_unsupportedFlag704); 
					pushFollow(FOLLOW_classSpecification_in_unsupportedFlag708);
					classSpec=classSpecification();
					state._fsp--;

					}

					}
					break;
				case 34 :
					// Proguard.g:96:7: ( '-keeppackagenames' filter[package_filter, FilterSeparator.GENERAL] )
					{
					// Proguard.g:96:7: ( '-keeppackagenames' filter[package_filter, FilterSeparator.GENERAL] )
					// Proguard.g:96:8: '-keeppackagenames' filter[package_filter, FilterSeparator.GENERAL]
					{
					match(input,43,FOLLOW_43_in_unsupportedFlag719); 
					List<FilterSpecification> package_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag723);
					filter(package_filter, FilterSeparator.GENERAL);
					state._fsp--;

					}

					}
					break;
				case 35 :
					// Proguard.g:97:7: ( '-optimizationpasses' NAME )
					{
					// Proguard.g:97:7: ( '-optimizationpasses' NAME )
					// Proguard.g:97:8: '-optimizationpasses' NAME
					{
					match(input,49,FOLLOW_49_in_unsupportedFlag735); 
					match(input,NAME,FOLLOW_NAME_in_unsupportedFlag737); 
					}

					}
					break;
				case 36 :
					// Proguard.g:98:7: ( '-optimizations' filter[optimization_filter, FilterSeparator.GENERAL] )
					{
					// Proguard.g:98:7: ( '-optimizations' filter[optimization_filter, FilterSeparator.GENERAL] )
					// Proguard.g:98:8: '-optimizations' filter[optimization_filter, FilterSeparator.GENERAL]
					{
					match(input,50,FOLLOW_50_in_unsupportedFlag748); 
					List<FilterSpecification> optimization_filter = new ArrayList<FilterSpecification>();
					pushFollow(FOLLOW_filter_in_unsupportedFlag752);
					filter(optimization_filter, FilterSeparator.GENERAL);
					state._fsp--;

					}

					}
					break;
				case 37 :
					// Proguard.g:99:7: ( '-printconfiguration' ( NAME )? )
					{
					// Proguard.g:99:7: ( '-printconfiguration' ( NAME )? )
					// Proguard.g:99:8: '-printconfiguration' ( NAME )?
					{
					match(input,54,FOLLOW_54_in_unsupportedFlag763); 
					// Proguard.g:99:30: ( NAME )?
					int alt6=2;
					int LA6_0 = input.LA(1);
					if ( (LA6_0==NAME) ) {
						alt6=1;
					}
					switch (alt6) {
						case 1 :
							// Proguard.g:99:30: NAME
							{
							match(input,NAME,FOLLOW_NAME_in_unsupportedFlag765); 
							}
							break;

					}

					}

					}
					break;
				case 38 :
					// Proguard.g:100:7: ( '-printseeds' (seedOutputFile= NAME )? )
					{
					// Proguard.g:100:7: ( '-printseeds' (seedOutputFile= NAME )? )
					// Proguard.g:100:8: '-printseeds' (seedOutputFile= NAME )?
					{
					match(input,56,FOLLOW_56_in_unsupportedFlag777); 
					// Proguard.g:100:36: (seedOutputFile= NAME )?
					int alt7=2;
					int LA7_0 = input.LA(1);
					if ( (LA7_0==NAME) ) {
						alt7=1;
					}
					switch (alt7) {
						case 1 :
							// Proguard.g:100:36: seedOutputFile= NAME
							{
							seedOutputFile=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag781); 
							}
							break;

					}

					}

					}
					break;
				case 39 :
					// Proguard.g:101:7: ( '-printusage' NAME )
					{
					// Proguard.g:101:7: ( '-printusage' NAME )
					// Proguard.g:101:8: '-printusage' NAME
					{
					match(input,57,FOLLOW_57_in_unsupportedFlag793); 
					match(input,NAME,FOLLOW_NAME_in_unsupportedFlag795); 
					}

					}
					break;
				case 40 :
					// Proguard.g:102:7: ( '-renamesourcefileattribute' (sourceFile= NAME )? )
					{
					// Proguard.g:102:7: ( '-renamesourcefileattribute' (sourceFile= NAME )? )
					// Proguard.g:102:8: '-renamesourcefileattribute' (sourceFile= NAME )?
					{
					match(input,58,FOLLOW_58_in_unsupportedFlag806); 
					// Proguard.g:102:47: (sourceFile= NAME )?
					int alt8=2;
					int LA8_0 = input.LA(1);
					if ( (LA8_0==NAME) ) {
						alt8=1;
					}
					switch (alt8) {
						case 1 :
							// Proguard.g:102:47: sourceFile= NAME
							{
							sourceFile=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag810); 
							}
							break;

					}

					}

					}
					break;
				case 41 :
					// Proguard.g:103:7: ( '-repackageclasses' ( '\\'' (newPackage= NAME )? '\\'' )? )
					{
					// Proguard.g:103:7: ( '-repackageclasses' ( '\\'' (newPackage= NAME )? '\\'' )? )
					// Proguard.g:103:8: '-repackageclasses' ( '\\'' (newPackage= NAME )? '\\'' )?
					{
					match(input,59,FOLLOW_59_in_unsupportedFlag821); 
					// Proguard.g:103:28: ( '\\'' (newPackage= NAME )? '\\'' )?
					int alt10=2;
					int LA10_0 = input.LA(1);
					if ( (LA10_0==72) ) {
						alt10=1;
					}
					switch (alt10) {
						case 1 :
							// Proguard.g:103:29: '\\'' (newPackage= NAME )? '\\''
							{
							match(input,72,FOLLOW_72_in_unsupportedFlag824); 
							// Proguard.g:103:44: (newPackage= NAME )?
							int alt9=2;
							int LA9_0 = input.LA(1);
							if ( (LA9_0==NAME) ) {
								alt9=1;
							}
							switch (alt9) {
								case 1 :
									// Proguard.g:103:44: newPackage= NAME
									{
									newPackage=(Token)match(input,NAME,FOLLOW_NAME_in_unsupportedFlag828); 
									}
									break;

							}

							match(input,72,FOLLOW_72_in_unsupportedFlag831); 
							}
							break;

					}

					}

					}
					break;

			}

			}

			retval.stop = input.LT(-1);

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return retval;
	}
	// $ANTLR end "unsupportedFlag"



	// $ANTLR start "classpath"
	// Proguard.g:107:9: private classpath : NAME ( ( ':' | ';' ) classpath )? ;
	public final void classpath() throws RecognitionException {
		try {
			// Proguard.g:108:3: ( NAME ( ( ':' | ';' ) classpath )? )
			// Proguard.g:108:6: NAME ( ( ':' | ';' ) classpath )?
			{
			match(input,NAME,FOLLOW_NAME_in_classpath855); 
			// Proguard.g:108:11: ( ( ':' | ';' ) classpath )?
			int alt12=2;
			int LA12_0 = input.LA(1);
			if ( ((LA12_0 >= 65 && LA12_0 <= 66)) ) {
				alt12=1;
			}
			switch (alt12) {
				case 1 :
					// Proguard.g:108:12: ( ':' | ';' ) classpath
					{
					if ( (input.LA(1) >= 65 && input.LA(1) <= 66) ) {
						input.consume();
						state.errorRecovery=false;
					}
					else {
						MismatchedSetException mse = new MismatchedSetException(null,input);
						throw mse;
					}
					pushFollow(FOLLOW_classpath_in_classpath864);
					classpath();
					state._fsp--;

					}
					break;

			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
	}
	// $ANTLR end "classpath"



	// $ANTLR start "filter"
	// Proguard.g:111:9: private filter[List<FilterSpecification> filter, FilterSeparator separator] : ( nonEmptyFilter[filter, separator] |);
	public final void filter(List<FilterSpecification> filter, FilterSeparator separator) throws RecognitionException {
		try {
			// Proguard.g:112:3: ( nonEmptyFilter[filter, separator] |)
			int alt13=2;
			int LA13_0 = input.LA(1);
			if ( ((LA13_0 >= NAME && LA13_0 <= NEGATOR)) ) {
				alt13=1;
			}
			else if ( (LA13_0==EOF||(LA13_0 >= 12 && LA13_0 <= 64)||LA13_0==70) ) {
				alt13=2;
			}

			else {
				NoViableAltException nvae =
					new NoViableAltException("", 13, 0, input);
				throw nvae;
			}

			switch (alt13) {
				case 1 :
					// Proguard.g:113:3: nonEmptyFilter[filter, separator]
					{
					pushFollow(FOLLOW_nonEmptyFilter_in_filter885);
					nonEmptyFilter(filter, separator);
					state._fsp--;

					}
					break;
				case 2 :
					// Proguard.g:114:5: 
					{
					GrammarActions.filter(filter, false, "**", separator);
					}
					break;

			}
		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
	}
	// $ANTLR end "filter"



	// $ANTLR start "nonEmptyFilter"
	// Proguard.g:118:9: private nonEmptyFilter[List<FilterSpecification> filter, FilterSeparator separator] : ( ( NEGATOR )? NAME ( ',' nonEmptyFilter[filter, separator] )? ) ;
	public final void nonEmptyFilter(List<FilterSpecification> filter, FilterSeparator separator) throws RecognitionException {
		Token NAME1=null;


		  boolean negator = false;

		try {
			// Proguard.g:122:3: ( ( ( NEGATOR )? NAME ( ',' nonEmptyFilter[filter, separator] )? ) )
			// Proguard.g:123:3: ( ( NEGATOR )? NAME ( ',' nonEmptyFilter[filter, separator] )? )
			{
			// Proguard.g:123:3: ( ( NEGATOR )? NAME ( ',' nonEmptyFilter[filter, separator] )? )
			// Proguard.g:123:4: ( NEGATOR )? NAME ( ',' nonEmptyFilter[filter, separator] )?
			{
			// Proguard.g:123:4: ( NEGATOR )?
			int alt14=2;
			int LA14_0 = input.LA(1);
			if ( (LA14_0==NEGATOR) ) {
				alt14=1;
			}
			switch (alt14) {
				case 1 :
					// Proguard.g:123:5: NEGATOR
					{
					match(input,NEGATOR,FOLLOW_NEGATOR_in_nonEmptyFilter919); 
					negator=true;
					}
					break;

			}

			NAME1=(Token)match(input,NAME,FOLLOW_NAME_in_nonEmptyFilter925); 
			GrammarActions.filter(filter, negator, (NAME1!=null?NAME1.getText():null), separator);
			// Proguard.g:123:102: ( ',' nonEmptyFilter[filter, separator] )?
			int alt15=2;
			int LA15_0 = input.LA(1);
			if ( (LA15_0==11) ) {
				alt15=1;
			}
			switch (alt15) {
				case 1 :
					// Proguard.g:123:103: ',' nonEmptyFilter[filter, separator]
					{
					match(input,11,FOLLOW_11_in_nonEmptyFilter930); 
					pushFollow(FOLLOW_nonEmptyFilter_in_nonEmptyFilter932);
					nonEmptyFilter(filter, separator);
					state._fsp--;

					}
					break;

			}

			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
	}
	// $ANTLR end "nonEmptyFilter"



	// $ANTLR start "classSpecification"
	// Proguard.g:126:9: private classSpecification returns [ClassSpecification classSpec] : ( annotation )? cType= classModifierAndType[modifier] classNames (inheritanceSpec= inheritance )? ( members[classSpec] )? ;
	public final ClassSpecification classSpecification() throws RecognitionException {
		ClassSpecification classSpec = null;


		ClassTypeSpecification cType =null;
		InheritanceSpecification inheritanceSpec =null;
		List<NameSpecification> classNames2 =null;
		AnnotationSpecification annotation3 =null;


		  ModifierSpecification modifier = new ModifierSpecification();
		  boolean hasNameNegator = false;

		try {
			// Proguard.g:131:3: ( ( annotation )? cType= classModifierAndType[modifier] classNames (inheritanceSpec= inheritance )? ( members[classSpec] )? )
			// Proguard.g:132:3: ( annotation )? cType= classModifierAndType[modifier] classNames (inheritanceSpec= inheritance )? ( members[classSpec] )?
			{
			// Proguard.g:132:3: ( annotation )?
			int alt16=2;
			int LA16_0 = input.LA(1);
			if ( (LA16_0==70) ) {
				int LA16_1 = input.LA(2);
				if ( ((LA16_1 >= NAME && LA16_1 <= NEGATOR)) ) {
					alt16=1;
				}
			}
			switch (alt16) {
				case 1 :
					// Proguard.g:132:4: annotation
					{
					pushFollow(FOLLOW_annotation_in_classSpecification962);
					annotation3=annotation();
					state._fsp--;

					}
					break;

			}

			pushFollow(FOLLOW_classModifierAndType_in_classSpecification970);
			cType=classModifierAndType(modifier);
			state._fsp--;

			pushFollow(FOLLOW_classNames_in_classSpecification975);
			classNames2=classNames();
			state._fsp--;

			classSpec = GrammarActions.classSpec(classNames2, cType, annotation3, modifier);
			// Proguard.g:135:3: (inheritanceSpec= inheritance )?
			int alt17=2;
			int LA17_0 = input.LA(1);
			if ( (LA17_0==80||LA17_0==82) ) {
				alt17=1;
			}
			switch (alt17) {
				case 1 :
					// Proguard.g:135:4: inheritanceSpec= inheritance
					{
					pushFollow(FOLLOW_inheritance_in_classSpecification984);
					inheritanceSpec=inheritance();
					state._fsp--;

					classSpec.setInheritance(inheritanceSpec);
					}
					break;

			}

			// Proguard.g:136:3: ( members[classSpec] )?
			int alt18=2;
			int LA18_0 = input.LA(1);
			if ( (LA18_0==96) ) {
				alt18=1;
			}
			switch (alt18) {
				case 1 :
					// Proguard.g:136:3: members[classSpec]
					{
					pushFollow(FOLLOW_members_in_classSpecification992);
					members(classSpec);
					state._fsp--;

					}
					break;

			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return classSpec;
	}
	// $ANTLR end "classSpecification"



	// $ANTLR start "classNames"
	// Proguard.g:139:9: private classNames returns [List<NameSpecification> names] : firstName= className ( ',' otherName= className )* ;
	public final List<NameSpecification> classNames() throws RecognitionException {
		List<NameSpecification> names = null;


		NameSpecification firstName =null;
		NameSpecification otherName =null;


		  names = new ArrayList<NameSpecification>();

		try {
			// Proguard.g:143:3: (firstName= className ( ',' otherName= className )* )
			// Proguard.g:144:3: firstName= className ( ',' otherName= className )*
			{
			pushFollow(FOLLOW_className_in_classNames1021);
			firstName=className();
			state._fsp--;

			names.add(firstName);
			// Proguard.g:145:3: ( ',' otherName= className )*
			loop19:
			while (true) {
				int alt19=2;
				int LA19_0 = input.LA(1);
				if ( (LA19_0==11) ) {
					alt19=1;
				}

				switch (alt19) {
				case 1 :
					// Proguard.g:145:4: ',' otherName= className
					{
					match(input,11,FOLLOW_11_in_classNames1028); 
					pushFollow(FOLLOW_className_in_classNames1032);
					otherName=className();
					state._fsp--;

					names.add(otherName);
					}
					break;

				default :
					break loop19;
				}
			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return names;
	}
	// $ANTLR end "classNames"



	// $ANTLR start "className"
	// Proguard.g:148:9: private className returns [NameSpecification nameSpec] : ( NEGATOR )? NAME ;
	public final NameSpecification className() throws RecognitionException {
		NameSpecification nameSpec = null;


		Token NAME4=null;


		    boolean hasNameNegator = false;

		try {
			// Proguard.g:152:3: ( ( NEGATOR )? NAME )
			// Proguard.g:153:3: ( NEGATOR )? NAME
			{
			// Proguard.g:153:3: ( NEGATOR )?
			int alt20=2;
			int LA20_0 = input.LA(1);
			if ( (LA20_0==NEGATOR) ) {
				alt20=1;
			}
			switch (alt20) {
				case 1 :
					// Proguard.g:153:4: NEGATOR
					{
					match(input,NEGATOR,FOLLOW_NEGATOR_in_className1061); 
					hasNameNegator = true;
					}
					break;

			}

			NAME4=(Token)match(input,NAME,FOLLOW_NAME_in_className1069); 
			nameSpec=GrammarActions.className((NAME4!=null?NAME4.getText():null), hasNameNegator);
			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return nameSpec;
	}
	// $ANTLR end "className"



	// $ANTLR start "classModifierAndType"
	// Proguard.g:157:9: private classModifierAndType[ModifierSpecification modifier] returns [ClassTypeSpecification cType] : ( NEGATOR )? ( 'public' cmat= classModifierAndType[modifier] | 'abstract' cmat= classModifierAndType[modifier] | 'final' cmat= classModifierAndType[modifier] | classType ) ;
	public final ClassTypeSpecification classModifierAndType(ModifierSpecification modifier) throws RecognitionException {
		ClassTypeSpecification cType = null;


		ClassTypeSpecification cmat =null;
		int classType5 =0;


		  boolean hasNegator = false;

		try {
			// Proguard.g:161:3: ( ( NEGATOR )? ( 'public' cmat= classModifierAndType[modifier] | 'abstract' cmat= classModifierAndType[modifier] | 'final' cmat= classModifierAndType[modifier] | classType ) )
			// Proguard.g:162:3: ( NEGATOR )? ( 'public' cmat= classModifierAndType[modifier] | 'abstract' cmat= classModifierAndType[modifier] | 'final' cmat= classModifierAndType[modifier] | classType )
			{
			// Proguard.g:162:3: ( NEGATOR )?
			int alt21=2;
			int LA21_0 = input.LA(1);
			if ( (LA21_0==NEGATOR) ) {
				alt21=1;
			}
			switch (alt21) {
				case 1 :
					// Proguard.g:162:4: NEGATOR
					{
					match(input,NEGATOR,FOLLOW_NEGATOR_in_classModifierAndType1096); 
					hasNegator = true;
					}
					break;

			}

			// Proguard.g:163:3: ( 'public' cmat= classModifierAndType[modifier] | 'abstract' cmat= classModifierAndType[modifier] | 'final' cmat= classModifierAndType[modifier] | classType )
			int alt22=4;
			switch ( input.LA(1) ) {
			case 88:
				{
				alt22=1;
				}
				break;
			case 73:
				{
				alt22=2;
				}
				break;
			case 81:
				{
				alt22=3;
				}
				break;
			case 70:
			case 78:
			case 79:
			case 84:
				{
				alt22=4;
				}
				break;
			default:
				NoViableAltException nvae =
					new NoViableAltException("", 22, 0, input);
				throw nvae;
			}
			switch (alt22) {
				case 1 :
					// Proguard.g:164:3: 'public' cmat= classModifierAndType[modifier]
					{
					match(input,88,FOLLOW_88_in_classModifierAndType1108); 
					GrammarActions.addAccessFlag(modifier, AccessFlag.PUBLIC, hasNegator);
					pushFollow(FOLLOW_classModifierAndType_in_classModifierAndType1114);
					cmat=classModifierAndType(modifier);
					state._fsp--;

					cType = cmat;
					}
					break;
				case 2 :
					// Proguard.g:165:5: 'abstract' cmat= classModifierAndType[modifier]
					{
					match(input,73,FOLLOW_73_in_classModifierAndType1123); 
					GrammarActions.addModifier(modifier, Modifier.ABSTRACT, hasNegator);
					pushFollow(FOLLOW_classModifierAndType_in_classModifierAndType1129);
					cmat=classModifierAndType(modifier);
					state._fsp--;

					cType = cmat;
					}
					break;
				case 3 :
					// Proguard.g:166:5: 'final' cmat= classModifierAndType[modifier]
					{
					match(input,81,FOLLOW_81_in_classModifierAndType1138); 
					GrammarActions.addModifier(modifier, Modifier.FINAL, hasNegator);
					pushFollow(FOLLOW_classModifierAndType_in_classModifierAndType1144);
					cmat=classModifierAndType(modifier);
					state._fsp--;

					cType = cmat;
					}
					break;
				case 4 :
					// Proguard.g:167:5: classType
					{
					pushFollow(FOLLOW_classType_in_classModifierAndType1153);
					classType5=classType();
					state._fsp--;

					cType=GrammarActions.classType(classType5, hasNegator); 
					}
					break;

			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return cType;
	}
	// $ANTLR end "classModifierAndType"



	// $ANTLR start "classType"
	// Proguard.g:171:9: private classType returns [int type] : ( '@' )? ( 'interface' | 'enum' | 'class' ) ;
	public final int classType() throws RecognitionException {
		int type = 0;



		  type = 0;

		try {
			// Proguard.g:175:3: ( ( '@' )? ( 'interface' | 'enum' | 'class' ) )
			// Proguard.g:176:3: ( '@' )? ( 'interface' | 'enum' | 'class' )
			{
			// Proguard.g:176:3: ( '@' )?
			int alt23=2;
			int LA23_0 = input.LA(1);
			if ( (LA23_0==70) ) {
				alt23=1;
			}
			switch (alt23) {
				case 1 :
					// Proguard.g:176:4: '@'
					{
					match(input,70,FOLLOW_70_in_classType1186); 
					type |= ACC_ANNOTATION;
					}
					break;

			}

			// Proguard.g:177:3: ( 'interface' | 'enum' | 'class' )
			int alt24=3;
			switch ( input.LA(1) ) {
			case 84:
				{
				alt24=1;
				}
				break;
			case 79:
				{
				alt24=2;
				}
				break;
			case 78:
				{
				alt24=3;
				}
				break;
			default:
				NoViableAltException nvae =
					new NoViableAltException("", 24, 0, input);
				throw nvae;
			}
			switch (alt24) {
				case 1 :
					// Proguard.g:177:4: 'interface'
					{
					match(input,84,FOLLOW_84_in_classType1195); 
					type |= ACC_INTERFACE;
					}
					break;
				case 2 :
					// Proguard.g:178:5: 'enum'
					{
					match(input,79,FOLLOW_79_in_classType1203); 
					type |= ACC_ENUM;
					}
					break;
				case 3 :
					// Proguard.g:179:5: 'class'
					{
					match(input,78,FOLLOW_78_in_classType1211); 
					}
					break;

			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return type;
	}
	// $ANTLR end "classType"



	// $ANTLR start "members"
	// Proguard.g:183:9: private members[ClassSpecification classSpec] : '{' ( member[classSpec] )* '}' ;
	public final void members(ClassSpecification classSpec) throws RecognitionException {
		try {
			// Proguard.g:184:3: ( '{' ( member[classSpec] )* '}' )
			// Proguard.g:185:3: '{' ( member[classSpec] )* '}'
			{
			match(input,96,FOLLOW_96_in_members1234); 
			// Proguard.g:186:5: ( member[classSpec] )*
			loop25:
			while (true) {
				int alt25=2;
				int LA25_0 = input.LA(1);
				if ( ((LA25_0 >= NAME && LA25_0 <= NEGATOR)||LA25_0==8||(LA25_0 >= 67 && LA25_0 <= 70)||LA25_0==73||LA25_0==77||LA25_0==81||(LA25_0 >= 85 && LA25_0 <= 95)) ) {
					alt25=1;
				}

				switch (alt25) {
				case 1 :
					// Proguard.g:186:5: member[classSpec]
					{
					pushFollow(FOLLOW_member_in_members1240);
					member(classSpec);
					state._fsp--;

					}
					break;

				default :
					break loop25;
				}
			}

			match(input,97,FOLLOW_97_in_members1246); 
			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
	}
	// $ANTLR end "members"



	// $ANTLR start "member"
	// Proguard.g:190:9: private member[ClassSpecification classSpec] : ( annotation )? modifiers ( (typeSig= type )? name= ( NAME | '<init>' ) (signature= arguments |) | '<methods>' | '<fields>' ) ';' ;
	public final void member(ClassSpecification classSpec) throws RecognitionException {
		Token name=null;
		String typeSig =null;
		String signature =null;
		AnnotationSpecification annotation6 =null;
		ModifierSpecification modifiers7 =null;

		try {
			// Proguard.g:191:3: ( ( annotation )? modifiers ( (typeSig= type )? name= ( NAME | '<init>' ) (signature= arguments |) | '<methods>' | '<fields>' ) ';' )
			// Proguard.g:192:5: ( annotation )? modifiers ( (typeSig= type )? name= ( NAME | '<init>' ) (signature= arguments |) | '<methods>' | '<fields>' ) ';'
			{
			// Proguard.g:192:5: ( annotation )?
			int alt26=2;
			int LA26_0 = input.LA(1);
			if ( (LA26_0==70) ) {
				alt26=1;
			}
			switch (alt26) {
				case 1 :
					// Proguard.g:192:5: annotation
					{
					pushFollow(FOLLOW_annotation_in_member1267);
					annotation6=annotation();
					state._fsp--;

					}
					break;

			}

			pushFollow(FOLLOW_modifiers_in_member1270);
			modifiers7=modifiers();
			state._fsp--;

			// Proguard.g:193:5: ( (typeSig= type )? name= ( NAME | '<init>' ) (signature= arguments |) | '<methods>' | '<fields>' )
			int alt29=3;
			switch ( input.LA(1) ) {
			case NAME:
			case 8:
			case 68:
				{
				alt29=1;
				}
				break;
			case 69:
				{
				alt29=2;
				}
				break;
			case 67:
				{
				alt29=3;
				}
				break;
			default:
				NoViableAltException nvae =
					new NoViableAltException("", 29, 0, input);
				throw nvae;
			}
			switch (alt29) {
				case 1 :
					// Proguard.g:194:7: (typeSig= type )? name= ( NAME | '<init>' ) (signature= arguments |)
					{
					// Proguard.g:194:7: (typeSig= type )?
					int alt27=2;
					int LA27_0 = input.LA(1);
					if ( (LA27_0==NAME) ) {
						int LA27_1 = input.LA(2);
						if ( (LA27_1==NAME||LA27_1==68||LA27_1==71) ) {
							alt27=1;
						}
					}
					else if ( (LA27_0==8) ) {
						alt27=1;
					}
					switch (alt27) {
						case 1 :
							// Proguard.g:194:8: typeSig= type
							{
							pushFollow(FOLLOW_type_in_member1287);
							typeSig=type();
							state._fsp--;

							}
							break;

					}

					name=input.LT(1);
					if ( input.LA(1)==NAME||input.LA(1)==68 ) {
						input.consume();
						state.errorRecovery=false;
					}
					else {
						MismatchedSetException mse = new MismatchedSetException(null,input);
						throw mse;
					}
					// Proguard.g:194:44: (signature= arguments |)
					int alt28=2;
					int LA28_0 = input.LA(1);
					if ( (LA28_0==9) ) {
						alt28=1;
					}
					else if ( (LA28_0==66) ) {
						alt28=2;
					}

					else {
						NoViableAltException nvae =
							new NoViableAltException("", 28, 0, input);
						throw nvae;
					}

					switch (alt28) {
						case 1 :
							// Proguard.g:194:45: signature= arguments
							{
							pushFollow(FOLLOW_arguments_in_member1302);
							signature=arguments();
							state._fsp--;

							GrammarActions.method(classSpec, annotation6, typeSig, (name!=null?name.getText():null), signature, modifiers7);
							}
							break;
						case 2 :
							// Proguard.g:195:21: 
							{
							GrammarActions.fieldOrAnyMember(classSpec, annotation6, typeSig, (name!=null?name.getText():null), modifiers7);
							}
							break;

					}

					}
					break;
				case 2 :
					// Proguard.g:196:9: '<methods>'
					{
					match(input,69,FOLLOW_69_in_member1337); 
					GrammarActions.method(classSpec, annotation6,
					          GrammarActions.getSignature("***", 0), "*", "\\("+ GrammarActions.getSignature("...", 0) + "\\)",
					          modifiers7);
					}
					break;
				case 3 :
					// Proguard.g:199:9: '<fields>'
					{
					match(input,67,FOLLOW_67_in_member1349); 
					GrammarActions.field(classSpec, annotation6, null, "*", modifiers7);
					}
					break;

			}

			match(input,66,FOLLOW_66_in_member1359); 
			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
	}
	// $ANTLR end "member"



	// $ANTLR start "annotation"
	// Proguard.g:203:9: private annotation returns [AnnotationSpecification annotSpec] : '@' ( NEGATOR )? NAME ;
	public final AnnotationSpecification annotation() throws RecognitionException {
		AnnotationSpecification annotSpec = null;


		Token NAME8=null;


		  boolean hasNameNegator = false;

		try {
			// Proguard.g:207:3: ( '@' ( NEGATOR )? NAME )
			// Proguard.g:207:6: '@' ( NEGATOR )? NAME
			{
			match(input,70,FOLLOW_70_in_annotation1383); 
			// Proguard.g:207:10: ( NEGATOR )?
			int alt30=2;
			int LA30_0 = input.LA(1);
			if ( (LA30_0==NEGATOR) ) {
				alt30=1;
			}
			switch (alt30) {
				case 1 :
					// Proguard.g:207:11: NEGATOR
					{
					match(input,NEGATOR,FOLLOW_NEGATOR_in_annotation1386); 
					hasNameNegator = true;
					}
					break;

			}

			NAME8=(Token)match(input,NAME,FOLLOW_NAME_in_annotation1392); 
			annotSpec = GrammarActions.annotation((NAME8!=null?NAME8.getText():null), hasNameNegator);
			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return annotSpec;
	}
	// $ANTLR end "annotation"



	// $ANTLR start "modifiers"
	// Proguard.g:209:9: private modifiers returns [ModifierSpecification modifiers] : ( modifier[modifiers] )* ;
	public final ModifierSpecification modifiers() throws RecognitionException {
		ModifierSpecification modifiers = null;



		  modifiers = new ModifierSpecification();

		try {
			// Proguard.g:213:3: ( ( modifier[modifiers] )* )
			// Proguard.g:214:3: ( modifier[modifiers] )*
			{
			// Proguard.g:214:3: ( modifier[modifiers] )*
			loop31:
			while (true) {
				int alt31=2;
				int LA31_0 = input.LA(1);
				if ( (LA31_0==NEGATOR||LA31_0==73||LA31_0==77||LA31_0==81||(LA31_0 >= 85 && LA31_0 <= 95)) ) {
					alt31=1;
				}

				switch (alt31) {
				case 1 :
					// Proguard.g:214:3: modifier[modifiers]
					{
					pushFollow(FOLLOW_modifier_in_modifiers1416);
					modifier(modifiers);
					state._fsp--;

					}
					break;

				default :
					break loop31;
				}
			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return modifiers;
	}
	// $ANTLR end "modifiers"



	// $ANTLR start "modifier"
	// Proguard.g:217:9: private modifier[ModifierSpecification modifiers] : ( NEGATOR )? ( 'public' | 'private' | 'protected' | 'static' | 'synchronized' | 'volatile' | 'native' | 'abstract' | 'strictfp' | 'final' | 'transient' | 'synthetic' | 'bridge' | 'varargs' ) ;
	public final void modifier(ModifierSpecification modifiers) throws RecognitionException {

		  boolean hasNegator = false;

		try {
			// Proguard.g:221:3: ( ( NEGATOR )? ( 'public' | 'private' | 'protected' | 'static' | 'synchronized' | 'volatile' | 'native' | 'abstract' | 'strictfp' | 'final' | 'transient' | 'synthetic' | 'bridge' | 'varargs' ) )
			// Proguard.g:222:3: ( NEGATOR )? ( 'public' | 'private' | 'protected' | 'static' | 'synchronized' | 'volatile' | 'native' | 'abstract' | 'strictfp' | 'final' | 'transient' | 'synthetic' | 'bridge' | 'varargs' )
			{
			// Proguard.g:222:3: ( NEGATOR )?
			int alt32=2;
			int LA32_0 = input.LA(1);
			if ( (LA32_0==NEGATOR) ) {
				alt32=1;
			}
			switch (alt32) {
				case 1 :
					// Proguard.g:222:4: NEGATOR
					{
					match(input,NEGATOR,FOLLOW_NEGATOR_in_modifier1442); 
					hasNegator = true;
					}
					break;

			}

			// Proguard.g:223:3: ( 'public' | 'private' | 'protected' | 'static' | 'synchronized' | 'volatile' | 'native' | 'abstract' | 'strictfp' | 'final' | 'transient' | 'synthetic' | 'bridge' | 'varargs' )
			int alt33=14;
			switch ( input.LA(1) ) {
			case 88:
				{
				alt33=1;
				}
				break;
			case 86:
				{
				alt33=2;
				}
				break;
			case 87:
				{
				alt33=3;
				}
				break;
			case 89:
				{
				alt33=4;
				}
				break;
			case 91:
				{
				alt33=5;
				}
				break;
			case 95:
				{
				alt33=6;
				}
				break;
			case 85:
				{
				alt33=7;
				}
				break;
			case 73:
				{
				alt33=8;
				}
				break;
			case 90:
				{
				alt33=9;
				}
				break;
			case 81:
				{
				alt33=10;
				}
				break;
			case 93:
				{
				alt33=11;
				}
				break;
			case 92:
				{
				alt33=12;
				}
				break;
			case 77:
				{
				alt33=13;
				}
				break;
			case 94:
				{
				alt33=14;
				}
				break;
			default:
				NoViableAltException nvae =
					new NoViableAltException("", 33, 0, input);
				throw nvae;
			}
			switch (alt33) {
				case 1 :
					// Proguard.g:224:5: 'public'
					{
					match(input,88,FOLLOW_88_in_modifier1456); 
					modifiers.addAccessFlag(AccessFlag.PUBLIC, hasNegator);
					}
					break;
				case 2 :
					// Proguard.g:225:7: 'private'
					{
					match(input,86,FOLLOW_86_in_modifier1466); 
					modifiers.addAccessFlag(AccessFlag.PRIVATE, hasNegator);
					}
					break;
				case 3 :
					// Proguard.g:226:7: 'protected'
					{
					match(input,87,FOLLOW_87_in_modifier1476); 
					modifiers.addAccessFlag(AccessFlag.PROTECTED, hasNegator);
					}
					break;
				case 4 :
					// Proguard.g:227:7: 'static'
					{
					match(input,89,FOLLOW_89_in_modifier1486); 
					modifiers.addModifier(Modifier.STATIC, hasNegator);
					}
					break;
				case 5 :
					// Proguard.g:228:7: 'synchronized'
					{
					match(input,91,FOLLOW_91_in_modifier1496); 
					modifiers.addModifier(Modifier.SYNCHRONIZED, hasNegator);
					}
					break;
				case 6 :
					// Proguard.g:229:7: 'volatile'
					{
					match(input,95,FOLLOW_95_in_modifier1506); 
					modifiers.addModifier(Modifier.VOLATILE, hasNegator);
					}
					break;
				case 7 :
					// Proguard.g:230:7: 'native'
					{
					match(input,85,FOLLOW_85_in_modifier1516); 
					modifiers.addModifier(Modifier.NATIVE, hasNegator);
					}
					break;
				case 8 :
					// Proguard.g:231:7: 'abstract'
					{
					match(input,73,FOLLOW_73_in_modifier1526); 
					modifiers.addModifier(Modifier.ABSTRACT, hasNegator);
					}
					break;
				case 9 :
					// Proguard.g:232:7: 'strictfp'
					{
					match(input,90,FOLLOW_90_in_modifier1536); 
					modifiers.addModifier(Modifier.STRICTFP, hasNegator);
					}
					break;
				case 10 :
					// Proguard.g:233:7: 'final'
					{
					match(input,81,FOLLOW_81_in_modifier1546); 
					modifiers.addModifier(Modifier.FINAL, hasNegator);
					}
					break;
				case 11 :
					// Proguard.g:234:7: 'transient'
					{
					match(input,93,FOLLOW_93_in_modifier1556); 
					modifiers.addModifier(Modifier.TRANSIENT, hasNegator);
					}
					break;
				case 12 :
					// Proguard.g:235:7: 'synthetic'
					{
					match(input,92,FOLLOW_92_in_modifier1566); 
					modifiers.addModifier(Modifier.SYNTHETIC, hasNegator);
					}
					break;
				case 13 :
					// Proguard.g:236:7: 'bridge'
					{
					match(input,77,FOLLOW_77_in_modifier1576); 
					modifiers.addModifier(Modifier.BRIDGE, hasNegator);
					}
					break;
				case 14 :
					// Proguard.g:237:7: 'varargs'
					{
					match(input,94,FOLLOW_94_in_modifier1586); 
					modifiers.addModifier(Modifier.VARARGS, hasNegator);
					}
					break;

			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
	}
	// $ANTLR end "modifier"



	// $ANTLR start "inheritance"
	// Proguard.g:241:9: private inheritance returns [InheritanceSpecification inheritanceSpec] : ( 'extends' | 'implements' ) ( annotation )? ( NEGATOR )? NAME ;
	public final InheritanceSpecification inheritance() throws RecognitionException {
		InheritanceSpecification inheritanceSpec = null;


		Token NAME9=null;
		AnnotationSpecification annotation10 =null;


		  boolean hasNameNegator = false;

		try {
			// Proguard.g:245:3: ( ( 'extends' | 'implements' ) ( annotation )? ( NEGATOR )? NAME )
			// Proguard.g:246:3: ( 'extends' | 'implements' ) ( annotation )? ( NEGATOR )? NAME
			{
			if ( input.LA(1)==80||input.LA(1)==82 ) {
				input.consume();
				state.errorRecovery=false;
			}
			else {
				MismatchedSetException mse = new MismatchedSetException(null,input);
				throw mse;
			}
			// Proguard.g:247:3: ( annotation )?
			int alt34=2;
			int LA34_0 = input.LA(1);
			if ( (LA34_0==70) ) {
				alt34=1;
			}
			switch (alt34) {
				case 1 :
					// Proguard.g:247:3: annotation
					{
					pushFollow(FOLLOW_annotation_in_inheritance1627);
					annotation10=annotation();
					state._fsp--;

					}
					break;

			}

			// Proguard.g:247:15: ( NEGATOR )?
			int alt35=2;
			int LA35_0 = input.LA(1);
			if ( (LA35_0==NEGATOR) ) {
				alt35=1;
			}
			switch (alt35) {
				case 1 :
					// Proguard.g:247:16: NEGATOR
					{
					match(input,NEGATOR,FOLLOW_NEGATOR_in_inheritance1631); 
					hasNameNegator = true;
					}
					break;

			}

			NAME9=(Token)match(input,NAME,FOLLOW_NAME_in_inheritance1637); 
			inheritanceSpec = GrammarActions.createInheritance((NAME9!=null?NAME9.getText():null), hasNameNegator, annotation10);
			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return inheritanceSpec;
	}
	// $ANTLR end "inheritance"



	// $ANTLR start "arguments"
	// Proguard.g:249:9: private arguments returns [String signature] : '(' ( (parameterSig= type ( ',' parameterSig= type )* )? ) ')' ;
	public final String arguments() throws RecognitionException {
		String signature = null;


		String parameterSig =null;

		try {
			// Proguard.g:250:3: ( '(' ( (parameterSig= type ( ',' parameterSig= type )* )? ) ')' )
			// Proguard.g:251:3: '(' ( (parameterSig= type ( ',' parameterSig= type )* )? ) ')'
			{
			match(input,9,FOLLOW_9_in_arguments1657); 
			signature = "\\(";
			// Proguard.g:252:5: ( (parameterSig= type ( ',' parameterSig= type )* )? )
			// Proguard.g:253:7: (parameterSig= type ( ',' parameterSig= type )* )?
			{
			// Proguard.g:253:7: (parameterSig= type ( ',' parameterSig= type )* )?
			int alt37=2;
			int LA37_0 = input.LA(1);
			if ( (LA37_0==NAME||LA37_0==8) ) {
				alt37=1;
			}
			switch (alt37) {
				case 1 :
					// Proguard.g:254:9: parameterSig= type ( ',' parameterSig= type )*
					{
					pushFollow(FOLLOW_type_in_arguments1685);
					parameterSig=type();
					state._fsp--;

					signature += parameterSig;
					// Proguard.g:255:9: ( ',' parameterSig= type )*
					loop36:
					while (true) {
						int alt36=2;
						int LA36_0 = input.LA(1);
						if ( (LA36_0==11) ) {
							alt36=1;
						}

						switch (alt36) {
						case 1 :
							// Proguard.g:255:10: ',' parameterSig= type
							{
							match(input,11,FOLLOW_11_in_arguments1698); 
							pushFollow(FOLLOW_type_in_arguments1702);
							parameterSig=type();
							state._fsp--;

							signature += parameterSig;
							}
							break;

						default :
							break loop36;
						}
					}

					}
					break;

			}

			}

			match(input,10,FOLLOW_10_in_arguments1731); 
			signature += "\\)";
			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return signature;
	}
	// $ANTLR end "arguments"



	// $ANTLR start "type"
	// Proguard.g:261:9: private type returns [String signature] : (typeName= ( '%' | NAME ) ( '[]' )* ) ;
	public final String type() throws RecognitionException {
		String signature = null;


		Token typeName=null;


		  int dim = 0;

		try {
			// Proguard.g:265:3: ( (typeName= ( '%' | NAME ) ( '[]' )* ) )
			// Proguard.g:266:3: (typeName= ( '%' | NAME ) ( '[]' )* )
			{
			// Proguard.g:266:3: (typeName= ( '%' | NAME ) ( '[]' )* )
			// Proguard.g:267:5: typeName= ( '%' | NAME ) ( '[]' )*
			{
			typeName=input.LT(1);
			if ( input.LA(1)==NAME||input.LA(1)==8 ) {
				input.consume();
				state.errorRecovery=false;
			}
			else {
				MismatchedSetException mse = new MismatchedSetException(null,input);
				throw mse;
			}
			// Proguard.g:267:27: ( '[]' )*
			loop38:
			while (true) {
				int alt38=2;
				int LA38_0 = input.LA(1);
				if ( (LA38_0==71) ) {
					alt38=1;
				}

				switch (alt38) {
				case 1 :
					// Proguard.g:267:28: '[]'
					{
					match(input,71,FOLLOW_71_in_type1776); 
					dim++;
					}
					break;

				default :
					break loop38;
				}
			}

			String sig = (typeName!=null?typeName.getText():null); signature = GrammarActions.getSignature(sig == null ? "" : sig, dim);
			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return signature;
	}
	// $ANTLR end "type"



	// $ANTLR start "keepOptionModifier"
	// Proguard.g:271:9: private keepOptionModifier returns [KeepModifier modifier] : ( ',' ( 'allowshrinking' | 'allowoptimization' | 'includedescriptorclasses' | 'allowobfuscation' ) )* ;
	public final KeepModifier keepOptionModifier() throws RecognitionException {
		KeepModifier modifier = null;



		  modifier = new KeepModifier();

		try {
			// Proguard.g:275:3: ( ( ',' ( 'allowshrinking' | 'allowoptimization' | 'includedescriptorclasses' | 'allowobfuscation' ) )* )
			// Proguard.g:275:5: ( ',' ( 'allowshrinking' | 'allowoptimization' | 'includedescriptorclasses' | 'allowobfuscation' ) )*
			{
			// Proguard.g:275:5: ( ',' ( 'allowshrinking' | 'allowoptimization' | 'includedescriptorclasses' | 'allowobfuscation' ) )*
			loop40:
			while (true) {
				int alt40=2;
				int LA40_0 = input.LA(1);
				if ( (LA40_0==11) ) {
					alt40=1;
				}

				switch (alt40) {
				case 1 :
					// Proguard.g:275:6: ',' ( 'allowshrinking' | 'allowoptimization' | 'includedescriptorclasses' | 'allowobfuscation' )
					{
					match(input,11,FOLLOW_11_in_keepOptionModifier1811); 
					// Proguard.g:276:3: ( 'allowshrinking' | 'allowoptimization' | 'includedescriptorclasses' | 'allowobfuscation' )
					int alt39=4;
					switch ( input.LA(1) ) {
					case 76:
						{
						alt39=1;
						}
						break;
					case 75:
						{
						alt39=2;
						}
						break;
					case 83:
						{
						alt39=3;
						}
						break;
					case 74:
						{
						alt39=4;
						}
						break;
					default:
						NoViableAltException nvae =
							new NoViableAltException("", 39, 0, input);
						throw nvae;
					}
					switch (alt39) {
						case 1 :
							// Proguard.g:276:4: 'allowshrinking'
							{
							match(input,76,FOLLOW_76_in_keepOptionModifier1816); 
							modifier.setAllowShrinking();
							}
							break;
						case 2 :
							// Proguard.g:277:5: 'allowoptimization'
							{
							match(input,75,FOLLOW_75_in_keepOptionModifier1824); 
							}
							break;
						case 3 :
							// Proguard.g:278:5: 'includedescriptorclasses'
							{
							match(input,83,FOLLOW_83_in_keepOptionModifier1831); 
							}
							break;
						case 4 :
							// Proguard.g:279:5: 'allowobfuscation'
							{
							match(input,74,FOLLOW_74_in_keepOptionModifier1838); 
							modifier.setAllowObfuscation();
							}
							break;

					}

					}
					break;

				default :
					break loop40;
				}
			}

			}

		}
		catch (RecognitionException re) {
			reportError(re);
			recover(input,re);
		}
		finally {
			// do for sure before leaving
		}
		return modifier;
	}
	// $ANTLR end "keepOptionModifier"

	// Delegated rules



	public static final BitSet FOLLOW_18_in_prog81 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_prog85 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_set_in_prog96 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_prog104 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_40_in_prog115 = new BitSet(new long[]{0x0000000000000840L,0x000000000112C240L});
	public static final BitSet FOLLOW_keepOptionModifier_in_prog119 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_prog123 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_38_in_prog135 = new BitSet(new long[]{0x0000000000000840L,0x000000000112C240L});
	public static final BitSet FOLLOW_keepOptionModifier_in_prog139 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_prog143 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_35_in_prog155 = new BitSet(new long[]{0x0000000000000840L,0x000000000112C240L});
	public static final BitSet FOLLOW_keepOptionModifier_in_prog159 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_prog163 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_unsupportedFlag_in_prog177 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_28_in_prog189 = new BitSet(new long[]{0xFFFFFFFFFFFFF060L,0x0000000000000041L});
	public static final BitSet FOLLOW_filter_in_prog193 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_32_in_prog206 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_61_in_prog218 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_prog222 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_64_in_prog234 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_prog238 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_24_in_prog250 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_22_in_prog263 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_21_in_prog276 = new BitSet(new long[]{0xFFFFFFFFFFFFF000L,0x0000000000000041L});
	public static final BitSet FOLLOW_EOF_in_prog288 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_15_in_unsupportedFlag321 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_19_in_unsupportedFlag329 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag333 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_23_in_unsupportedFlag341 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_25_in_unsupportedFlag349 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_26_in_unsupportedFlag357 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_27_in_unsupportedFlag365 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_31_in_unsupportedFlag373 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_34_in_unsupportedFlag381 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_classpath_in_unsupportedFlag385 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_44_in_unsupportedFlag393 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_45_in_unsupportedFlag401 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_classpath_in_unsupportedFlag405 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_46_in_unsupportedFlag413 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_47_in_unsupportedFlag421 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_48_in_unsupportedFlag429 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag433 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_51_in_unsupportedFlag441 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_classpath_in_unsupportedFlag445 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_52_in_unsupportedFlag453 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_53_in_unsupportedFlag461 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag465 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_55_in_unsupportedFlag473 = new BitSet(new long[]{0x0000000000000022L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag477 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_60_in_unsupportedFlag486 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_62_in_unsupportedFlag494 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_63_in_unsupportedFlag502 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_12_in_unsupportedFlag511 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag515 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_13_in_unsupportedFlag526 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag530 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_14_in_unsupportedFlag542 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag546 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_16_in_unsupportedFlag558 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag562 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_17_in_unsupportedFlag573 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_unsupportedFlag575 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_20_in_unsupportedFlag585 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag589 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_29_in_unsupportedFlag600 = new BitSet(new long[]{0x0000000000000022L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag602 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_30_in_unsupportedFlag614 = new BitSet(new long[]{0x0000000000000002L,0x0000000000000100L});
	public static final BitSet FOLLOW_72_in_unsupportedFlag617 = new BitSet(new long[]{0x0000000000000020L,0x0000000000000100L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag621 = new BitSet(new long[]{0x0000000000000000L,0x0000000000000100L});
	public static final BitSet FOLLOW_72_in_unsupportedFlag624 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_36_in_unsupportedFlag637 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag641 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_37_in_unsupportedFlag653 = new BitSet(new long[]{0x0000000000000840L,0x000000000112C240L});
	public static final BitSet FOLLOW_keepOptionModifier_in_unsupportedFlag657 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_unsupportedFlag661 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_39_in_unsupportedFlag673 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_unsupportedFlag677 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_41_in_unsupportedFlag689 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag693 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_42_in_unsupportedFlag704 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classSpecification_in_unsupportedFlag708 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_43_in_unsupportedFlag719 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag723 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_49_in_unsupportedFlag735 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag737 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_50_in_unsupportedFlag748 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_filter_in_unsupportedFlag752 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_54_in_unsupportedFlag763 = new BitSet(new long[]{0x0000000000000022L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag765 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_56_in_unsupportedFlag777 = new BitSet(new long[]{0x0000000000000022L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag781 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_57_in_unsupportedFlag793 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag795 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_58_in_unsupportedFlag806 = new BitSet(new long[]{0x0000000000000022L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag810 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_59_in_unsupportedFlag821 = new BitSet(new long[]{0x0000000000000002L,0x0000000000000100L});
	public static final BitSet FOLLOW_72_in_unsupportedFlag824 = new BitSet(new long[]{0x0000000000000020L,0x0000000000000100L});
	public static final BitSet FOLLOW_NAME_in_unsupportedFlag828 = new BitSet(new long[]{0x0000000000000000L,0x0000000000000100L});
	public static final BitSet FOLLOW_72_in_unsupportedFlag831 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_NAME_in_classpath855 = new BitSet(new long[]{0x0000000000000002L,0x0000000000000006L});
	public static final BitSet FOLLOW_set_in_classpath858 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_classpath_in_classpath864 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_nonEmptyFilter_in_filter885 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_NEGATOR_in_nonEmptyFilter919 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_nonEmptyFilter925 = new BitSet(new long[]{0x0000000000000802L});
	public static final BitSet FOLLOW_11_in_nonEmptyFilter930 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_nonEmptyFilter_in_nonEmptyFilter932 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_annotation_in_classSpecification962 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classModifierAndType_in_classSpecification970 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_classNames_in_classSpecification975 = new BitSet(new long[]{0x0000000000000002L,0x0000000100050000L});
	public static final BitSet FOLLOW_inheritance_in_classSpecification984 = new BitSet(new long[]{0x0000000000000002L,0x0000000100000000L});
	public static final BitSet FOLLOW_members_in_classSpecification992 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_className_in_classNames1021 = new BitSet(new long[]{0x0000000000000802L});
	public static final BitSet FOLLOW_11_in_classNames1028 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_className_in_classNames1032 = new BitSet(new long[]{0x0000000000000802L});
	public static final BitSet FOLLOW_NEGATOR_in_className1061 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_className1069 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_NEGATOR_in_classModifierAndType1096 = new BitSet(new long[]{0x0000000000000000L,0x000000000112C240L});
	public static final BitSet FOLLOW_88_in_classModifierAndType1108 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classModifierAndType_in_classModifierAndType1114 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_73_in_classModifierAndType1123 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classModifierAndType_in_classModifierAndType1129 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_81_in_classModifierAndType1138 = new BitSet(new long[]{0x0000000000000040L,0x000000000112C240L});
	public static final BitSet FOLLOW_classModifierAndType_in_classModifierAndType1144 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_classType_in_classModifierAndType1153 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_70_in_classType1186 = new BitSet(new long[]{0x0000000000000000L,0x000000000010C000L});
	public static final BitSet FOLLOW_84_in_classType1195 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_79_in_classType1203 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_78_in_classType1211 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_96_in_members1234 = new BitSet(new long[]{0x0000000000000160L,0x00000002FFE22278L});
	public static final BitSet FOLLOW_member_in_members1240 = new BitSet(new long[]{0x0000000000000160L,0x00000002FFE22278L});
	public static final BitSet FOLLOW_97_in_members1246 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_annotation_in_member1267 = new BitSet(new long[]{0x0000000000000160L,0x00000000FFE22238L});
	public static final BitSet FOLLOW_modifiers_in_member1270 = new BitSet(new long[]{0x0000000000000120L,0x0000000000000038L});
	public static final BitSet FOLLOW_type_in_member1287 = new BitSet(new long[]{0x0000000000000020L,0x0000000000000010L});
	public static final BitSet FOLLOW_set_in_member1293 = new BitSet(new long[]{0x0000000000000200L,0x0000000000000004L});
	public static final BitSet FOLLOW_arguments_in_member1302 = new BitSet(new long[]{0x0000000000000000L,0x0000000000000004L});
	public static final BitSet FOLLOW_69_in_member1337 = new BitSet(new long[]{0x0000000000000000L,0x0000000000000004L});
	public static final BitSet FOLLOW_67_in_member1349 = new BitSet(new long[]{0x0000000000000000L,0x0000000000000004L});
	public static final BitSet FOLLOW_66_in_member1359 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_70_in_annotation1383 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_NEGATOR_in_annotation1386 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_annotation1392 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_modifier_in_modifiers1416 = new BitSet(new long[]{0x0000000000000042L,0x00000000FFE22200L});
	public static final BitSet FOLLOW_NEGATOR_in_modifier1442 = new BitSet(new long[]{0x0000000000000000L,0x00000000FFE22200L});
	public static final BitSet FOLLOW_88_in_modifier1456 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_86_in_modifier1466 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_87_in_modifier1476 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_89_in_modifier1486 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_91_in_modifier1496 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_95_in_modifier1506 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_85_in_modifier1516 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_73_in_modifier1526 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_90_in_modifier1536 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_81_in_modifier1546 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_93_in_modifier1556 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_92_in_modifier1566 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_77_in_modifier1576 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_94_in_modifier1586 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_set_in_inheritance1617 = new BitSet(new long[]{0x0000000000000060L,0x0000000000000040L});
	public static final BitSet FOLLOW_annotation_in_inheritance1627 = new BitSet(new long[]{0x0000000000000060L});
	public static final BitSet FOLLOW_NEGATOR_in_inheritance1631 = new BitSet(new long[]{0x0000000000000020L});
	public static final BitSet FOLLOW_NAME_in_inheritance1637 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_9_in_arguments1657 = new BitSet(new long[]{0x0000000000000520L});
	public static final BitSet FOLLOW_type_in_arguments1685 = new BitSet(new long[]{0x0000000000000C00L});
	public static final BitSet FOLLOW_11_in_arguments1698 = new BitSet(new long[]{0x0000000000000120L});
	public static final BitSet FOLLOW_type_in_arguments1702 = new BitSet(new long[]{0x0000000000000C00L});
	public static final BitSet FOLLOW_10_in_arguments1731 = new BitSet(new long[]{0x0000000000000002L});
	public static final BitSet FOLLOW_set_in_type1767 = new BitSet(new long[]{0x0000000000000002L,0x0000000000000080L});
	public static final BitSet FOLLOW_71_in_type1776 = new BitSet(new long[]{0x0000000000000002L,0x0000000000000080L});
	public static final BitSet FOLLOW_11_in_keepOptionModifier1811 = new BitSet(new long[]{0x0000000000000000L,0x0000000000081C00L});
	public static final BitSet FOLLOW_76_in_keepOptionModifier1816 = new BitSet(new long[]{0x0000000000000802L});
	public static final BitSet FOLLOW_75_in_keepOptionModifier1824 = new BitSet(new long[]{0x0000000000000802L});
	public static final BitSet FOLLOW_83_in_keepOptionModifier1831 = new BitSet(new long[]{0x0000000000000802L});
	public static final BitSet FOLLOW_74_in_keepOptionModifier1838 = new BitSet(new long[]{0x0000000000000802L});
}
