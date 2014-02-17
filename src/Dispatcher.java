import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * <H1>Dispatcher</H1>
 * Dispatcher listens on sip port and waits until a call comes.<BR>
 * <BR>
 * A SIP INVITE message will cause a new record in call table,
 * and will be routed to next node in list.<BR>
 * <BR>
 * All other SIP messages are routed to node according to call table.<BR>
 * For entries that do not exist in call table, an error message is printed to
 * console. Such calls are lost.<BR>
 * <BR>
 * Each change in call table is also broadcasted to peers so they should update 
 * their call tables. 
 * @author eigorde
 *
 */
public class Dispatcher implements Runnable {

	public Dispatcher () throws SocketException {
		/*
		 * Bind now.
		 */
		if (LoadBalancer.anyDatagramSocket == null) {
			LoadBalancer.anyDatagramSocket = new DatagramSocket(LoadBalancer.bindPort);
		}		
	}
	
    @Override
    public void run() {

        byte[] receiveData = new byte[LoadBalancer.BUFFER_LEN];

        McastSync mcastSync = new McastSync();

        Collector collector = new Collector(LoadBalancer.anyDatagramSocket);

        List<String> queryNodeList = new ArrayList<String>();
        
        long lastCheckNodeTracker = System.currentTimeMillis();

        /*
         * Should be done better, eg. to use any port number for sip,
         * since nodes might listen on any port for sip message.
         */
        final int sipPort = 5060;

        while (true) 
        	 try {
            
        		 // Allocate space for new udp datagram.
        		 DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                           
                // Only at verbosity level 3 print this message.
                if (LoadBalancer.verbose == 3) {
                    LoadBalancer.log(Thread.currentThread().getName(), "waiting for incoming packet.");
                }
                
                // Wait for new udp datagram.
                LoadBalancer.anyDatagramSocket.receive(receivePacket);

                String message = new String(receivePacket.getData());
                String method = message.substring(0, message.indexOf('\n') - 1);
                String callID = message.substring(message.indexOf("Call-ID:") + "Call-ID:".length() + 1,
                        message.indexOf('\n', message.indexOf("Call-ID:")) - 1);
                
                if (LoadBalancer.verbose == 3) {
                    LoadBalancer.log(Thread.currentThread().getName(), "method = " + method + ".");
                    LoadBalancer.log(Thread.currentThread().getName(), "callID = " + callID + ".");
                }
                
                /*
                 * Check SIP message type.
                 */
                if (method.contains("INVITE")) {
                    /*
                     * SIP INVITE should be distributed across nodes in
                     * node list, or to node which has been last reported to watchdog.
                     */
                	String currentNode = LoadBalancer.getCurrentNode();
                	
                	// Build a new udp datagram.
                    DatagramPacket sendPacket = new DatagramPacket(receiveData, message.length(), new InetSocketAddress(
                            InetAddress.getByName(currentNode), sipPort));

                    // Create new call type object which will be stored in call table and send to peers for sync.
                    CallType callType = new CallType(receivePacket.getAddress(), receivePacket.getPort(),
                    		InetAddress.getByName(currentNode), sipPort);
                    
                    // Store new call in table.
                    LoadBalancer.putCallRecord(callID, callType);

                    // Sync. with peers.
                    mcastSync.send(callID, callType);

                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "Call stored in table [" + callType.srcAddress.getHostAddress() + ":"
                            + callType.srcPort + " --> " + callType.dstAddress.getHostAddress() + ":" + callType.dstPort
                            + "].");
                    }
                    
                    // Finally, forward datagram to node.
                    LoadBalancer.anyDatagramSocket.send(sendPacket);
                    
                    // Increase stat. counter.
                	LoadBalancer.stat.sipInvite++;
                	
                    /*
                     * Adjust nodePointer to next el. in list in case
                     * that watchdog is disabled. This has to be done
                     * here.
                     */
                	LoadBalancer.updateCurrentNode();

                	/*
                	 * Check if nodes in tracker list should be updated.
                	 * But only if SIP OPTIONS tracking is enabled.
                	 */
                	long delta = System.currentTimeMillis() - lastCheckNodeTracker;
                	if (delta > LoadBalancer.helloInterval && LoadBalancer.sipOptions) {
                		queryNodeList.clear();
                		lastCheckNodeTracker = System.currentTimeMillis();
                		
                		for (Integer index : LoadBalancer.getNodeTrackerKeySet()) {
                			delta = lastCheckNodeTracker - LoadBalancer.getNodeTracker(index);
                			String node = LoadBalancer.getNode(index);
                			if (delta > LoadBalancer.helloInterval) {
                				/*
                				 * Query each node that didn't send any packet with in helloInterval time.
                				 */
                				collector.sendSipOptions(node, sipPort);
                				queryNodeList.add(node);
                			}
                			else if (delta > LoadBalancer.deadInterval &&
                					delta < LoadBalancer.deadInterval + LoadBalancer.helloInterval) {
                    			/*
                    			 * Dead node.
                    			 */
                                if (LoadBalancer.verbose > 1) {                
                                    LoadBalancer.log(Thread.currentThread().getName(), "Dead node: " + node);
                                }  
                			}
                		}
                	}
                	
                } else {
                    /*
                     * Locate call in call table.
                     */
                    CallType callPointer = LoadBalancer.getCallRecord(callID);

                    if (callPointer == null) {
                    	
                    	if (method.contains("200 OK") &&
                    			queryNodeList.contains(receivePacket.getAddress().getHostAddress())) {
                    		/*
                    		 * This is SIP OK reply to SIP OPTIONS query.
                    		 * Update node tracker for that ip address.
                    		 */
                    		LoadBalancer.updateNodeTracker(receivePacket.getAddress());
                    		
                    	}
                    	else {

                    		// This is error condition !
                    		if (LoadBalancer.verbose > 0) {                        
                    			LoadBalancer.log(Thread.currentThread().getName(), "callID " + callID + " not found in call table.");
                    		}

                    		// Increase stat. counter.
                    		LoadBalancer.stat.sipNotFound++;
                    	}
                    } else {
                        
                        if (LoadBalancer.verbose == 3) {                        
                            LoadBalancer.log(Thread.currentThread().getName(), "Call found in table [" + callPointer.srcAddress.getHostAddress() + ":"
                                + callPointer.srcPort + " --> " + callPointer.dstAddress.getHostAddress() + ":"
                                + callPointer.dstPort + "].");
                        }
                        
                        /*
                         * Check from which direction SIP message came.
                         */
                        if (receivePacket.getAddress().equals(callPointer.dstAddress)) {
                        	/*
                        	 * SIP server ---> Load balancer ---> outside network --> remote SIP peer
                        	 */
                            DatagramPacket sendPacket = new DatagramPacket(receiveData, message.length(), new InetSocketAddress(
                                    callPointer.srcAddress, callPointer.srcPort));
                            LoadBalancer.anyDatagramSocket.send(sendPacket);
                            
                            /*
                             * Update tracker.
                             */
                            LoadBalancer.updateNodeTracker(callPointer.dstAddress);
                            
                        } else {
                        	/*
                        	 * Remote SIP peer ---> outside network --> Load balancer ---> SIP server  
                        	 */                        	
                            DatagramPacket sendPacket = new DatagramPacket(receiveData, message.length(), new InetSocketAddress(
                                    callPointer.dstAddress, callPointer.dstPort));
                            LoadBalancer.anyDatagramSocket.send(sendPacket);
                        }
                    	
                        /*
                         * Remove call from table if bye flag is set.
                         */
                        if (callPointer.bye) {
                            LoadBalancer.removeCallRecord(callID);
                            
                            if (LoadBalancer.verbose == 3) {                            
                                LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " removed.");
                            }
                            
                            mcastSync.send(callID, callPointer);
                            
                            // Increase stat. counter.
                        	LoadBalancer.stat.sipBye++;
                        }

                        /*
                         * Set bye flag upon SIP BYE message arrival.
                         */
                        if (method.contains("BYE")) {
                            callPointer.bye = true;
                        }

                    }
                }

            } catch (IOException e) {
                // Print error on console.
                e.printStackTrace();
                // Quit while loop.
                break;
            }

        // Close udp socket.
        mcastSync.close();
    
    }
}
