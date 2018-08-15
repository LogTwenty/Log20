# This script is using Python 3.6

# Usage: python decodeMethodAndBasicBlock.py <xxxxx>-<yy> <path to MethodSignatureMapping.log> <path to BBProperties.log>

import sys
import os

print("current working directory is: " + os.getcwd())
targetString = sys.argv[1]
methodSignatureMappingPath = sys.argv[2]
bbPropertiesPath = sys.argv[3]

print("targetString: " + targetString)
print("methodSignatureMappingPath: " + methodSignatureMappingPath)
print("bbPropertiesPath: " + bbPropertiesPath)

methodSignatureID = targetString.split("-")[0]
basicBlockID = targetString.split("-")[1]

lines = open(methodSignatureMappingPath).read().split("\n")
methodSignatureMapping = {}
for line in lines[:-1]:  # get rid of the last line
    if (line == ""):
        continue
    key = line.split(":")[0].split("[")[1].split("]")[0]    # extract the only the value, then convert to string
    methodSignatureMapping[key] = line[line.find(":")+1:]


lines = open(bbPropertiesPath).read().split("\n")
bbIDMapping = {}
for line in lines[1:]:  # ignore the 1st line
    if (line == ""):
        continue
    components = line.split("\t")
    key = components[1]
    bbIDMapping[key] = {"methodSignature": components[0], "numTrace": components[2], "numDebug": components[3],
                         "numInfo": components[4], "numWarn": components[5], "numError": components[6],
                         "numFatal": components[7], "beginLineNo": components[8], "endLineNo": components[9],
                         "predIds": components[10], "succIds": components[11]}

print("*****************************************")
print("Target String: " + targetString)
print("methodSignature: " + methodSignatureMapping[methodSignatureID])
print("sourceCodeBeginLine#: " + bbIDMapping[basicBlockID]["beginLineNo"])
print("sourceCodeEndLine#: " + bbIDMapping[basicBlockID]["endLineNo"])
print("test")