package se.kth.csc.progsys.oacp;

import se.kth.csc.progsys.oacp.protocol.EndMessage;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author janmachacek
 */
class ActorSystemMessages {

    private int count = 0;
    private boolean end = false;
    private boolean start = false;

    private static final String FILENAME = "/Users/star/GitSource/OACP/output.log";

    void recordMessage(Object msg) {
        //System.out.println("enter recordMessage()" + msg.getClass().getName());
        //System.out.println("start:" + start + ", end:" + end);
        if (msg.toString().startsWith("StartMessage")) {
            System.out.println("Starts counting");
            start = true;
            end = false;
        }

        if(start && ! end){
            if (msg.getClass().getName().startsWith("akka")) {
                //System.out.println("skip internal message");
            }
            else if (msg.toString().startsWith("EndMessage")) {
                end = true;
                //System.out.println("Number of message is :"+ count);
                BufferedWriter bw = null;
                FileWriter fw = null;
                try {

                    String content = "Number of message is :"+ count + "\n";

                    fw = new FileWriter(FILENAME, true);
                    bw = new BufferedWriter(fw);
                    bw.write(content);

                    System.out.println("Done");

                } catch (IOException e) {

                    e.printStackTrace();
                } finally {

                    try {

                        if (bw != null)
                            bw.close();

                        if (fw != null)
                            fw.close();

                    } catch (IOException ex) {

                        ex.printStackTrace();

                    }

                }
            }
            else if (! (msg.toString().startsWith("AppendEntriesRPC")|| msg.toString().startsWith("AddSuccess")) || msg.toString().startsWith("GetAddSuccess")){
                ++count;
                //System.out.println("++count" + count);
                //System.out.println("msg is: " + msg.toString());
//                BufferedWriter bw = null;
//                FileWriter fw = null;
//                try {
//
//                    String content = "Message is :"+ msg.toString() + "\n";
//
//                    fw = new FileWriter(FILENAME, true);
//                    bw = new BufferedWriter(fw);
//                    bw.write(content);
//
//                    System.out.println("Done");
//
//                } catch (IOException e) {
//
//                    e.printStackTrace();
//                } finally {
//
//                    try {
//
//                        if (bw != null)
//                            bw.close();
//
//                        if (fw != null)
//                            fw.close();
//
//                    } catch (IOException ex) {
//
//                        ex.printStackTrace();
//
//                    }
//
//                }
            }
        }

    }

    int read() {
        return count;
    }
}
