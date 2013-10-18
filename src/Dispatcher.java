import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;


public class Dispatcher implements Runnable {

    @Override
    public void run() {

        byte[] receiveData = new byte[LoadBalancer.BUFFER_LEN];

        McastSync mcastSync = new McastSync();

        /*
         * Should be done better, eg. to use any port number for sip,
         * since nodes might listen on any port for sip message.
         */
        final int sipPort = 5060;

        while (LoadBalancer.alive) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                
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
                     * node list.
                     */
                    DatagramPacket sendPacket = new DatagramPacket(receiveData, message.length(), new InetSocketAddress(
                            InetAddress.getByName(LoadBalancer.nodePointer), sipPort));

                    LoadBalancer.callTable.put(callID,
                            new CallType(receivePacket.getAddress(), receivePacket.getPort(), InetAddress.getByName(LoadBalancer.nodePointer),
                                    sipPort));

                    CallType callPointer = LoadBalancer.callTable.get(callID);
                    mcastSync.send(callID, callPointer);

                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "Call stored in table [" + callPointer.srcAddress.getHostAddress() + ":"
                            + callPointer.srcPort + " --> " + callPointer.dstAddress.getHostAddress() + ":" + callPointer.dstPort
                            + "].");
                    }
                    
                    LoadBalancer.anyDatagramSocket.send(sendPacket);

                    /*
                     * Adjust nodePointer to next el. in list in case
                     * that watchdog is disabled. This has to be done
                     * here.
                     */
                    if (LoadBalancer.watchdogPort == 0 && !LoadBalancer.nodeList.isEmpty()) {

                        for (Integer index = 0; index < LoadBalancer.nodeList.size(); index++) {
                            if (LoadBalancer.nodeList.get(index).equalsIgnoreCase(LoadBalancer.nodePointer)) {
                                /*
                                 * Current node pointer found in list,
                                 * switch to next one, or first one if
                                 * we are at last item pointing.
                                 */
                                if (index == LoadBalancer.nodeList.size() - 1) {
                                    LoadBalancer.nodePointer = LoadBalancer.nodeList.get(0);
                                } else {
                                    LoadBalancer.nodePointer = LoadBalancer.nodeList.get(index + 1);
                                }

                                if (LoadBalancer.verbose == 3) {                                
                                    LoadBalancer.log(Thread.currentThread().getName(), "Next node is " + LoadBalancer.nodePointer + ".");
                                }
                                
                                break;
                            }
                        }

                    }

                } else {
                    /*
                     * Locate call in call table.
                     */
                    CallType callPointer = LoadBalancer.callTable.get(callID);

                    if (callPointer == null) {
                        // This is error condition !
                        if (LoadBalancer.verbose > 0) {                        
                            LoadBalancer.log(Thread.currentThread().getName(), "callID " + callID + " not found in call table.");
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
                            DatagramPacket sendPacket = new DatagramPacket(receiveData, message.length(), new InetSocketAddress(
                                    callPointer.srcAddress, callPointer.srcPort));
                            LoadBalancer.anyDatagramSocket.send(sendPacket);
                        } else {
                            DatagramPacket sendPacket = new DatagramPacket(receiveData, message.length(), new InetSocketAddress(
                                    callPointer.dstAddress, callPointer.dstPort));
                            LoadBalancer.anyDatagramSocket.send(sendPacket);
                        }

                        /*
                         * Remove call from table if bye flag is set.
                         */
                        if (callPointer.bye) {
                            LoadBalancer.callTable.remove(callID);
                            
                            if (LoadBalancer.verbose == 3) {                            
                                LoadBalancer.log(Thread.currentThread().getName(), "CallID " + callID + " removed.");
                            }
                            
                            mcastSync.send(callID, callPointer);
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
                // TODO Auto-generated catch block
                e.printStackTrace();
                LoadBalancer.alive = false;
            }
        }
        // serverSocket.close();
        mcastSync.close();
    }

}