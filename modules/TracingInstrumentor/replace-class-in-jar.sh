#/bin/bash
set -e

Usage="Usage: replace-class-in-jar.sh <input jar> <output jar> <class signature>"
if [ "$#" -lt 3 ] ; then
	echo $Usage
	exit
fi
inputJar="$1"
outputJar="$2"
classSig="$3"

scriptDir="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

# Classpath for running program and Soot
classPath="${JAVA8_HOME}/jre/lib/rt.jar"
classPath="$classPath:${JAVA8_HOME}/jre/lib/jce.jar"
classPath="$classPath:$SOOT_HOME/soot-trunk.jar"
classPath="$classPath:$scriptDir/bin/."

# Unzipped jar
jarOutputDir=/tmp/jarOutput/
rm -rf $jarOutputDir
mkdir -p $jarOutputDir

# Unzip jar
unzip -qq -n $inputJar -d $jarOutputDir

# Classpath for analysis
anaClassPath="${JAVA7_HOME}/jre/lib/rt.jar"
anaClassPath="$anaClassPath:${JAVA7_HOME}/jre/lib/jce.jar"
anaClassPath="$anaClassPath:$jarOutputDir"

# Generate and replace class
java -cp ${classPath} soot.Main -allow-phantom-refs -output-dir $jarOutputDir -cp $anaClassPath $classSig

# Zip up jar
jar -cfm $outputJar $jarOutputDir/META-INF/MANIFEST.MF -C $jarOutputDir .

# Remove temporary folder
rm -rf $jarOutputDir
