# Log20: Automated Log Printing Statements Placement

## Motivation
When systems fail in production environments, log data is often
the only information available to programmers for postmortem debugging.
Consequently, programmers’ decision on **where** to place a log statement
is of crucial importance, as it directly affects how effective
and efficient postmortem debugging can be. This collection of modules
forms the Log20 tool which determines the optimal placement of
log printing directives under the constraint of adding less than a
specified amount of performance overhead.

## What's included? 
Selected modules and examples from the Log20 project are included 
in this release for Opera Software.
This package is mainly used to demonstrate the end to end methodology used 
to suggest log printing statement placement in a trivial example 
and more complex Hadoop HDFS example. 

This Log20 release is composed of 3 modules.
The 1st module is a very light-weight request instrumentation library
which can record the a running program's current location and latency
from previous instrumentation point up to basic block granularity.
This module is packaged as a pre-built JAR (`examples/InstrumentationToolsExamples/lib/InstrumentationTools.jar`). 
The 2nd module is a utility which helps user configure the request
instrumentation library to automatically trace the target program
at desired locations. The resulting trace output would be analyzed
by the 3rd module and generates an appropriate log strategy. 
Information about the frequencies of each program
paths as well as its latencies are inferred from the trace file.
Using these information, we will be able to calculate where the
optimal log placement location should be for a specific workload 
**(note: this part is probably the most relevant for Opera)**.

## End To End Example
### 1. Instrumentation Library Manual Invocation to the High Performance Tracing Library
Inside `/examples/InstrumentationToolsExamples/src/SimpleExample1` presents a very simple
example program where the main method calls another method which is a loop which invokes 
`sleep(100ms)` 10 times in a loop. 
Request begin and end trace points are manually placed before and after the method we want to trace.
We also observe that the entrance, exits and selected basic blocks are location and time 
(obtained inside the tracing library) are recorded.
After this program is executed  
Currently the instrumentation framework is configured to dump two types of data:   

1) `pid_RequestInstrumentationContainer[containerId].stats` 
The *.stats file contains the only statistics needed for Log20. 
Below is a output of after running the example.   
`\*\*\*New Request\*\*\*`
`0,1,1051222922`
`1,10,1051181046`
For each new request, we will have a `\*\*\*New Request\*\*\*` header.
The data are organized into methodIds and one or more tuples pairs containing the observed
occurrences of basic block and aggregated latency in nanoseconds spent inside the basic block.
The method ID can be mapped to actual method names by referencing `MethodSignatureMapping.log` file. 
For example: `MethodSignatureHashList[1]:methodToInstrument` line inside the 
method signature mapping file shows methodID 1 correspond to **methodToIntrument** method. 

2) `RequestInstrumentationContainer[containerId].log`
These files contain compressed data dump of the each basic block traversal, 
basic block location, threadId and timestamps. 
The tracing utility is designed to trace multi-threaded programs thus there were 8 
tracining containers initialized by default. 
Only the 1st one contains output data because our toy sample program contained only 1 thread.
**Note: These files are used for debugging purpose to dump out additional data 
(each data point represents a portion of tracing utility memory content at specific point of time). 
These data are not required for suggest log printing statement placement strategies.**

In actual deployment of Log20, we automated the placement of the instrumentation framework invocation 
at appropriate locations in Java programs using tools such as 
Javassist (Online) or Soot (Offline) to modify the application binary.
In a already running program, we can also dynamically add or remove instrumentation framework invocation 
using tools such as spring-reloaded. 
Possible uses cases are to generate log printing statements, 
insert them into problematic program paths on certain nodes, 
then use them to debug. Once problem is found, 
remove the instrumentation code and automatically generated log messages.
The Java binary instrumentation portion of Log20 is excluded from this release because it is Java specific 
and not applicable to Opera software's intent of use on TypeScript.

[InstrumentationTools Examples README](examples/InstrumentationToolsExamples/README.md) 
 
### 2. Log Strategies Generator Examples
#### Sample Inputs
In `examples/LogStrategyGeneratorExamples/targetPrograms`, we have technical demonstration of the 
log strategy generator for the simpleTargetProgram and Hadoop HDFS.
There are two groups of input files we need for the Log Strategies Generator to work properly: 
   
1) Trace files from running the target program with the instrumentation framework enabled. 
The trace output for two sample programs are saved inside 
`examples/LogStrategyGeneratorExamples/targetPrograms/simpleTargetProgram/trace/statsFiles`
and 
`examples/LogStrategyGeneratorExamples/targetPrograms/hadoop/hdfs/trace/statsFiles`. 
For the log strategies generator examples, we chose one of the stats files, rename it to trace.txt. 
We also included `MethodSignatureMappling.log` file in their respective directories. 

2) Static analysis results of the the existing binary
The result of the static analysis is stored in `BBProperties.log` file.
These information is used by the Log Strategies Generator to map basicblock ID actual locations in the program.
 
#### Sample Outputs
In `examples/LogStrategyGeneratorExamples/targetPrograms/simpleTargetProgram/results` 
and
`examples/LogStrategyGeneratorExamples/targetPrograms/hadoop/hdfs/results`, 
we have the pre-computed outputs representing the log strategies of a given threshold 
(on average number of log message per request). 

Inside each output file, we have the following: 

Final choice of log placements:

    Final choice: [0, 41, 42, 55, 58, 59, 60, 67, 68, 77, 87, 194, 213, 214, 217, 218, 223, 378, 379], with entrophy: 6.534613575000831, total entropy: 9.875443753679221, path avg length: 1.0008510638297787, total avg length: 333032.41021276615
	
Individual log placement details:

    372-0, Weight: 1, Entropy: 9.870064675771708
	
372 is the function which appears at line 372 of Methodsignaturemapping.log.
0 is the basic block ID described in BBProperties.log.
Weight is the number of times this basic block appear in the trace.
Entropy is the system entropy value after logging this single basic block.

The numbers within the brackets are the logged basic blocks labeled by their internal IDs. 
For example, the 0 basic block is the exact `372-0` basic block. 
The entropy 6.53 under this logging placement while the total entropy without any logging is about 9.87. 


### Log Strategy Generator Source Code
The source code for the Log Strategy Generator is included in `modules/LogStrategyGenerator`. 
Details on how to compile to binaries and run the example is in 
[LogStrategyGeneratorExamples README](examples/LogStrategyGeneratorExamples/README.md) 


# Licensing - Non Commercial License FAQ
### License type
This release uses [Creative Common Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](https://creativecommons.org/licenses/by-nc/4.0/)


### When can I use the non-commercial license?
You can use our software for free under the non-commercial license when you are: 
- A student, university or a public school
- A non-profit organisation
- Developing and testing applications   

Source editing is allowed.   
Governmental entities are not covered by the non-commercial license. 

### What is commercial usage?
A commercial usage is where the purpose of using the software is generating revenue or cash flow of any type.
So if you're using the the software to sell a product, sell advertisement, 
sell a service or just marketing a commercial business, your intent is commercial. 

