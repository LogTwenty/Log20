#/bin/bash

# Script to run tester

set -e

Usage="Usage: find-in-jar.sh <package inclusion list> <package exclusion list> <input jar list> <output list>"
if [ "$#" -lt 4 ] ; then
	echo $Usage
	exit
fi
packInclList="$1"
packExclList="$2"
inputJarList="$3"
outputFile="$4"

scriptDir="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

inputDir=/tmp/jarOutput/

classPath="${JAVA8_HOME}/jre/lib/rt.jar"
classPath="$classPath:${JAVA8_HOME}/jre/lib/jce.jar"
classPath="$classPath:$SOOT_HOME/soot-trunk.jar"
classPath="$classPath:$scriptDir/bin/."

anaClassPath="${JAVA7_HOME}/jre/lib/rt.jar"
anaClassPath="$anaClassPath:${JAVA7_HOME}/jre/lib/jce.jar"
anaClassPath="$anaClassPath:$inputDir"

echo "methodSig	bbId	disamBlockIds	disamVarNames	postDomDisamBlockIds	postDomDisamVarNames	# disam	# vars" > $outputFile

cat $inputJarList | while read inputJar ; do
	rm -rf $inputDir
	mkdir -p $inputDir

	unzip -qq $inputJar -d $inputDir

	classPathList=$inputDir/classPaths.txt
	find $inputDir -type f -name "*.class" | cut -d'/' -f4- > $classPathList

	java -cp ${classPath} ca.utoronto.dsrg.twentyqs.LoggableVarFinder $anaClassPath $packInclList $packExclList $classPathList $outputFile

	rm -rf $inputDir
	rm -rf sootOutput
done

