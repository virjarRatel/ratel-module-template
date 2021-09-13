#!/usr/bin/env bash

now_dir=`pwd`
cd `dirname $0`
script_dir=`pwd`

cd ..

./gradlew createhelper:assemble
helper_jar=`pwd`/createhelper/build/libs/ratel-create-helper-1.0.jar

cp ${helper_jar} ${script_dir}/ratel-create-helper-1.0.jar

echo "install finish ${script_dir}/ratel-create-helper-1.0.jar"