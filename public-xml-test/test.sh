#!/bin/bash

aapt=/Users/tong/Applications/android-sdk-macosx/build-tools/24.0.0/aapt
android_jar=/Users/tong/Applications/android-sdk-macosx/platforms/android-25/android.jar

function setup {
	if [ -d 'gen' ];then
		rm -rf gen
	fi
	mkdir gen
	if [ -f 'resources.ap_' ];then
		rm resources.ap_
	fi

	if [ -f 'public.xml' ];then
		rm public.xml
	fi
}

function normal_generate {
	setup

	${aapt} package -f -m -J ./gen  -S res -M AndroidManifest.xml -I ${android_jar}
	${aapt} package -f -M AndroidManifest.xml -S res -I ${android_jar} -F resources.ap_	
}

function generate_with_public_xml {
	setup

	${aapt} package -f -m -J ./gen -P public.xml  -S res -M AndroidManifest.xml -I ${android_jar}
	${aapt} package -f -M AndroidManifest.xml -S res -I ${android_jar} -F resources.ap_	
}

test_count=10
for ((i=1; i<=${test_count}; i ++))  
do  
	start_time=$(date '+%s')
    normal_generate
    end=$(date '+%s')
    normal_generate_use_time=$(echo ${end} - ${start_time} | bc)

    start_time=$(date '+%s')
    generate_with_public_xml
    end=$(date '+%s')
    generate_with_public_xml_use_time=$(echo ${end} - ${start_time} | bc)

    echo "index: ${i} generate complete normal_use: ${normal_generate_use_time}s ,public_xml_use: ${generate_with_public_xml_use_time}s"
done 

