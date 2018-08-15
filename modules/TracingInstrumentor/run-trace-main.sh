rm -rf ./bin
mkdir -p bin

DIR='./src/ca/utoronto/dsrg'

CLASSES="${DIR}/MethodSigsPrinter.java ${DIR}/TracingBBPrinter.java ${DIR}/TracingInstrumentor.java"

javac -cp "./lib/soot-trunk.jar:./lib/InstrumentationTools.jar" ${CLASSES} -d bin
./instrument-jar-env.sh ./out/inc.txt  ./out/exc.txt  ./out/entry.txt ./out/hadoop-hdfs-2.0.2-alpha.orig.jar ./out/hadoop-hdfs-2.0.2-alpha.traced.jar


echo "Deploying traced jar to HDFS..."
cp ./out/hadoop-hdfs-2.0.2-alpha.traced.jar ${HOME}/code/hadoop-2.0.2-alpha-src/hadoop-dist/target/hadoop-2.0.2-alpha/share/hadoop/hdfs/
echo "Deploy success!"
# Run it with Java 1.8.0
