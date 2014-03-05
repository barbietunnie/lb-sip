import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * <H1>Watchdog</H1>
 * Watchdog listens on watchdog port and waits until a datagram arrives.<BR>
 * <BR>
 * Once it arrives, a source ip is determined and <I>node pointer</I> points
 * to source ip of datagram.<BR>
 * <BR>
 * This only works if watchdog is enabled, eg. when watchdog port is greater
 * than 0, and no static nodes are defined.<BR>
 * <BR>
 * Currently watchdog does not take into consideration datagram's content.
 *
 * @author eigorde
 *
 */
public class Watchdog implements Runnable {

    @Override
    public void run() {

        byte[] receiveData = new byte[LoadBalancer.BUFFER_LEN];

        DatagramSocket serverSocket = null;

        /*
         * Open watchdog port for listening.
         * If it fails, then terminate this thread.
         */
        try {
        	if (LoadBalancer.watchdogInterface != null) {
        		serverSocket = new DatagramSocket(LoadBalancer.watchdogPort,
        				InetAddress.getByName(LoadBalancer.watchdogInterface));
        	}
        	else {
        		/*
        		 * If no interface is specified, then bind to all.
        		 */
        		serverSocket = new DatagramSocket(LoadBalancer.watchdogPort);
        	}
        } catch (SocketException e) {
            // Print error on console.
            e.printStackTrace();
            // Quit function.
            return; 
        } catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
            // Quit function.
            return; 
        }

        while (true)
            try {
            	
            	// Allocate space for new udp datagram.
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                // Wait for new udp datagram.
                serverSocket.receive(receivePacket);
                
                // Extract datagram content.
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
                LoadBalancer.setCurrentNode(newNode);
                
                /*
                 * Store in table node (sip server) ip address and time.
                 */
                LoadBalancer.watchdogTable.put(newNode, System.currentTimeMillis());
                
                // Increase stat. counter.
            	LoadBalancer.stat.increment(LoadBalancer.stat.WATCHDOG_NODES);
                
            } catch (IOException e) {
                // Print error on console.
                e.printStackTrace();
                // Quit while loop.
                break; 
            }
        
        serverSocket.close();
        
    }

}
