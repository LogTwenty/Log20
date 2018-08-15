#/bin/bash

# Script to run tester

# For Hadoop analyses
classPath="/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar"
classPath="$classPath:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar"
classPath="$classPath:$SOOT_HOME/soot-trunk.jar"
classPath="$classPath:./bin/."
classPath="$classPath:/homes/rodri228/sw/hadoop/hadoop-2.6.0/share/hadoop/common/lib/*"
classPath="$classPath:/homes/rodri228/sw/hadoop/hadoop-2.6.0/share/hadoop/common/*"
classPath="$classPath:/homes/rodri228/sw/hadoop/hadoop-2.6.0/share/hadoop/hdfs/lib/*"
classPath="$classPath:/homes/rodri228/sw/hadoop/hadoop-2.6.0/share/hadoop/hdfs/*"
java -cp ${classPath} ca.utoronto.dsrg.twentyqs.LVFTester $*
