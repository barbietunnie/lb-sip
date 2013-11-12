import java.io.IOException;

/**
 * <H1>Synchronization</H1>
 * This class will enable synchronization between peers running Load Balancer.<BR>
 * Each peer that receives a new call on sip interface, should broadcast call record
 * via multicast to the rest of group. Members of group who receive such message should
 * store call record in their call tables.
 * @author eigorde
 *
 */
public class Synchronization implements Runnable {

    @Override
    public void run() {
        McastSync mcastSync = new McastSync();
        String callID;
        CallType callType;
        while (true)
            try {
            	/*
            	 * Wait for multicast datagram.
            	 */
                mcastSync.receive();
                
                /*
                 * Extract callID string.
                 */
                callID = mcastSync.getCallID();
                
                /*
                 * Check if callID is "ALL".
                 */
                if (callID.equalsIgnoreCase("ALL")) {

                	/*
                	 * Increase stat. counter.
                	 */
                	LoadBalancer.stat.syncAll++;
                	
                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "CallID is ALL. Request for all call records is ordered.");
                    }
                    
                    /*
                     * Send all call records to peers.
                     */
                    for (String key : LoadBalancer.callTable.keySet()) {
                        callID = key;
                        callType = LoadBalancer.callTable.get(key);
                        if (LoadBalancer.verbose == 3) {                        
                            LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " broadcasted.");
                        }
                        mcastSync.send(callID, callType);
                    }
                } else {
                	/*
                	 * Get CallType object.
                	 */
                    callType = mcastSync.getCallType();
                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " received.");
                    }
                    /*
                     * Add or remove call record from table,
                     * depending on bye flag, if it's set or not.
                     */
                    if (callType.bye) {
                    	// Remove call record from table.
                        LoadBalancer.callTable.remove(callID);
                    	
                        // Increase stat. counter.
                    	LoadBalancer.stat.syncBye++;
                    }
                    else {
                    	// Add call record to table.
                        LoadBalancer.callTable.put(callID, callType);
                        
                        // Increase stat. counter.
                    	LoadBalancer.stat.syncInvite++;
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
