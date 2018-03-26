import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.DatagramSocket;
import java.net.UnknownHostException;

public class Multicast implements Runnable{

    final static String INET_ADDR = "224.0.0.4";
    static int PORT;
    static InetAddress address;
    static MulticastSocket receiverSocket;

    public Multicast(int port) throws UnknownHostException{
        //Get the address that we are going to connect to.
        PORT = port;
       try{
           address = InetAddress.getByName(INET_ADDR);
       }
       catch(UnknownHostException e){
           e.printStackTrace();
       }
       catch (IOException ex) {
           ex.printStackTrace();
       }
    }


    public static void sendMessage(String msg) throws UnknownHostException, InterruptedException{
     
        // Open a new DatagramSocket, which will be used to send the data.
        try (DatagramSocket senderSocket = new DatagramSocket()) {

            // Create a packet that will contain the data
            // (in the form of bytes) and send it.
            DatagramPacket msgPacket = new DatagramPacket(msg.getBytes(),msg.getBytes().length, address, PORT);
            senderSocket.send(msgPacket);
     
            System.out.println("Sent msg: " + msg);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void run(){
        
        // Create a buffer of bytes, which will be used to store
        // the incoming bytes containing the information from the server.
        // Since the message is small here, 256 bytes should be enough.
        byte[] buf = new byte[256];
        
        // Create a new Multicast socket (that will allow other sockets/programs
        // to join it as well.
        try{
            //Joint the Multicast group.

            receiverSocket = new MulticastSocket(PORT);
            receiverSocket.joinGroup(address);

            while (true) {
                // Receive the information and print it.
                DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
                receiverSocket.receive(msgPacket);

                String msg = new String(buf, 0, buf.length);
                System.out.println("Received msg: " + msg);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


}
    