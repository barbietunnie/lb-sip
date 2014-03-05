import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LoadBalancer {

    /**
     * Version string.
     */
    final static String ver = "SIP Load Balancer v0.23";

    /**
     * Print messages to console.
     */
    final static PrintStream pr = System.out;

    /**
     * Max. buffer length to receive messages.
     */
    public final static int BUFFER_LEN = 4096;

    /**
     * UDP port number to bind. By default, it will bind on all interfaces.
     */
    static int bindPort = 5060;
    
    /**
     * TCP port for telnet management. By default, 
     * telnet service is disabled it port is set to 0.
     */
    static int telnetPort = 4444;

    /**
     * Ip interface to which telnet service should bind.
     */
    static String telnetInterface = null;
    
    /**
     * Watchdog udp port number to receive keep alive messages from nodes.<BR>
     * If it is 0, then watchdog should be disabled.<BR>
     */
    static int watchdogPort = 0;
    
    /**
     * Ip interface to which watchdog service should bind.
     */    
    static String watchdogInterface = null;

    /**
     * Any socket.
     */
    static DatagramSocket anyDatagramSocket;

    /**
     * List of nodes defined statically at startup as command argument(s).<BR>
     * This will disable watchdog feature.<BR>
     */
    private static Hashtable<Integer, String> nodeList;

    /**
     * An ip address index for <I>nodeList</I> table which points to next SIP server where to forward SIP
     * INVITE.<BR>
     * 
     */
    private static int nodePointer;

    /**
     * Node tracker should keep information about last packet received from SIP
     * servers (nodes). This should be always updated when a SIP server sends SIP message to
     * Load Balancer.<BR>
     * Another process can periodically check for availability and potentially 
     * check with OPTIONS message if node is alive or not. 
     * 
     */
    private static ConcurrentHashMap<Integer, Long> nodeTracker;
    
    /**
     * <H1>Call table</H1><BR>
     * This hashmap will store for each new call a <I>call record</I> which contains source --> destination link.<BR>
     * This means that source ip address and port of SIP INVITE will be stored as source pair of information,
     * while destination ip address and port are chosen among available SIP nodes.<BR>
     * <BR>
     * There are two ways how to call table gets populated: directly by SIP INVITE and by peer synchronization
     * when peer receives SIP INVITE and sends update.<BR>
     * <BR>
     * <B>NOTE</B><BR>
     * Please do not directly access this object instance, since it will cause problems and lost of records.<BR>
     * This type of hashmap is not thread safe, and requires methods with synchronized keywords.<BR>
     * <BR>
     * An example of synchronized methods are <I>putCallRecord(...)</I> and <I>getCallRecord(...)</I>.  
     * 
     */
    private static ConcurrentHashMap<String, CallType> callTable = new ConcurrentHashMap<String, CallType>();

    /**
     * Watchdog table for storing info about nodes which have reported their status to watchdog process.
     */
    static Hashtable<String, Long> watchdogTable = new Hashtable<String, Long>();

    /**
     * Do we need to perform discovery local ip interface. If interface
     * is <I>null</I>, then do not start discovery procedure.
     * Ip interface is ip address of local interface.<BR>
     * Eg. <I>192.168.1.1</I> 
     */
    static String discoveryInterface = null;

    /**
     * How long discovery process will wait for nodes to answer.<BR>
     * Default: 4 sec.
     */
    static long discoveryTimeout = 4;

    /**
     * SIP OPTIONS query and verification of SIP nodes.<BR>
     * This flag is used by Dispatcher process to periodically
     * query nodes in list and track their availability.<BR>
     * By default, this is turned on. 
     */
    static boolean sipOptions = true;
    
    /**
     * Hello interval, eg. how often nodes are queried.<BR>
     * Unit: msec.
     */
    static long helloInterval = 3000;
    
    /**
     * Dead interval, eg. after missing several hellos, consider node dead.<BR>
     * Unit: msec.
     */
    static long deadInterval = 10000;
    
    /**
     * Local Register for user authorization.
     */
    static Registrator registrator = new Registrator();
    
    /**
     * Realm name, eg. mydomain.com
     */
    static String realm = "myDomain";
    
    /**
     * Flag to keep dispatcher and other threads running forever.<BR>
     */
    //static boolean alive = true;

    /**
     * Statistic data.
     */
    static Stat stat = new Stat();
    
    /**
     * Verbose level. Used by Dispatcher, Synchronizer and Watchdog
     * processes. According to verbosity level, a log function will
     * write info to console/file or network stream.<BR> 
     * Value:<BR>
     * 0 completely turn off logging<BR>
     * 1 log only errors, default<BR>
     * 2 log errors and warnings<BR>
     * 3 log all<BR> 
     */
    static int verbose = 1;
    
    /**
     * Prints short help for program usage.
     */
    private static void usage() {
        pr.println("Usage:\n "
                + " java -jar lb.jar [node1] [node2] ... [nodeX] [--bindPort XX] [--watchdogPort XX] [--telnetPort XX] ... [--verbose X]\n"
                + " where:\n\n"
                + "  --bindPort XX\n"
                + "  will bind to udp port XX and wait for SIP messages. Default is 5060.\n\n"
                + "  --telnetPort XX\n"
                + "  --telnetInterface A.B.C.D\n"
                + "  will start telnet management interface on tcp port XX and ip interface A.B.C.D.\n"
                + "  Default port is 4444 if not specified. To enable telnet interface, specify at least one ip interface or 0.0.0.0.\n\n"
                + "  --watchdogPort XX\n"
                + "  --watchdogInterface A.B.C.D\n"
                + "  will start watchdog listener on ip interface A.B.C.D and udp port XX. By default, it is disabled.\n"
                + "  To enable watchdog, at least specify udp port number.\n\n"
                + "  --discoveryInterface A.B.C.D\n"
                + "  --discoveryTimeout XX\n"
                + "  will do auto discovery of SIP servers. A.B.C.D is local ip interface and XX is timeout in sec.\n"
                + "  Default timeout is 4 sec. Do not use discovery with node list together.\n\n"
                + "  --sipOptions [true | false]\n"
                + "  SIP OPTIONS will enable periodic checking of nodes in list by sending SIP OPTIONS message.\n"
                + "  Nodes that reply, are marked as alive, and those that do not replay, are marked dead.\n"
                + "  This is turned on by default. Please turn it off if you use watchdog service, otherwise results might be unpredictable.\n\n"
                + "  --realmName [domain]\n"
                + "  Use custom realm name when processing REGISTER requests.\n\n"
                + "  --verbose X\n"
                + "  determine the level of logging. If 0, logging is turned off, if 1 only errors are shown (default), 3 is max. verbosity.\n\n"
                + "  Node list: [node1] [node2] ... [nodeX] are ip addresses of nodes manually defined in case that watchdog service is not in use.\n"
                + "  If you specify static nodes, then watchdog is disabled, an no new nodes will be considered for load balancing.\n");        
    }
    
    /**
     * Main, program entry point.
     * @param args see <I>usage()</I> on how to use program arguments and switches
     * @throws IOException
     * @throws InterruptedException 
     */
    public static void main(String args[]) throws IOException, InterruptedException {

        /*
         * Print version and startup time.
         */
        pr.println(ver);
        pr.println("Started at: " + new Date());        

        /*
         * Initialize list of nodes. This list will disable watchdog, and let
         * user add a list of SIP servers manually.
         */
        nodeList = new Hashtable<Integer, String>();
        nodeTracker = new ConcurrentHashMap<Integer, Long>();
        nodePointer = -1;
        
        stat.clear();
        
        registrator.dbRead("user.db");
        
        if (args.length == 0) {
            usage();
            return;
        } else {
            GetOpts op = new GetOpts(args);
            
            /*
             * If user request help, print usage and quit.
             */
            if (op.isSwitch("-h") || op.isSwitch("--help")) {
                usage();
                return;
            }
            
            /*
             * Read command line switches.
             */
            for (String switchName : op.getSwitches()) {
                if (op.isSwitch(switchName)) {
                    if (switchName.equalsIgnoreCase("--bindPort"))
                        bindPort = Integer.parseInt(op.getSwitch(switchName));
                    else if (switchName.equalsIgnoreCase("--watchdogInterface"))
                        watchdogInterface = op.getSwitch(switchName);
                    else if (switchName.equalsIgnoreCase("--watchdogPort"))
                        watchdogPort = Integer.parseInt(op.getSwitch(switchName));
                    else if (switchName.equalsIgnoreCase("--telnetInterface"))
                        telnetInterface = op.getSwitch(switchName);
                    else if (switchName.equalsIgnoreCase("--telnetPort"))
                        telnetPort = Integer.parseInt(op.getSwitch(switchName));
                    else if (switchName.equalsIgnoreCase("--discoveryInterface"))
                        discoveryInterface = op.getSwitch(switchName);
                    else if (switchName.equalsIgnoreCase("--discoveryTimeout"))
                        discoveryTimeout = Integer.parseInt(op.getSwitch(switchName));
                    else if (switchName.equalsIgnoreCase("--sipOptions"))
                        sipOptions = op.getSwitch(switchName).equalsIgnoreCase("true");
                    else if (switchName.equalsIgnoreCase("--realmName"))
                        realm = op.getSwitch(switchName);
                    else if (switchName.equalsIgnoreCase("--verbose"))
                        verbose = Integer.parseInt(op.getSwitch(switchName));
                    else
                        log(Thread.currentThread().getName(), "Unkown switch option: " + switchName);
                }
            }
            
            /*
             * Add list of static nodes.
             */
            int counter = 0;
            long currentTime = System.currentTimeMillis();
            for (String nodeAddress : op.getArguments()) {
                nodeList.put(counter, nodeAddress);
                nodeTracker.put(counter, currentTime);
                counter++;
            }
            nodePointer = 0;

            /*
             * Set realm value if not specified.
             */
            if (!op.isSwitch("--realmName")) {
            	realm = System.getenv("HOSTNAME");
            	if (realm == null) {
            		// Try again.
            		realm = System.getenv("COMPUTERNAME");
            	}
            	if (realm == null) {
            		// Set bogus name.
            		realm = "myDomain";
            	}
            }
        }

        /*
         * Do not bind now. Dispatcher process will
         * check for null value and perform binding.
         */
        anyDatagramSocket = null;
        
        /*
         * Print list of manually added nodes. 
         */
        if (nodeList.size() > 0) {
            String nodes = "Node list: ";
            for (Integer key : nodeList.keySet()) {
                nodes = nodes + nodeList.get(key) + " ";
            }
            log(Thread.currentThread().getName(), nodes);
        } else
            log(Thread.currentThread().getName(), "Node list is empty.");

        /*
         * Start discovery module.
         */
        if (discoveryInterface != null) {
        	
    		long startTime = System.currentTimeMillis();
    		
    		long timeout = discoveryTimeout * 1000;
    		
    		ServerDiscovery serverDiscovery = new ServerDiscovery(discoveryInterface, bindPort, timeout);
    		Thread discovery = new Thread(serverDiscovery, "discoveryService");
    		
    		log(Thread.currentThread().getName(), "Starting discovery process.");
    		
    		/*
    		 *  Start discovery
    		 */
    		discovery.start();
    		/*
    		 *  Wait to complete
    		 */
    		discovery.join();
    		
    		/*
    		 *  Get result
    		 */
    		String serverList = "";
    		int counter = 0;
    		for (String item : serverDiscovery.getDiscoveredHosts()) {
                /*
                 * Add found items to list.
                 */
                nodeList.put(counter++, item);                    			
    			serverList = serverList + item + " ";
    		}
    		
    		/*
    		 * Check that we have some servers in list. 
    		 */
    		if (nodeList.size() > 0) {
    			nodePointer = 0;
    		}
    		else {
    			log(Thread.currentThread().getName(), "Nothing found. Please start SIP servers and restart.");
    			return;
    		}
    		
    		long endTime = System.currentTimeMillis();
    		
    		log(Thread.currentThread().getName(), "Found: " + serverList);
    		log(Thread.currentThread().getName(), "Elapsed: " + String.valueOf(endTime - startTime) + " msec.");
    		        	
        }
        else {
        	log(Thread.currentThread().getName(), "Discovery disabled.");
        }
        
        /*
         * Start dispatcher.
         */
        Thread dispacherThread = new Thread(new Dispatcher(), "dispacherThread");
        log(Thread.currentThread().getName(), "Starting dispatcher process.");
        dispacherThread.start();

        /*
         * Print useful information on which port load balancer is listening.
         */
        log(Thread.currentThread().getName(), "Listening on: " +
        		anyDatagramSocket.getLocalAddress().getHostAddress() + ":" +
        		anyDatagramSocket.getLocalPort());
        
        /*
         * Start watchdog, only if watchdog is enabled.
         * When node list is not empty, then watchdog is disabled.
         */
        Thread watchdogThread = new Thread(new Watchdog(), "watchdogThread");
        if (watchdogPort > 0 && nodeList.isEmpty()) {
            log(Thread.currentThread().getName(), "Starting watchdog process.");
            watchdogThread.start();
        }
        else {
            log(Thread.currentThread().getName(), "Watchdog is disabled.");
        }
        
        /*
         * Start synchronization process.
         */        
        Thread syncThread = new Thread(new Synchronization(), "syncThread");
        log(Thread.currentThread().getName(), "Starting McastSync process.");
        syncThread.start();
        
        /*
         * Configure and start telnet service.
         */
        TelnetServer ts = new TelnetServer(telnetInterface, telnetPort);
        ts.configTelnetServer();
        Thread telnetThread = new Thread(ts, "telnetThread");
        if (telnetInterface != null) {        
        	log(Thread.currentThread().getName(), "Starting telnet interface on " + telnetInterface + ":" + telnetPort + ".");
        	telnetThread.start();
        }
        else {
        	log(Thread.currentThread().getName(), "Telnet interface disabled.");
        }
        
        /*
         * Synchronize with peers who are already running.
         */
        McastSync mcastSync = new McastSync();
        log(Thread.currentThread().getName(), "Broadcasting synchronization request to all peers.");
        //mcastSync.requestSync();
        mcastSync.sendSync();
        mcastSync.close();

    }
    
    /**
     * Update node pointer, so that we have available next node for new call.
     */
    public static synchronized void updateCurrentNode() {
    	/*
    	 * Node list must not be empty and watchdog disabled.
    	 */
        if (!nodeList.isEmpty() && watchdogPort == 0) {

        	/*
        	 * Save current pointer value.
        	 */
        	int currentPointer = nodePointer;

        	/*
        	 * Save current time stamp.
        	 */
        	long currentTime = System.currentTimeMillis();
        	
        	long delta = 0;
        	
        	/*
        	 * Find next alive node.
        	 * This loop will skip all nodes that did not report
        	 * in time of deadInterval and will find next available node.
        	 */
        	do {
				/*
				 * Switch to next one, or first one if we are at last item
				 * pointing.
				 */
				if (nodePointer == LoadBalancer.nodeList.size() - 1) {
					nodePointer = 0;
				} else {
					nodePointer++;
				}

				/*
				 * Calculate delta, with tolerance of deadInterval.
				 * Quit loop if node pointer loops over whole list of nodes.
				 */
				delta = currentTime - getNodeTracker(nodePointer);
				
        	} while (delta > deadInterval && currentPointer != nodePointer);
        	
			if (verbose == 3) {
				log(Thread.currentThread().getName(),
						"Next node is " + getCurrentNode() + ".");
			}

        }    	
    }
    
    /**
     * Get ip address of selected node.
     * @param index id of node
     * @return ip address of node in list
     */
    public static synchronized String getNode(int index) {
    	return nodeList.get(index);
    }
    
    /**
     * Add new ip address to node list and tracker list.
     * @param address ip address of new node
     */
    public static synchronized void addNode(String address) {
    	
    	int max = 0;
    	
    	/*
    	 * Find next free id value.
    	 */
    	for (int id : nodeList.keySet()) {
    		if (max < id ) {
    			max = id;
    		}
    	}
    	
    	int newID = max + 1;
    	
    	/*
    	 * Check that ip address does not exist.
    	 */
    	if (getNodeIndex(address) == -1) {
			nodeList.put(newID, address);
			nodeTracker.put(newID, System.currentTimeMillis());
    	}
    	else {
    		/*
    		 * Just refresh node tracker table.
    		 */
    		nodeTracker.put(getNodeIndex(address), System.currentTimeMillis());
    	}
    }
    
    /**
     * Remove existing ip address from node list and tracker list.
     * @param address ip address of existing node
     */
    public static synchronized void deleteNode(String address) {
    	
    	int id = getNodeIndex(address);
    	
    	if (id > -1) {
			nodeList.remove(id);
			nodeTracker.remove(id);
    	}
    }
    
    /**
     * Get ip address of current node.
     * @return ip address of current node
     */
    public static synchronized String getCurrentNode() {
    	return nodeList.get(nodePointer);
    }
    
    /**
     * Set node pointer.<BR>
     * <B>NOTE:</B>This function does not check boundaries of <I>nodeList</I> list.
     * @param nodePointer index value
     */
    public static synchronized void setCurrentNode(int nodePointer) {	
    	LoadBalancer.nodePointer = nodePointer;
    }
    
    /**
     * Set node pointer.<BR>
     * <B>NOTE:</B>This function works only if ip address is present in <I>nodeList</I> list.
     * @param nodeAddress ip address of node in the list
     */    
    public static synchronized void setCurrentNode(String nodeAddress) {
    	nodePointer = getNodeIndex(nodeAddress); 
    }
    
    /**
     * Get node id from ip address.<BR>
     * <BR>
     * <B>NOTE</B><BR>
     * This function works only if ip address is present in <I>nodeList</I> list.
     * @param nodeAddress ip address of node in the list
     * @return index of node in list, or <I>-1</I> if node address not found in list
     */    
    public static synchronized int getNodeIndex(String nodeAddress) {
    	for (Integer index : nodeList.keySet()) {
            if (nodeList.get(index).equalsIgnoreCase(nodeAddress)) {
            	return index;
            }
    	}
    	return -1;
    }
    
    /**
     * Allow access to set of keys (index) values for node list.
     * @return set of index values
     */
    public static synchronized Set<Integer> getNodeListKeySet() {
    	return nodeList.keySet();
    }
    
    /**
     * Update time stamp of node in tracker list.
     * @param index node id value, same as in <I>nodeList</I>
     */
    public static synchronized void updateNodeTracker(int index) {
    	if (nodeTracker != null) {
    		nodeTracker.put(index, System.currentTimeMillis());
    	}
    }
    
    /**
     * Update time stamp of node in tracker list.
     * @param address ip address of node in the list to update time stamp
     */
    public static synchronized void updateNodeTracker(InetAddress address) {
    	if (nodeTracker != null) {
    		String addr = address.getHostAddress();
            for (Integer index : nodeList.keySet()) {
                if (nodeList.get(index).equalsIgnoreCase(addr)) {
                	nodeTracker.put(index, System.currentTimeMillis());
                	return;
                }
            }
    		
    	}
    }
    
    /**
     * Get time stamp of node.
     * @param index id value of node
     * @return time stamp (UNIX time), or <I>0</I> if node is not in tracker table
     */
    public static synchronized long getNodeTracker(int index) {
    	long retVal = 0;
    	
    	if (nodeTracker != null) {
    		if (nodeTracker.get(index) != null) {
    			retVal = nodeTracker.get(index);
    		}
    	}
    	
    	return retVal;

    }
    
    /**
     * Allow access to set of keys (index) values for node tracker list.
     * @return set of index values
     */
    public static synchronized Set<Integer> getNodeTrackerKeySet() {
    	return nodeTracker.keySet();
    }
    
    /**
     * Get time stamp for a node in list. This is last time that is has been seen.
     * @param index id of node
     * @return time stamp (UNIX time)
     */
    public static synchronized long getNodeTrackerValue(int index) {
    	return nodeTracker.get(index);
    }  
    
    /**
     * Put new time stamp value on node. 
     * @param index id of node
     * @param timestamp new time value
     */
    public static synchronized void setNodeTrackerValue(int index, long timestamp) {
    	nodeTracker.put(index, timestamp);
    }
    
    /**
     * Store record in call table <I>callTable</I>. This is synchronized and 
     * should be thread safe and protected against concurrent modifications.<BR>
     * <B>NOTE:</B><BR>
     * Make sure you use this call instead of direct access to <I>callTable</I> object.
     * @param CallID unique identifier of each call record. This string is extracted from SIP INVITE message
     * @param callType call record which stores <I>source ip:port</I> and <I>destination ip:port</I>, so that SIP routing is possible
     */
    public static synchronized void putCallRecord(String CallID, CallType callType) {
    	callTable.put(CallID, callType);
    }
    
    /**
     * Retrieve record from call table <I>callTable</I>. This is synchronized and 
     * should be thread safe and protected against concurrent modifications.<BR>
     * <B>NOTE:</B><BR>
     * Make sure you use this call instead of direct access to <I>callTable</I> object.
     * @param CallID unique identifier of each call record. This string is extracted from SIP INVITE message
     * @return <I>callType</I> object which stores <I>source ip:port</I> and <I>destination ip:port</I>, so that SIP routing is possible
     */    
    public static synchronized CallType getCallRecord(String CallID) {
    	return callTable.get(CallID);
    }
    
    /**
     * Delete record from call table <I>callTable</I>. This is synchronized and 
     * should be thread safe and protected against concurrent modifications.<BR>
     * <B>NOTE:</B><BR>
     * Make sure you use this call instead of direct access to <I>callTable</I> object.
     * @param CallID unique identifier of each call record. This string is extracted from SIP INVITE message
     */
    public static synchronized void removeCallRecord(String CallID) {
    	callTable.remove(CallID);
    }
    
    /**
     * Get collection of <I>CallIDs</I> from table <I>callTable</I>. This is synchronized and 
     * should be thread safe and protected against concurrent modifications.<BR>
     * <B>NOTE:</B><BR>
     * Make sure you use this call instead of direct access to <I>callTable</I> object.
     * @return a collection of CallIDs which you can use in iterations to access call records
     */    
    public static synchronized Set<String> getCallRecords() {
    	return callTable.keySet();
    }
  
    /**
     * Save last thread name for <I>log()</I> function call.
     */
    static String lastThreadLog = "";
       
    /**
     * Save application start time.
     */
    static long startedAt = System.currentTimeMillis();
    
    /**
     * Produce log message based on thread name. If same thread
     * calls this function, a new message will append without 
     * thread name.
     * @param thread name of the thread
     * @param message text message
     */
    public static void log(String thread, String message) {
        DecimalFormat dF = new DecimalFormat("0.00");
        if (lastThreadLog.equalsIgnoreCase(thread)) {            
            String line = dF.format((System.currentTimeMillis() - startedAt) / 1000) +
                    " sec.  " +
                    message;
            if (verbose > 0) {            
                pr.println(line);
            }
        }
        else {
            lastThreadLog = thread;

            String separator = "-----------------------";
            String line = dF.format((System.currentTimeMillis() - startedAt) / 1000) +
                    " sec.  " +
                    message;
            if (verbose > 0) {
                pr.println();
                pr.println(thread);
                pr.println(separator);
                pr.println(line);
            }
        }
    }
}
