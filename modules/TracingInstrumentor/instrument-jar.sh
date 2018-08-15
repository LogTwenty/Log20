#/bin/bash
set -e

Usage="Usage: instrument-jar.sh <package inclusion list> <package exclusion list> <entry point list> <input jar> <output jar>"
if [ "$#" -lt 5 ] ; then
	echo $Usage
	exit
fi
packInclList="$1"
packExclList="$2"
reqEntryList="$3"
inputJar="$4"
outputJar="$5"

scriptDir="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

# Class path to run application and Soot
classPath="${JAVA8_HOME}/jre/lib/rt.jar"
classPath="$classPath:${JAVA8_HOME}/jre/lib/jce.jar"
classPath="$classPath:$SOOT_HOME/soot-trunk.jar"
classPath="$classPath:$scriptDir/bin/."
classPath="$classPath:$scriptDir/lib/InstrumentationTools.jar"

# Unzipped jar output
inputDir=/tmp/jarOutput/
rm -rf $inputDir
mkdir -p $inputDir

# Unzip jar
unzip -qq $inputJar -d $inputDir

# Generate list of classes
classPathList=$inputDir/classPaths.txt
find $inputDir -type f -name "*.class" | cut -d'/' -f4- > $classPathList

# Class path used for analysis
anaClassPath="${JAVA7_HOME}/jre/lib/rt.jar"
anaClassPath="$anaClassPath:${JAVA7_HOME}/jre/lib/jce.jar"
anaClassPath="$anaClassPath:$scriptDir/lib/InstrumentationTools.jar"
anaClassPath="$anaClassPath:$inputDir"

methodSigToIdPath="MethodSignatureMapping.log"
methodIdStart=0
if [ -e $methodSigToIdPath ] ; then
	methodIdStart=$(tail -n1 $methodSigToIdPath)
	tail -n1 $methodSigToIdPath | wc -c | xargs -I {} truncate $methodSigToIdPath -s -{}
fi

# Transformed classes output
outputDir=/tmp/sootOutput/
rm -rf $outputDir
mkdir -p $outputDir

# Generate instrumented classes
java -cp ${classPath} ca.utoronto.dsrg.TracingInstrumentor $anaClassPath $packInclList $packExclList $reqEntryList $methodIdStart $classPathList $outputDir

# Unzip rest of jar
unzip -qq -n $inputJar -d $outputDir

# Put contents into outputJar
jar -cfm $outputJar $outputDir/META-INF/MANIFEST.MF -C $outputDir .

# Remove temporary dirs
rm -rf $inputDir
rm -rf $outputDir
