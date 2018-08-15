#/bin/bash
set -e

Usage="Usage: instrument-app.sh <package inclusion list> <package exclusion list> <entry point list> <input jars list> <output dir>"
if [ "$#" -lt 5 ] ; then
	echo $Usage
	exit
fi
packInclList="$1"
packExclList="$2"
reqEntryList="$3"
inputJarList="$4"
outputDir="$5"

scriptDir="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

methodSigToIdPath="MethodSignatureMapping.log"
if [ -e "$methodSigToIdPath" ] ; then
	rm $methodSigToIdPath
fi

bbPropsPath="BBProperties.log"
if [ -e "$bbPropsPath" ] ; then
	rm $bbPropsPath
fi
echo "methodSig	bbId	numTrace	numDebug	numInfo	numWarn	numError	numFatal	beginLineNo	endLineNo	predIds	succIds" > $bbPropsPath

foundReqEntryList=/tmp/foundReqEntryList.txt
rm -f $foundReqEntryList

cat $inputJarList | while read line ; do
	# Ignore comments
	if [[ $line == \#* ]] ; then
		continue
	fi

	# Ignore empty lines
	if [[ "$line" == "" ]] ; then
		continue
	fi

	jarName=$(basename $line)
	$scriptDir/instrument-jar.sh $packInclList $packExclList $reqEntryList $line $outputDir/$jarName >> $foundReqEntryList
done

cleanReqEntryList=/tmp/cleanReqEntryList.txt
rm -f $cleanReqEntryList
cat $reqEntryList | while read line ; do
	# Ignore comments
	if [[ $line == \#* ]] ; then
		continue
	fi

	# Ignore empty lines
	if [[ "$line" == "" ]] ; then
		continue
	fi

	echo "$line" >> $cleanReqEntryList
done

comm -3 <(sort $foundReqEntryList | uniq) <(sort $cleanReqEntryList | uniq) | while read line ; do
	echo "ERROR Entry point not found: $line"
done
