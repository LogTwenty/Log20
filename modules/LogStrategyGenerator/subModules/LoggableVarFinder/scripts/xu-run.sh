#/bin/bash

# Script to run tester

# For Hadoop analyses
classPath="/usr/lib64/jvm/java-1.7.0-openjdk/jre/lib/rt.jar"
classPath="$classPath:${HOME}/git/info-log-analysis/libs/soot-trunk.jar"
classPath="$classPath:../../out/production/loggable-var-finder"
classPath="$classPath:${HOME}/code/hadoop-2.6.0/share/hadoop/common/lib/*"
classPath="$classPath:${HOME}/code/hadoop-2.6.0/share/hadoop/common/*"
classPath="$classPath:${HOME}/code/hadoop-2.6.0/share/hadoop/hdfs/lib/*"
classPath="$classPath:${HOME}/code/hadoop-2.6.0/share/hadoop/hdfs/*"
java -cp ${classPath} ca.utoronto.dsrg.twentyqs.LVFTester $*
