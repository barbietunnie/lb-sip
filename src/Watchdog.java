import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class Watchdog implements Runnable {

    @Override
    public void run() {

        byte[] receiveData = new byte[LoadBalancer.BUFFER_LEN];

        DatagramSocket serverSocket = null;

        try {
            serverSocket = new DatagramSocket(LoadBalancer.watchdogPort);
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        while (LoadBalancer.alive) {
            try {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                serverSocket.receive(receivePacket);
                String sentence = new String(receivePacket.getData());

                /*
                 * Process message.
                 */
                InetAddress ipAddress = receivePacket.getAddress();
                String newNode = ipAddress.getHostAddress();

                // Print status of sip servers, if verbosity is increased.
                if (LoadBalancer.verbose == 2) {                
                    LoadBalancer.log(Thread.currentThread().getName(), "Node " + newNode + " is live.");
                }
                else if (LoadBalancer.verbose == 3) {                
                    LoadBalancer.log(Thread.currentThread().getName(), "Node " + newNode + " sent: " + sentence);
                }
                
                /*
                 * Set a node pointer to point to new node.
                 */
                LoadBalancer.nodePointer = newNode;
                
                /*
                 * Store in table node (sip server) ip address and time.
                 */
                LoadBalancer.watchdogTable.put(newNode, System.currentTimeMillis());
                
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        serverSocket.close();
        
    }

}
