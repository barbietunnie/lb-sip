import java.io.IOException;
import java.util.Map;

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

        while (true)
            try {
            	/*
            	 * Wait for multicast datagram.
            	 */
            	mcastSync.retrieve();
                
                /*
                 * Check what kind of update / request we received.
                 */
            	if (mcastSync.getRequestALL()) {
                	/*
                	 * Increase stat. counter.
                	 */
                	LoadBalancer.stat.increment(LoadBalancer.stat.SYNC_ALL);
                	
                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "Request for synchronization is ordered.");
                    }
                    
                    /*
                     * Send all call records to peers.
                     */
                    for (String key : LoadBalancer.getCallRecords()) {
                        String callID = key;
                        CallType callType = LoadBalancer.getCallRecord(key);
                        if (LoadBalancer.verbose == 3) {                        
                            LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " broadcasted.");
                        }
                        mcastSync.store(callID, callType);
                    }         
                    mcastSync.flush();
            	}
            	else {
                	/*
                	 * Get new calls (updates).
                	 */
                    Map<String, CallType> updates = mcastSync.getReceivedCalls();

                    for (String callID : updates.keySet()) {
                    	CallType callType = updates.get(callID);
                    
                    	if (LoadBalancer.verbose == 3) {                    
                    		LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " received.");
                    	}
                    	
						/*
						 * Add or remove call record from table, depending on
						 * bye flag, if it's set or not.
						 */
						if (callType.bye) {
							// Remove call record from table.
							LoadBalancer.removeCallRecord(callID);

							// Increase stat. counter.
							LoadBalancer.stat.increment(LoadBalancer.stat.SYNC_BYE);
						} else {
							// Add call record to table.
							LoadBalancer.putCallRecord(callID, callType);

							// Increase stat. counter.
							LoadBalancer.stat.increment(LoadBalancer.stat.SYNC_INVITE);
						}
                    }
                    
                    /*
                     * Clean buffer and make it ready to receive new calls.
                     */
                    mcastSync.cleanReceivedCalls();
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
