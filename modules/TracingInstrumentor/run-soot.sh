#/bin/bash
set -e

Usage="Usage: run-soot.sh [<soot args>]"

scriptDir="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

classPath="${JAVA8_HOME}/jre/lib/rt.jar"
classPath="$classPath:${JAVA8_HOME}/jre/lib/jce.jar"
classPath="$classPath:$SOOT_HOME/soot-trunk.jar"
classPath="$classPath:$scriptDir/bin/."

java -cp ${classPath} soot.Main $*
