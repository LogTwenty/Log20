#!/usr/bin/env bash

DIR_project=`pwd`/../..
DIR_project=`realpath $DIR_project`
DIR_logStrategyGeneratorSrc=`pwd`"/src/ca/utoronto/dsrg/twentyqs/"
DIR_dependencies=`pwd`"/lib"
classPath="$classPath:${DIR_dependencies}/soot-trunk.jar:${DIR_dependencies}/commons-codec-1.10.jar:${DIR_dependencies}/LoggableVarFinder.jar"

## compile Log Strategy Generator
#classes="${DIR_logStrategyGeneratorSrc}/BasicBlockTrace.java ${DIR_logStrategyGeneratorSrc}/DistinguishedSet.java ${DIR_logStrategyGeneratorSrc}/LogStrategyCell.java ${DIR_logStrategyGeneratorSrc}/PathSig.java ${DIR_logStrategyGeneratorSrc}/LogStrategyGenerator.java ${DIR_logStrategyGeneratorSrc}/Trace.java ${DIR_logStrategyGeneratorSrc}/Utils.java ${DIR_logStrategyGeneratorSrc}/BallLarusProfiling.java ${DIR_logStrategyGeneratorSrc}/Graph.java"
#javac -cp ${classPath} ${classes}

# Note: source code is pre-compiled in the out/production/LogStrategyGenerator folder
cd ${DIR_project}/bin/production/LogStrategyGenerator

# run hadoop example
#java -cp ${classPath} ca.utoronto.dsrg.twentyqs.LogStrategyGenerator --test-home ${DIR_project}/examples/LogStrategyGeneratorExamples/targetPrograms/hadoop/hdfs/trace  --threshold 1.0

# run simple target program example
java -cp ${classPath} ca.utoronto.dsrg.twentyqs.LogStrategyGenerator --test-home ${DIR_project}/examples/LogStrategyGeneratorExamples/targetPrograms/simpleTargetProgram/trace  --threshold 1.0
