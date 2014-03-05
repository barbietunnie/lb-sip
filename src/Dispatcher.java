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

                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                
                /*
                 * Check for garbage and discard.
                 */
                if (receivePacket.getLength() < 10) {
                	continue;
                }
                
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

                    // Immediately sync. with peers.
                    mcastSync.sendImmediately(callID, callType);
                    
                    if (LoadBalancer.verbose == 3) {                    
                        LoadBalancer.log(Thread.currentThread().getName(), "Call stored in table [" + callType.srcAddress.getHostAddress() + ":"
                            + callType.srcPort + " --> " + callType.dstAddress.getHostAddress() + ":" + callType.dstPort
                            + "].");
                    }
                    
                    // Finally, forward datagram to node.
                    LoadBalancer.anyDatagramSocket.send(sendPacket);
                    
                    // Increase stat. counter.
                	LoadBalancer.stat.increment(LoadBalancer.stat.SIP_INVITE);
                	
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
                				 * This will also query dead nodes. Maybe some of them came up again.
                				 */
                				collector.sendSipOptions(node, sipPort);
                				queryNodeList.add(node);
                			}
                			else if (delta > LoadBalancer.deadInterval &&
                					delta < LoadBalancer.deadInterval + LoadBalancer.helloInterval) {
                    			/*
                    			 * Dead node. Just report.
                    			 */
                                if (LoadBalancer.verbose > 1) {                
                                    LoadBalancer.log(Thread.currentThread().getName(), "Dead node: " + node);
                                }  
                			}
                		}
                	}
                	
                }
                else if (method.contains("REGISTER")) {
                	/*
                	 * SIP REGISTER message should update phone
                	 * table and and store source ip address. 
                	 */
                	
                	
                	// IP addr. of received packet (from).
                	String ipAddress = receivePacket.getAddress().getHostAddress();
                    
                	// Log for debugging
            		if (LoadBalancer.verbose == 3) {
            			/*
            			 * Output whole message.
            			 */
            			LoadBalancer.log(Thread.currentThread().getName(), "REGISTAR request received from " + ipAddress + "\n" + message);
            		}
            		else if (LoadBalancer.verbose == 2) {
            			/*
            			 * Just inform.
            			 */
            			LoadBalancer.log(Thread.currentThread().getName(), "REGISTAR request received from: " + ipAddress);
            		}
            		
                	// Nonce and md5result might be present in REGISTER message
                	String nonce = "";
                	String md5result = "";
                	
                	// Complete Via, To, From, CSeq, Content-Length, Expires lines of REGISTER message
                	int idxSpace = method.indexOf(' ');
                	String requestUri = method.substring(idxSpace + 1,
                            method.indexOf(' ', idxSpace + 1));
                    String via = message.substring(message.indexOf("Via:"),
                            message.indexOf('\n', message.indexOf("Via:")) - 1);
                    String to = message.substring(message.indexOf("To:"),
                            message.indexOf('\n', message.indexOf("To:")) - 1);
                    String from = message.substring(message.indexOf("From:"),
                            message.indexOf('\n', message.indexOf("From:")) - 1);
                    String cseq = message.substring(message.indexOf("CSeq:"),
                            message.indexOf('\n', message.indexOf("CSeq:")) - 1);
                    String contentLen = message.substring(message.indexOf("Content-Length:"),
                            message.indexOf('\n', message.indexOf("Content-Length:")) - 1);
                    String expires = message.substring(message.indexOf("Expires:"),
                            message.indexOf('\n', message.indexOf("Expires:")) - 1);
                                        
                    int idxContact = message.indexOf("Contact:");
                    String contact = "";
                    if (idxContact > 0) {
                    	contact = message.substring(idxContact,
                    			message.indexOf('\n', idxContact) - 1);
                    }
                    else {
                    	contact = "Contact: <" + requestUri + ">";
                    }
                    
                    /*
                     * Extract nonce or generate new one.
                     */
                    if (message.indexOf("nonce") > 0) {
                        nonce = message.substring(message.indexOf("nonce") + 7,
                                message.indexOf('\"', message.indexOf("nonce") + 7));                    	
                    }
                    else {
                    	nonce = String.format("%x", System.currentTimeMillis());
                    }
                    
                    /*
                     * Try to extract md5 response if possible.
                     */
                    if (message.indexOf("Authorization:") > 0) {
                    	int idxAuthorization = message.indexOf("Authorization:");
                        String authorization = message.substring(idxAuthorization,
                                message.indexOf('\n', idxAuthorization));
                        
                        int idxResponse = authorization.indexOf("response=");
                        md5result = authorization.substring(
                        		idxResponse + 10,
                        		authorization.indexOf('\"', idxResponse + 10));
                    }
                    
                    /*
                     * Extract user part in From: line:
                     * From: <sip:1001@192.168.110.1;transport=UDP>;tag=485af632
                     * user = 1001
                     */
                    String user = from.substring(from.indexOf('<') + 1, from.indexOf('>') - 1);
                    user = user.substring(0, user.indexOf('@'));
                    if (user.indexOf(':') > 0) {
                    	user = user.substring(user.indexOf(':') + 1);
                    }

                    /*
                     * Build OK message and replay.
                     */
                    String[] okMessage = {
                    		"SIP/2.0 200 OK",
                    		via.replaceAll("rport", "received=" + ipAddress),
                    		from,
                    		to,            				
                    		"Call-ID: " + callID,
                    		cseq,
                    		"Server: " + LoadBalancer.ver,
                    		"Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REFER, SUBSCRIBE, NOTIFY, INFO, PUBLISH",
                    		"Supported: replaces, timer",                    		
                    		contact,
                    		contentLen,
                    		expires				
                    };

                    /*
                     * Build Unauthorized message and replay
                     */
                    String[] unauthorizedMessage = {
                    		"SIP/2.0 401 Unauthorized",
                    		via + ";received=" + ipAddress + ";rport=" + receivePacket.getPort(),
                    		from,
                    		to,            				
                    		"Call-ID: " + callID,
                    		cseq,
                    		"Server: " + LoadBalancer.ver,
                    		"Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REFER, SUBSCRIBE, NOTIFY, INFO, PUBLISH",
                    		"Supported: replaces, timer",
                    		"WWW-Authenticate: Digest algorithm=MD5, realm=\"" + LoadBalancer.realm + "\", nonce=\"" + nonce + "\"",
                    		contentLen,				
                    };

                	
            		StringBuilder sb = new StringBuilder();
            		
            		if (expires.endsWith(" 0")) {
            			/*
            			 * Unregister user or phone number when
            			 *  Expires: 0 
            			 * line is present in REGISTER message.
            			 */
            			LoadBalancer.registrator.unregister(user);
        				/*
        				 *  Build OK message.
        				 */
        				for (String line : okMessage) {
        					sb.append(line);
        					sb.append("\r\n");
        				} 
            		}
            		else {
            			/*
            			 * Try to perform register in db.
            			 */
            			if (LoadBalancer.registrator.register(user, ipAddress, LoadBalancer.realm, nonce, requestUri, md5result)) {
            				/*
            				 *  Build OK message.
            				 */
            				for (String line : okMessage) {
            					sb.append(line);
            					sb.append("\r\n");
            				}                		
            			}
            			else {
            				/*
            				 *  Build Unauthorized message.
            				 */
            				for (String line : unauthorizedMessage) {
            					sb.append(line);
            					sb.append("\r\n");
            				}                		
            			}
            		}
            		
            		sb.append("\r\n");
            		
            		String replayMessage = sb.toString();
            				
                    // Log for debugging
            		if (LoadBalancer.verbose == 3) {
            			/*
            			 * Output whole message.
            			 */
            			LoadBalancer.log(Thread.currentThread().getName(),
            					"REGISTAR replay sent back to: " + ipAddress + "\n" + replayMessage);
            		}
            		else if (LoadBalancer.verbose == 2) {
            			/*
            			 * Just inform.
            			 */
            			LoadBalancer.log(Thread.currentThread().getName(), "REGISTAR replay sent back to: " + ipAddress);
            		}
                    
                    // Build replay.
                    DatagramPacket sendPacket = new DatagramPacket(replayMessage.getBytes(), replayMessage.getBytes().length,
                    		new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort()));
                    LoadBalancer.anyDatagramSocket.send(sendPacket);
                    
                }
                else {
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
                    		LoadBalancer.stat.increment(LoadBalancer.stat.SIP_NOT_FOUND);
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
                            
                            mcastSync.sendImmediately(callID, callPointer);
                            
                            // Increase stat. counter.
                        	LoadBalancer.stat.increment(LoadBalancer.stat.SIP_BYE);
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
