import java.io.IOException;

public class Synchronization implements Runnable {

    @Override
    public void run() {
        McastSync mcastSync = new McastSync();
        String callID;
        CallType callType;
        while (LoadBalancer.alive)
            try {
                mcastSync.receive();
                callID = mcastSync.getCallID();
                if (callID.equalsIgnoreCase("ALL")) {

                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "CallID is ALL. Request for all call records is ordered.");
                    }
                    
                    for (String key : LoadBalancer.callTable.keySet()) {
                        callID = key;
                        callType = LoadBalancer.callTable.get(key);
                        if (LoadBalancer.verbose == 3) {                        
                            LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " broadcasted.");
                        }
                        mcastSync.send(callID, callType);
                    }
                } else {
                    callType = mcastSync.getCallType();
                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " received.");
                    }
                    if (callType.bye) {
                        LoadBalancer.callTable.remove(callID);
                    }
                    else {
                        LoadBalancer.callTable.put(callID, callType);
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                LoadBalancer.alive = false;
            }

        mcastSync.close();
    }

}
