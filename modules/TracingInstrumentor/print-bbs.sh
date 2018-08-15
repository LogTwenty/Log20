#/bin/bash
set -e

Usage="Usage: print-bbs.sh <input jars list> <method signature list> <output dir>"
if [ "$#" -lt 3 ] ; then
	echo $Usage
	exit
fi
inputJarList="$1"
methodSigList="$2"
outputDir="$3"

scriptDir="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

# Class path for running program and Soot
classPath="${JAVA8_HOME}/jre/lib/rt.jar"
classPath="$classPath:${JAVA8_HOME}/jre/lib/jce.jar"
classPath="$classPath:$SOOT_HOME/soot-trunk.jar"
classPath="$classPath:$scriptDir/bin/."

# Class path for analysis
anaClassPath="${JAVA7_HOME}/jre/lib/rt.jar"
anaClassPath="$anaClassPath:${JAVA7_HOME}/jre/lib/jce.jar"
while read line ; do
	anaClassPath="$anaClassPath:$line"
done < $inputJarList

java -cp ${classPath} ca.utoronto.dsrg.TracingBBPrinter $anaClassPath $methodSigList $outputDir
