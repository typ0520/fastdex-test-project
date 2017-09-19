#!/bin/bash

sh gradlew assembleDebug

test_increment_compile() {
    echo "========测试${1}是否支持增量, ${2}"

    str=$(stat -x ${1}/build/intermediates/classes/debug/com/github/typ0520/annotation_generators/MainActivity.class | grep 'Modify')
    echo $str

    echo 'package com.github.typ0520.annotation_generators;' > ${1}/src/main/java/com/github/typ0520/annotation_generators/HAHA.java

    echo 'public class HAHA {' >> ${1}/src/main/java/com/github/typ0520/annotation_generators/HAHA.java
    echo "    public long millis = $(date +%s);" >> ${1}/src/main/java/com/github/typ0520/annotation_generators/HAHA.java
    echo '}' >> ${1}/src/main/java/com/github/typ0520/annotation_generators/HAHA.java

    sh gradlew ${1}:assembleDebug > /dev/null

    str2=$(stat -x ${1}/build/intermediates/classes/debug/com/github/typ0520/annotation_generators/MainActivity.class  | grep 'Modify')
    echo $str2

    echo ' '
    if [ "$str" == "$str2" ];then
        echo "${1}只修改HAHA.java，MainActivity.class没有发生变化"
    else
        echo "${1}只修改HAHA.java，MainActivity.class发生变化"
    fi
}

test_increment_compile app "compile 'com.jakewharton:butterknife:7.0.1'"
test_increment_compile app2 "annotationProcessor 'com.jakewharton:butterknife-compiler:8.8.1'"
test_increment_compile app3 "没有用任何AbstractProcessor"