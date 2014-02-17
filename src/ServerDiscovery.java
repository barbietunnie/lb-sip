import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * <H1>Server Discovery</H1><BR>
 * SIP server discovery is based on sending SIP OPTIONS message to
 * each ip address in local subnets (private ranges in A, B or C class).<BR>
 * <BR>
 * It is assumed that this works for most SIP implementations, and sip
 * server should reply with SIP OK message, which will be collected 
 * by <I>Collector</I>.<BR>
 * <BR>
 * 
 * @author eigorde
 *
 */
public class ServerDiscovery implements Runnable {

	/**
	 * Collector which will wait for SIP OK replies.
	 */
	private Collector collector;

	/**
	 * List of IP interfaces, both ipv4 and ipv6. 
	 */
    private List<InterfaceAddress> ipInterfaces;

	/**
	 * Desired IP interface, to perform discovery. 
	 */    
    private String selectedInterface;
    
    /**
     * UDP Port for binding SIP listener, <I>Collector</I>. 
     */
    private int bindPort;
    
    /**
     * Datagram socket, if binding is already done.<BR>
     * Please use either <I>datagramSocket</I> or <I>bindPort</I>.
     */
    private DatagramSocket datagramSocket;
    
    /**
     * Timeout after which discovery process is done.
     */
    private long timeout;
    
    /**
     * Thread Collector.
     */
    private Thread tCollector;
    
    /**
     * Fetch list of discovered servers.
     * @return list of sip servers that have replied with SIP OK message
     */
	public List<String> getDiscoveredHosts() {
		return collector.getDiscoveredHosts();
	}
	
	/**
	 * <H1>Server Discovery</H1><BR>
	 * Set ip interface for discovery, datagram socket, and start <I>Collector</I>.<BR>
	 * <BR>
	 * Timeout value will determine time after which process of collecting SIP OK 
	 * replies is terminated and discovery effectively ends at that point in time.
	 * This also causes main thread to end. Reasonable value is between 2 sec. and 10 sec.<BR>
	 * <BR>
	 * @param hostIpInterface ip interface where to perform discovery
	 * @param datagramSocket a datagram socket which already listens
	 * @param timeout timeout value in milliseconds
	 */
	public ServerDiscovery(String hostIpInterface, DatagramSocket datagramSocket, long timeout) {
	
		this.selectedInterface = hostIpInterface; 
		this.bindPort = 0;
		this.datagramSocket = datagramSocket; 
		this.timeout = timeout;
		
		initIpInterfaceList();

	}
	
	/**
	 * <H1>Server Discovery</H1><BR>
	 * Set ip interface for discovery, binding port, and start <I>Collector</I>.<BR>
	 * <BR>
	 * Timeout value will determine time after which process of collecting SIP OK 
	 * replies is terminated and discovery effectively ends at that point in time.
	 * This also causes main thread to end. Reasonable value is between 2 sec. and 10 sec.<BR>
	 * <BR>
	 * Binding port for SIP protocol over udp is <I>5060</I>.<BR>
	 * @param hostIpInterface ip interface where to perform discovery
	 * @param bindPort choose a udp port for binding, usually <I>5060</I>
	 * @param timeout timeout value in milliseconds
	 */
	public ServerDiscovery(String hostIpInterface, int bindPort, long timeout) {
	
		this.selectedInterface = hostIpInterface; 
		this.bindPort = bindPort;
		this.datagramSocket = null;
		this.timeout = timeout;
		
		initIpInterfaceList();

	}
	
	/**
	 * Fill list of all ip interfaces: loopback, ipv4 and ipv6 ip addresses.<BR>
	 * <BR>
	 * This includes also link local ipv6 address. In general, all what is available
	 * on this host.
	 */
	private void initIpInterfaceList () {
		
		if (bindPort > 0) {		
			collector = new Collector(this.selectedInterface, this.bindPort);
		} 
		else {
			collector = new Collector(datagramSocket);
		}
		
		tCollector = new Thread(collector, "CollectorThread");		
		
        Enumeration<NetworkInterface> theIntfList;
        List<InterfaceAddress> theAddrList = null;
        NetworkInterface theIntf = null;        

        ipInterfaces = new ArrayList<InterfaceAddress>();
        
        try {
        	/*
        	 *  Enumerate network interfaces.
        	 */
            theIntfList = NetworkInterface.getNetworkInterfaces();
            while (theIntfList.hasMoreElements()) {
                theIntf = theIntfList.nextElement();  
                /*
                 *  Get ip interface(s).
                 */
                theAddrList = theIntf.getInterfaceAddresses();
                for (InterfaceAddress intAddr : theAddrList) {
            		/*
            		 *  Add ip interface to list.
            		 */
            		ipInterfaces.add(intAddr);                	
                }
            }
        } catch (SocketException e1) {
            e1.printStackTrace();
        }		
	}
	
	@Override
	public void run() {
		
		/*
		 *  Check that there is ip interface selected.
		 */
		if (selectedInterface == null) {
			return;
		}
		
		if (selectedInterface.length() == 0) {
			return;
		}
        
        /*
         *  Start collector process.
         */
        tCollector.start();
        
        /*
         *  Wait a little bit, until Collector opens a socket.
         */
        while (!collector.isSocketOpen()) {
        	Thread.yield();
        }
        
        boolean foundSelectedInterface = false;
		
        InetAddress ipAddress = null;
        short ipMask = 0;
        
        /*
         *  Examine all ip interfaces that were found.
         */
        for (InterfaceAddress intAddr : ipInterfaces) {
        	ipAddress = intAddr.getAddress();
        	ipMask = intAddr.getNetworkPrefixLength();
        	
        	// Get byte representation of each ip interface.
			byte[] byteAddr = ipAddress.getAddress();
        	
			if (selectedInterface.equalsIgnoreCase(ipAddress.getHostAddress())) {
				/*
				 * This is selected interface.
				 */
				foundSelectedInterface = true;
        	}
        	else {
        		/*
        		 * Skip ip interface.
        		 */
        		continue;
        	}
        	
			/*
			 * Ipv4 is 32-bit, thus has 4 bytes.
			 */
			if (byteAddr.length == 4) {

				/*
				 *  Calculate ipv4 address, subnet mask, network address and broadcast.
				 */
				int ipv4_Address = (byteAddr[3] & 0xff)
						+ ((byteAddr[2] & 0xff) << 8)
						+ ((byteAddr[1] & 0xff) << 16)
						+ ((byteAddr[0] & 0xff) << 24);
				int ipv4_Submask = 0xffffffff << (32 - ipMask);
				int ipv4_Network = ipv4_Address & ipv4_Submask;
				int ipv4_Broadcast = ipv4_Network | (~ipv4_Submask);

				/*
				 * Send SIP OPTIONS to all neighbors
				 */
				for (int addr = ipv4_Network + 1; addr < ipv4_Broadcast; addr++) {
					/*
					 * Finally, check that we don't send to our own ip address
					 */
					if (addr == ipv4_Address) {
						/*
						 * Our own ip address, skip it
						 */
					} else {
						/*
						 * Send SIP OPTIONS message
						 */
						try {
							InetAddress inetAddress = InetAddress.getByAddress(intToByteArray(addr));

							/*
							 * Here is assumed that udp port 5060 will be
							 * probably used by SIP servers which are about to
							 * be discovered.
							 */
							collector.sendSipOptions(inetAddress, bindPort);

							if (LoadBalancer.verbose == 3) {
								LoadBalancer.log(Thread.currentThread().getName(), "Query sent to: "
										+ inetAddress.getHostAddress());
							}

						} catch (UnknownHostException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

				}
				
			} else if (byteAddr.length == 16) {
				/*
				 *  ipv6 is not yet supported. :(
				 */
			}

        }
        
        /*
         * Warn user if selected ip interface is not found.
         */
        if (!foundSelectedInterface) {
			if (LoadBalancer.verbose > 0) {
				LoadBalancer.log(Thread.currentThread().getName(), "IP interface: "
						+ selectedInterface + " not found on this system.");
			}        	
        }
        
        /*
         *  Sleep and wait for collector to collect replies.
         */
        try {
			Thread.sleep(timeout);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        
        /*
         *  Stop collector thread. This also effectively
         *  terminates discovery since nobody will receive late
         *  SIP OK replies.
         */
        collector.stopDiscovery();
	}
    	
	/**
	 * Convert 32bit integer into 4 byte array.
	 * @param value integer
	 * @return byte array
	 */
	private final byte[] intToByteArray(int value) {
	    return new byte[] {
	            (byte)(value >>> 24),
	            (byte)(value >>> 16),
	            (byte)(value >>> 8),
	            (byte)value};
	}
	
}
