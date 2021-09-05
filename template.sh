#!/usr/bin/env bash

now_dir=`pwd`
cd `dirname $0`
script_dir=`pwd`

./gradlew createhelper:assemble
transformer_jar=`pwd`/createhelper/build/libs/ratel-create-helper-1.0.jar

java -jar ${transformer_jar}  $*