#!/usr/bin/env bash

now_dir=`pwd`
cd `dirname $0`
script_dir=`pwd`


helper_jar=`pwd`/script/ratel-create-helper-1.0.jar

java -jar ${helper_jar}  $*