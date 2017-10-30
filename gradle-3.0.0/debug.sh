#!/bin/bash

export GRADLE_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"

./gradlew assembleDebug -Dorg.gradle.daemon=false #!important, disable daemon mode


#unset GRADLE_OPTS