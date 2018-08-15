#/bin/bash
set -e

Usage="Usage: print-methodsigs.sh <input jar>"
if [ "$#" -lt 1 ] ; then
	echo $Usage
	exit
fi
inputJar="$1"

scriptDir="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

# Unzipped jar
inputDir=/tmp/jarOutput/
rm -rf $inputDir
mkdir -p $inputDir

unzip -qq $inputJar -d $inputDir

# Generate list of classes
classPathList=$inputDir/classPaths.txt
pushd $inputDir
find . -type f -name "*.class" | cut -d'/' -f2- > $classPathList
popd

# Classpath for running program and Soot
classPath="${JAVA8_HOME}/jre/lib/rt.jar"
classPath="$classPath:${JAVA8_HOME}/jre/lib/jce.jar"
classPath="$classPath:$SOOT_HOME/soot-trunk.jar"
classPath="$classPath:$scriptDir/bin/."

# Classpath for analysis
anaClassPath="${JAVA7_HOME}/jre/lib/rt.jar"
anaClassPath="$anaClassPath:${JAVA7_HOME}/jre/lib/jce.jar"
anaClassPath="$anaClassPath:$inputDir"

# Generate instrumented classes
java -cp ${classPath} ca.utoronto.dsrg.MethodSigsPrinter $anaClassPath $classPathList

# Remove temporary dirs
rm -rf $inputDir
rm -rf sootOutput
