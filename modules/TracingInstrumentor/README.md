Programs to analyze and instrument jars with tracing code

# Applications
`instrument-app.sh`
Instruments a list of jars with tracing code

`instrument-jar.sh`
Instruments a jar with tracing code

`print-bbs.sh`
Print basic blocks for a list of methods

`print-methodsigs.sh`
Print Soot method signatures for all methods in a jar

`replace-class-in-jar.sh`
Replace a given class in a jar

`run-soot.sh`
Run Soot with provided arguments

# Arguments
./instrument-jar-env.sh <package inclusion list>  <package exclusion list> <request entry point list> <original jar> <traced jar>
Example configs for HDFS can be found in: log20-release/modules/TracingInstrumentor/test/hdfs_datanode.
