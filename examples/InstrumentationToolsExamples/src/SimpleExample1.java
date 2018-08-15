import java.io.IOException;

import static java.lang.Thread.sleep;

/***
 * This example's purpose is to show the capabilities of instrumentation framework with minimal code.
 * For this case, we manually insert the instrumentation code to the approximated places in the source code.
 * When operating on Byte Code (intended log20 usage), we automatically and precisely insert instrumentation
 * code at desired locations
 *
 */

public class SimpleExample1 {
    static Integer MethodSignatureHash_main = MethodSignatureHashGenerator.GenerateMethodSignatureHash("main");
    static Integer MethodSignatureHash_main_bb0 = 0;
    static Integer MethodSignatureHash_methodToInstrument = MethodSignatureHashGenerator.GenerateMethodSignatureHash("methodToInstrument");
    static Integer MethodSignatureHash_methodToInstrument_bb0 = 0;

    public static void main(String[] args) {
        // Preparation (done once only per program's life)
        // calculate LogPointLocationData for the MethodSignatureHash_main and methodToInstrument, basic block 0 (1st basic block)
        // 31 - 13bit represents the MethodSignatureIndex
        // 12th bit represent its if log point is basic block signifying begin or end of method, 1 for true, 0 for false
        // 11th bit, given 13th bit is true, 0 represents begin of method, 1 represents end of method
        // 10 - 0th bit represents basic block index
        MethodSignatureHash_main_bb0 = MethodSignatureHash_main_bb0 | MethodSignatureHash_main;  // MethodSignatureIndex
        MethodSignatureHash_main_bb0 = ((MethodSignatureHash_main_bb0 << 1)| 0x0);     // signify not a special logPoint, 0x0 is put it here for clarity
        MethodSignatureHash_main_bb0 = ((MethodSignatureHash_main_bb0 << 12) | 0);

        MethodSignatureHash_methodToInstrument_bb0 = MethodSignatureHash_methodToInstrument_bb0 | MethodSignatureHash_methodToInstrument;  // MethodSignatureIndex
        MethodSignatureHash_methodToInstrument_bb0 = ((MethodSignatureHash_methodToInstrument_bb0 << 1)| 0x0);     // signify not a special logPoint, 0x0 is put it here for clarity
        MethodSignatureHash_methodToInstrument_bb0 = ((MethodSignatureHash_methodToInstrument_bb0 << 12) | 0);

        try {
            // The goal is to surround each of the request with
            // insertTopLevelMethodBeginTraceToBuffer and insertTopLevelMethodEndTraceToBuffer methods
            System.out.println("Started RequestInstrumentation SimpleExample1");

            System.out.println("Instrumentation of request begins");
            // Begin instrumenting a request by invoking insertTopLevelMethodBeginTraceToBuffer
            ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler.insertTopLevelMethodBeginTraceToBuffer(
                    Thread.currentThread().getId(), MethodSignatureHash_main, 1);

            // Invoke the method which you want to instrument
            methodToInstrument();
            ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler.insertBasicBlockTraceToBuffer(
                    Thread.currentThread().getId(), MethodSignatureHash_main_bb0);

            // End instrumenting a basic request by invoking insertTopLevelMethodEndTraceToBuffer
            ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler.insertTopLevelMethodEndTraceToBuffer(
                    Thread.currentThread().getId(), MethodSignatureHash_main);
            System.out.println("Instrumentation of request ends");
            System.out.println("RequestInstrumentation SimpleExample1 stopped");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // This request is composed of 10 calls to sleep(100) - "sleep 10x for 100ms each time"
    static void methodToInstrument() throws InterruptedException {
        // Begin instrumenting a method by invoking insertMethodBeginTraceToBuffer
        ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler.insertMethodBeginTraceToBuffer(
                Thread.currentThread().getId(), MethodSignatureHash_methodToInstrument,1 );

        for (int count = 0; count < 10; count++) {
//            System.out.println("Request - part " + (count+1) + "/10 - do 100ms of work");
            sleep(100);
            // Invoke insertBasicBlockTraceToBuffer at the end of each basic block
            // Note: If insertBasicBlockTraceToBuffer is used,
            //       insertMethodBeginTraceToBuffer and insertMethodBeginTraceToBuffer must also be used for the same method
            ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler.insertBasicBlockTraceToBuffer(
                    Thread.currentThread().getId(), MethodSignatureHash_methodToInstrument_bb0);
        }

        // Begin instrumenting a method by invoking insertMethodEndTraceToBuffer
        ca.utoronto.dsrg.RequestInstrumentationMultiThreadScheduler.insertMethodEndTraceToBuffer(
                Thread.currentThread().getId(), MethodSignatureHash_methodToInstrument);
    }
}
