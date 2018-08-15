import java.io.*;
import java.util.ArrayList;

/**
 * Created by jack on 19/02/17.
 */
public class MethodSignatureHashGenerator {
    private static BufferedWriter bw;

    static {

        try {
            File fout = new File("MethodSignatureMapping.log");
            FileOutputStream fos = new FileOutputStream(fout);
            bw = new BufferedWriter(new OutputStreamWriter(fos));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }


    public static ArrayList<String> MethodSignatureHashList = new ArrayList<String>();


    // Poor man's solution: Array list returns negative index for recently added item. Temporary solution
    public static String[] MethodSignatureHashListTemp = new String[20000];
    public static int nextNewMethodSignatureHashListTempIndex = -1;

    public static int GenerateMethodSignatureHash(String MethodSignature) {

        for (int i = 0, length = MethodSignatureHashListTemp.length; i < length; i++){
            // found duplicate
            if (MethodSignature.equals(MethodSignatureHashListTemp[i])){
                return i;
            }
        }
        // else return the next available index (1st index in this case is 0, ++ from negative 1)
        nextNewMethodSignatureHashListTempIndex++;
        try {
            bw.write("MethodSignatureHashList["+nextNewMethodSignatureHashListTempIndex+"]:"+MethodSignature+"\n");
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return nextNewMethodSignatureHashListTempIndex;


//        if(!MethodSignatureHashList.contains(MethodSignature)) {
//            MethodSignatureHashList.add(MethodSignature);
//            try {
//                bw.write("MethodSignatureHashList["+MethodSignatureHashList.indexOf(MethodSignature)+"]:"+MethodSignature+"\n");
//                bw.flush();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
////            System.out.println("MethodSignatureHashList["+MethodSignatureHashList.indexOf(MethodSignature)+"]:"+MethodSignature);
//        }
//        return MethodSignatureHashList.indexOf(MethodSignature);
    }

//    public static Object[] getMethodSignatureHashArray() {
//        return MethodSignatureHashList.toArray();
//    }

    public static String[] getMethodSignatureHashArray() {
        return MethodSignatureHashList.toArray(new String[MethodSignatureHashList.size()]);
    }
}