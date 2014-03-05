import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * <H1>Collector</H1><BR>
 * Collector should collect all SIP OK messages as a result of SIP OPTIONS,
 * and store their ip addresses into list. This list is then readable,
 * and others can fetch discovered SIP servers.<BR>
 * <BR>
 * <H1>Example</H1><BR>
 * This short example should demonstrate how to collect ip addresses
 * from host that have sent SIP OK message.
 * <PRE>
 * int bindPort = 5060 // SIP default port (udp)
 * Collector collector = new Collector("192.168.1.1", bindPort);
 * Thread collect = new Thread(collector);
 * collect.start();
 * // ... do some work here ... 
 * // Eg. send SIP OPTIONS or something 
 * // else which results in SIP OK
 * 
 * collector.stopDiscovery(); // Stop discovery thread
 * 
 * for (String host : collector.getDiscoveredHosts()) {
 *  System.out.println("Host: " + host);
 * }
 * </PRE>
 * This class won't take care how long discovery thread runs, in general
 * it is running until <I>stopDiscovery()</I> method occurs.
 * <BR> 
 * @author eigorde
 *
 */
public class Collector implements Runnable {

	private final int BUFFER_LEN = 4096;

	private SocketAddress socket;
	private DatagramSocket datagramSocket;

	private List<String> discoveredHosts;

	private boolean socketOpen;
	
	/**
	 * <H1>Collector</H1><BR>
	 * Make instance of <I>Collector</I> with a desired listen port.
	 * <BR> 
	 * @param hostAddress ip interface on host for discovery 
	 * @param bindPort listening port for SIP OK messages in udp socket
	 */
	public Collector(String hostAddress, int bindPort) {

		socket = new InetSocketAddress(hostAddress, bindPort);
		datagramSocket = null;

		discoveredHosts = new ArrayList<String>();
		
		socketOpen = false;
	}
	
	/**
	 * <H1>Collector</H1><BR>
	 * Make instance of <I>Collector</I> with a desired listen port.
	 * <BR> 
	 * @param datagramSocket datagram socket for sending udp datagrams
	 */
	public Collector(DatagramSocket datagramSocket) {

		this.socket = null;
		this.datagramSocket = datagramSocket;

		discoveredHosts = new ArrayList<String>();
		
		socketOpen = false;
	}
	
	@Override
	public void run() {

		if (datagramSocket == null) {
			try {
				datagramSocket = new DatagramSocket(socket);
			} catch (SocketException e) {
				e.printStackTrace();
				return;
			}		
		}

		byte[] receiveData = new byte[BUFFER_LEN];
		DatagramPacket receivePacket = new DatagramPacket(receiveData,
				receiveData.length);
		
		setSocketOpen(true);
		
		while (isSocketOpen())
			try {
				datagramSocket.receive(receivePacket);

				String message = new String(receivePacket.getData());
				String method = message.substring(0, message.indexOf('\n') - 1);
				// String callID = message.substring(message.indexOf("Call-ID:") + "Call-ID:".length() + 1, message.indexOf('\n', message.indexOf("Call-ID:")) - 1);

				/*
				 *  SIP OK message
				 */
				if (method.contains("200 OK")) {
					String newHost = receivePacket.getAddress().getHostAddress();
					if (!discoveredHosts.contains(newHost)) {
						// Store ip address of host which has respond with OK
						discoveredHosts.add(newHost);
					}
				}

			} catch (IOException e) {
				if (isSocketOpen()) {
					e.printStackTrace();
					setSocketOpen(false);
				}
			}

		datagramSocket.close();
	}

	/**
	 * Check if socket is open. This happens when Collector
	 * starts to collect SIP replies.
	 * @return <I>true</I> socket is open, otherwise <I>false</I>
	 */
	public synchronized boolean isSocketOpen() {
		return socketOpen;
	}

	/**
	 * Set socket state.
	 * @param state <I>true</I> socket is open, otherwise <I>false</I>
	 */
	public synchronized void setSocketOpen(boolean state) {
		socketOpen = state;
	}
	
	/**
	 * Send message to socket. This will convert string into bytes,
	 * and send it to remote ip address and port via udp socket.
	 * @param message message to send, like SIP OPTIONS 
	 * @param address remote ip address
	 * @param port remote port, usually <I>5060</I>
	 * @throws IOException
	 */
	private void send(String message, InetAddress address, int port) throws IOException {
		
		byte[] data = message.getBytes();
		
    	/*
    	 *  Build a new udp datagram.
    	 */
		DatagramPacket packet = new DatagramPacket(data, data.length,
				address, port);
		
		
		if (LoadBalancer.verbose == 3) {
			LoadBalancer.log(Thread.currentThread().getName(), "OPTIONS message sent to: " + address.getHostAddress() + ":" + port);
		}
		
		datagramSocket.send(packet);
	}
	
	/**
	 * Return random number between <I>min</I> and <I>max</I> value.
	 * @param min minimum value
	 * @param max maximum value
	 * @return between value
	 */
	private long randomNumber(long min, long max) {
		long retVal = min + (long)(Math.random() * ((max - min) + 1));
		return retVal;
	}

	/**
	 * Send SIP OPTIONS message.
	 * @param address remote address
	 * @param port port number, usually SIP servers listen on udp port 5060
	 * @throws IOException
	 */
	public void sendSipOptions(String address, int port) throws IOException {
		sendSipOptions(InetAddress.getByName(address), port);
	}
	
	/**
	 * Send SIP OPTIONS message.
	 * @param address remote address
	 * @param port port number, usually SIP servers listen on udp port 5060
	 * @throws IOException
	 */
	public void sendSipOptions(InetAddress address, int port) throws IOException {
		
		HashMap <String, String> mapVariable = new HashMap<String, String>();
		
		/*
		 *  Variables in SIP OPTIONS message		
		 */
		String service = Thread.currentThread().getName();
		String remote_ip = address.getHostAddress();
		String transport = "UDP";
		String local_ip = datagramSocket.getLocalAddress().getHostAddress();
		String local_port = String.valueOf(datagramSocket.getLocalPort());
		String call_id = local_ip + "-" + local_port + "-" + String.valueOf(System.currentTimeMillis() + randomNumber(100000, 999999));
		
		String[] optionsMessage = {
				"OPTIONS sip:[service]@[remote_ip] SIP/2.0",
				"Via: SIP/2.0/[transport] [local_ip]:[local_port]",
				"Max-Forwards: 70",
				"To: <sip:[service]@[remote_ip]>",
				"From: sipp <[service]@[local_ip]:[local_port]>",
				"Call-ID: [call_id]",
				"CSeq: 1 OPTIONS",
				"Contact: <sip:[service]@[local_ip]:[local_port]>",
				"Accept: application/sdp",
				"Content-Length: 0",
				""
		};
		
		mapVariable.put("[service]", service);
		mapVariable.put("[remote_ip]", remote_ip);
		mapVariable.put("[transport]", transport);
		mapVariable.put("[local_ip]", local_ip);
		mapVariable.put("[local_port]", local_port);
		mapVariable.put("[call_id]", call_id);
		
		StringBuilder sb = new StringBuilder();
		
		/*
		 *  Build message
		 */
		for (String line : optionsMessage) {
			sb.append(line);
			sb.append("\r\n");
		}
		
		/*
		 *  Do replacement of variable fields in SIP OPTIONS message
		 */
		for (String key : mapVariable.keySet()) {
			int start = sb.indexOf(key);
			while (start >= 0) {
				int end = start + key.length();
				String str = mapVariable.get(key);
				sb.replace(start, end, str);
				start = sb.indexOf(key);
			}
		}
		
		/*
		 *  Send to socket
		 */
		send(sb.toString(), address, port);

	}
	
	/**
	 * Get list of discovered hosts.
	 * @return sip hosts that have sent SIP OK message
	 */
	public List<String> getDiscoveredHosts() {
		if (isSocketOpen()) {
			stopDiscovery();
		}
		return discoveredHosts;
	}

	/**
	 * Stop discovery thread. This is optional,
	 * since <I>getDiscoveredHosts()</I> will check and
	 * stop discovery thread if necessary to avoid concurrency.
	 */
	public void stopDiscovery() {
		setSocketOpen(false);
		datagramSocket.close();
	}
}
