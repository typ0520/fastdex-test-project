#!/bin/bash

./gradlew  clean  assembleDebug  > output.txt 2>&1

./gradlew  clean  assembleRelease  > output.txt 2>&1