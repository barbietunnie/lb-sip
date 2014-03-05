import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * <H1>Multicast synchronizer</H1>
 * <BR>
 * This class is for keeping in sync two Load Balacer's.<BR>
 * It is used by {@link Synchronization} and {@link Dispatcher} process.
 * @author eigorde
 * 
 */
public class McastSync {

    private static final int BUFFER_LENGTH = 4096;

    /**
     * Udp datagram socket.
     */
    private DatagramSocket socket;

    /**
     * Multicast socket.
     */
    private MulticastSocket mSocket;

    /**
     * Default multicast address to join and receive datagrams.
     */
    private String mcastAddr = "226.13.25.1";
    /**
     * Default udp port.
     */
    private int mcastPort = 5555;

    /**
     * @deprecated This is used in <B>send()</B> and <B>receive()</B> functions.
     */
    private String callID = "";
    /**
     * @deprecated This is used in <B>send()</B> and <B>receive()</B> functions.
     */
    private String callType = null;
    
    /**
     * Call buffer to store call records before they are flushed to multicast socket.
     */
    private Hashtable<String, CallType> callBuffer;

    /**
     * Call buffer of received call records before they are read.
     */
    private Hashtable<String, CallType> receivedBuffer;
    
    /**
     * Flag which is <I>true</I> when ALL request is received.
     */
    private boolean requestALL;
    
    /**
     * List of own ip interfaces. This is used to 
     * recognize and avoid
     * locally generated multicast packets.  
     */
    private List<InetAddress> ipInterfaces;

    /**
     * <H1>McastSync</H1><BR>
     * By default, <B>McastSync</B> uses default <BR>
     * multicast address <I>226.13.25.1</I> and port <I>5555</I>.<BR>
     * 
     * @throws IOException
     */
    public McastSync() {
        init();
    }

    /**
     * <H1>McastSync</H1><BR>
     * By default, <B>McastSync</B> uses default <BR>
     * multicast address <I>226.13.25.1</I> and port <I>5555</I>.<BR>
     * <BR>
     * You can override default settings by specifying desired address and port.<BR>
     * 
     * @throws IOException
     */
    public McastSync(String mcastAddr, int mcastPort) {

        this.mcastAddr = mcastAddr;
        this.mcastPort = mcastPort;

        init();

    }

    /**
     * <H1>Init</H1>
     * <BR>
     * Initialize part which is same for all constructors:
     * <UL>
     * <LI>add ip interfaces to list</LI>
     * <LI>open a udp socket for sending datagrams</LI>
     * <LI>open a multicast socket to receive datagrams</LI>
     * </UL>
     * 
     */
    private void init() {

        Enumeration<NetworkInterface> theIntfList;
        List<InterfaceAddress> theAddrList = null;
        NetworkInterface theIntf = null;
        InetAddress theAddr = null;

        ipInterfaces = new ArrayList<InetAddress>();

        try {
            theIntfList = NetworkInterface.getNetworkInterfaces();
            while (theIntfList.hasMoreElements()) {
                theIntf = theIntfList.nextElement();
                theAddrList = theIntf.getInterfaceAddresses();
                for (InterfaceAddress intAddr : theAddrList) {
                    theAddr = intAddr.getAddress();
                    ipInterfaces.add(theAddr);
                }
            }
        } catch (SocketException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        try {
            socket = new DatagramSocket();
            mSocket = new MulticastSocket(mcastPort);
            mSocket.joinGroup(InetAddress.getByName(mcastAddr));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        callBuffer = new Hashtable<String, CallType>();
        receivedBuffer = new Hashtable<String, CallType>();
        
        requestALL = false;
    }

    /**
     * Send <I>{callID, CallType}</I> record to multicast group.<BR>
     * <BR>
     * Packet format:<BR>
     * <TABLE border=2>
     * <TR>
     * <TH>Len1</TH>
     * <TH>CallID</TH>
     * <TH>Len2</TH>
     * <TH>CallType</TH>
     * </TR>
     * </TABLE>
     * <BR>
     * Where:<BR>
     * <I>Len1</I> is length of <I>CallID</I> string, 1 byte<BR>
     * <I>Len2</I> is length of <I>CallType</I> string, 1 byte<BR>
     * <I>CallType</I> is custom string in format:<BR>
     * <I>source ip</I>:<I>source port</I>,<I>destination ip</I>:<I>destination
     * port</I>,<I>bye flag</I><BR>
     * <BR>
     * <B>Example:</B><BR>
     * <I>88.20.34.6</I>:<I>5060</I>,<I>192.168.0.1</I>:<I>5060</I>,<I>false</I><BR>
     * <BR>
     * @deprecated Use <B>store()</B> function instead, and then <B>flush()</B>.
     * 
     * @param callID
     *            SIP CallID string
     * @param callType
     *            CallType string
     * @throws IOException
     */
    public void send(String callID, CallType callType) throws IOException {

        String call = callType.toString();

        int len = 1 + callID.length() + 1 + call.length();

        byte[] data = new byte[len];
        data[0] = (byte) callID.length();
        int i = 1;
        for (Byte b : callID.getBytes()) {
            data[i] = b;
            i++;
        }
        data[i] = (byte) call.length();
        i++;
        for (Byte b : call.getBytes()) {
            data[i] = b;
            i++;
        }

        DatagramPacket dgram;

        dgram = new DatagramPacket(data, data.length, InetAddress.getByName(mcastAddr), mcastPort);

        socket.send(dgram);

    }
    
    /**
     * Write <I>{callID, CallType}</I> record to data buffer.<BR>
     * <BR>
     * Later use <I>flush()</I> to send records to multicast group.
     * @param callID
     *            SIP CallID string
     * @param callType
     *            CallType string
     */    
    public void store(String callID, CallType callType) {
    	callBuffer.put(callID, callType);
    }
    
    /**
     * Immediately sends update to peers without storing call record in buffer.<BR>
     * <BR>
     * Data format:<BR>
     * <TABLE border=2><TR><TD>count (n)</TD><TD>callID-1 length</TD><TD>callID-1</TD>
     * <TD>callType-1 length</TD><TD>callType-1</TD>
     * <TD>...</TD><TD>callID-n length</TD><TD>callID-n</TD><TD>callType-n length</TD>
     * <TD>callType-n</TD> </TR></TABLE><BR>
     * <BR>
     * This is faster way if you need to send only 1 call record to update peers.
     * 
     * @throws IOException
     */    
    public void sendImmediately(String callID, CallType callType) throws IOException {

		/*
		 * Datagram buffer.
		 */
		byte[] buffer = new byte[BUFFER_LENGTH];
		/*
		 * Index position in datagram buffer which determines the size of
		 * datagram.
		 */
		int indexPos = 1;

		/*
		 * Store CallID part in buffer.
		 */
		int callIDLen = callID.length();
		buffer[indexPos] = (byte) callIDLen;
		indexPos++;
		System.arraycopy(callID.getBytes(), 0, buffer, indexPos, callIDLen);
		indexPos = indexPos + callIDLen;
		/*
		 * Store CallType part in buffer.
		 */
		int callTypeLen = callType.getBytes().length;
		buffer[indexPos] = (byte) callTypeLen;
		indexPos++;
		System.arraycopy(callType.getBytes(), 0, buffer, indexPos, callTypeLen);
		indexPos = indexPos + callTypeLen;

		/*
		 * Counter value, how many records are in buffer.
		 */
		buffer[0] = 1;

		/*
		 * Send datagram.
		 */
		DatagramPacket dgram;
		dgram = new DatagramPacket(buffer, indexPos,
				InetAddress.getByName(mcastAddr), mcastPort);
		socket.send(dgram);
    }
    
    /**
     * Flush call buffer and send call records to multicast group.<BR>
     * <BR>
     * Data format:<BR>
     * <TABLE border=2><TR><TD>count (n)</TD><TD>callID-1 length</TD><TD>callID-1</TD>
     * <TD>callType-1 length</TD><TD>callType-1</TD>
     * <TD>...</TD><TD>callID-n length</TD><TD>callID-n</TD><TD>callType-n length</TD>
     * <TD>callType-n</TD> </TR></TABLE><BR>
     * <BR>
     * This method will try to put call records to fit in around 1000 bytes datagram packet,
     * and continue to flush until list of call records become empty.<BR>
     * Number of generated packets depends on call record length (callID length + callType length)
     * and total number of call records to be flushed to multicast socket.
     * 
     * @throws IOException
     */
    public void flush() throws IOException {

    	/*
    	 * Loop over and over until buffer is empty.
    	 */
    	while (callBuffer.size() > 0) {
    		/*
    		 * How many elements from call buffer are transfered to datagram buffer.
    		 */
			byte count = 0;
			/*
			 * Datagram buffer.
			 */
			byte[] buffer = new byte[BUFFER_LENGTH];
			/*
			 * Index position in datagram buffer which determines the size of datagram.
			 */
			int indexPos = 1;

			Iterator<Map.Entry<String, CallType>> iterator = callBuffer
					.entrySet().iterator();

			while (iterator.hasNext()) {

				Map.Entry<String, CallType> entry = iterator.next();

				/*
				 * Store CallID part in buffer.
				 */
				String callID = entry.getKey();
				int callIDLen = callID.length();
				buffer[indexPos] = (byte) callIDLen;
				indexPos++;
				System.arraycopy(callID.getBytes(), 0, buffer, indexPos,
						callIDLen);
				indexPos = indexPos + callIDLen;
				/*
				 * Store CallType part in buffer.
				 */
				CallType callType = entry.getValue();
				int callTypeLen = callType.getBytes().length;
				buffer[indexPos] = (byte) callTypeLen;
				indexPos++;
				System.arraycopy(callType.getBytes(), 0, buffer, indexPos,
						callTypeLen);
				indexPos = indexPos + callTypeLen;

				/*
				 * Remove it from map.
				 */
				iterator.remove();

				/*
				 * Increment counter.
				 */
				count++;

				/*
				 * Check that datagram size does not exceed.
				 */
				if (indexPos > 1000) {
					break;
				}
			}

			buffer[0] = (byte) count;

			/*
			 * Send datagram.
			 */
			DatagramPacket dgram;
			dgram = new DatagramPacket(buffer, indexPos,
					InetAddress.getByName(mcastAddr), mcastPort);
			socket.send(dgram);
    	}
    	
    }
    
    /**
     * Receive <I>{callID, CallType}</I> records from multicast sender.<BR>
     * <BR>
     * Packet format:<BR>
      * <TABLE border=2><TR><TD>count (n)</TD><TD>callID-1 length</TD><TD>callID-1</TD>
     * <TD>callType-1 length</TD><TD>callType-1</TD>
     * <TD>...</TD><TD>callID-n length</TD><TD>callID-n</TD><TD>callType-n length</TD>
     * <TD>callType-n</TD> </TR></TABLE><BR>
     * <BR>
     * <B>NOTE:</B><I>retrieve()</I> call is blocking call. After it, you can get <BR>
     * received values with <I>getReceivedCalls</I> function.<BR>
     * @throws IOException
     */   
    public void retrieve() throws IOException {

        byte[] data = new byte[BUFFER_LENGTH];

        DatagramPacket dgram = new DatagramPacket(data, data.length);

        do {
            // Blocks until a datagram is received
            mSocket.receive(dgram);
            // avoid packets that locally origin
        } while (ipInterfaces.contains(dgram.getAddress()));

        /*
         * Clear synchronization bit.
         */
        requestALL = false;
        
        /*
         * Number of bytes in datagram.
         */
        int receivedBytes = dgram.getLength();
        
        /*
         * How many records in buffer we have.
         */
        byte count = 0;
        int indexPos = 1;
        
        /*
         * Loop until all records in datagram are extracted.
         */
        while (count < data[0] && indexPos < receivedBytes) {
			
			int callIDLen = data[indexPos];
			indexPos++;
			String callID = new String(data, indexPos, callIDLen);
			indexPos = indexPos + callIDLen;

			int callTypeLen = data[indexPos];
			indexPos++;
			byte[] callTypeRaw = new byte[callTypeLen];
			System.arraycopy(data, indexPos, callTypeRaw, 0, callTypeLen);
			CallType callType = new CallType(callTypeRaw);
			indexPos = indexPos + callTypeLen;

			receivedBuffer.put(callID, callType);
			
			count++;
        }
        
        /*
         * Special case when ALL keyword is received.
         */
        if (data[0] == 0) {
        	String keyword = new String(data, 1, 3);
        	requestALL = keyword.equalsIgnoreCase("ALL");
        }

    }
    
    /**
     * This function will allow direct access to hashmap <I>receivedBuffer</I>
     * and allow to copy data with <I>putAll</I> command.<BR>
     * <B>NOTE: </B> Do not forget to clean map after copy operation is done.
     * @return received calls
     */
    public Map<String, CallType> getReceivedCalls() {
    	return receivedBuffer;
    }
    
    /**
     * Clear received call table. Usually invoked after reading, function <I>getReceivedCalls()</I>.
     */
    public void cleanReceivedCalls() {
    	receivedBuffer.clear();
    }
    
    /**
     * Check if request for synchronization was received.<BR>
     *  Each call to <I>retrieve()</I> will clear this bit,
     *  and set it only if datagram with synchronization
     *  request is received.
     * @return <I>true</I> if synchronization request was received,
     *  otherwise <I>false</I>
     */
    public boolean getRequestALL() {
    	return requestALL;
    }
    
    /**
     * <B>Request for synchronization</B><BR>
     * <BR>
     * This function call will send multicast datagram with word <I>ALL</I><BR>
     * and peer who receive such datagram should respond with full call record
     * table.<BR>
     * <BR>
     * That should happen only once when application starts.<BR>
     * <BR>  
     * @throws IOException
     * 
     */    
    public void sendSync() throws IOException {

        DatagramSocket socket = new DatagramSocket();

        byte[] data = { 0, 'A', 'L', 'L' };

        DatagramPacket dgram;

        dgram = new DatagramPacket(data, data.length, InetAddress.getByName(mcastAddr), mcastPort);

        socket.send(dgram);

        socket.close();

    }
    
    /**
     * <B>Request for synchronization</B><BR>
     * <BR>
     * This function call will send multicast datagram with word <I>ALL</I><BR>
     * and peer who receive such datagram should respond with full call record
     * table.<BR>
     * <BR>
     * That should happen only once when application starts.<BR>
     * <BR>
     * @deprecated Please use <B>sendSync()</B> function.
     *  
     * @throws IOException
     * 
     */
    public void requestSync() throws IOException {

        DatagramSocket socket = new DatagramSocket();

        byte[] data = { 3, 'A', 'L', 'L' };

        DatagramPacket dgram;

        dgram = new DatagramPacket(data, data.length, InetAddress.getByName(mcastAddr), mcastPort);

        socket.send(dgram);

        socket.close();

    }

    /**
     * Receive <I>{callID, CallType}</I> record from multicast sender.<BR>
     * <BR>
     * Packet format:<BR>
     * <TABLE border=2>
     * <TR>
     * <TH>Len1</TH>
     * <TH>CallID</TH>
     * <TH>Len2</TH>
     * <TH>CallType</TH>
     * </TABLE>
     * <BR>
     * Where:<BR>
     * <I>Len1</I> is length of <I>CallID</I> string, 1 byte<BR>
     * <I>Len2</I> is length of <I>CallType</I> string, 1 byte<BR>
     * <I>CallType</I> is custom string in format:<BR>
     * <I>source ip</I>:<I>source port</I>,<I>destination ip</I>:<I>destination
     * port</I>,<I>bye flag</I><BR>
     * <BR>
     * <B>Example:</B><BR>
     * <I>88.20.34.6</I>:<I>5060</I>,<I>192.168.0.1</I>:<I>5060</I>,<I>false</I><BR>
     * <BR>
     * <B>NOTE:</B><I>receive()</I> call is blocking call. After it, you can get <BR>
     * received values with <I>getCallID</I> and <I>getCallType</I> functions.<BR>
     * @deprecated Please use <B>retrieve()</B> function.
     * @throws IOException
     */
    public void receive() throws IOException {

        byte[] data = new byte[BUFFER_LENGTH];

        DatagramPacket dgram = new DatagramPacket(data, data.length);

        do {
            // Blocks until a datagram is received
            mSocket.receive(dgram);
            // avoid packets that locally origin
        } while (ipInterfaces.contains(dgram.getAddress()));

        // Format CallID and CallType
        int callIDlen = data[0];
        callID = new String(data, 1, callIDlen);
        // here we want to support {3, A, L, L} sequence of bytes, "ALL" string
        if (callID.equalsIgnoreCase("ALL")) {
            callType = null;
        } else {
            // in normal case, it is a call record
            callType = new String(data, callIDlen + 2, data[callIDlen + 1]);
        }
        // must reset length field!
        dgram.setLength(data.length);

    }

    /**
     * Close udp socket connections.<BR>
     * <BR>
     * <B>NOTE:</B> Please don't forget to call this at the end.<BR>
     */
    public void close() {
        socket.close();
        mSocket.close();
    }

    /**
     * <B>CallID</B><BR>
     * <BR>
     * Use it to read <I>CallID</I> value, after <I>receive()</I> function call.<BR>
     * <BR>
     * <B>NOTE:</B> This function may also return <I>"ALL"</I> string in case
     * when <BR>
     * such datagram arrives on multicast socket. It's meaning is to send all
     * <I>CallType</I><BR>
     * records to peer.<BR>
     * <BR>
     * 
     * @return the callID
     */
    public String getCallID() {
        return callID;
    }

    /**
     * <B>CallType</B><BR>
     * <BR>
     * Use it to read <I>CallType</I> value, after <I>receive()</I> function
     * call.<BR>
     * <BR>
     * <B>NOTE:</B> If callType is not set, the null value is returned. This
     * happens when<BR>
     * <I>ALL</I> string is received by <I>receive()</I> function call.<BR>
     * <BR>
     * <B>NOTE:</B> In case of bad argument or error, <I>null</I> value is returned.
     * @return the callType
     */
    public CallType getCallType() {

        /*
         * Return null value if callType string is null.
         */
        if (callType == null) {
            return null;
        }

        CallType retVal;
        
        try {
            retVal = new CallType(callType);
        } catch (IndexOutOfBoundsException ex1) {
            ex1.printStackTrace();
            return null;
        } catch (UnknownHostException ex2) {
            ex2.printStackTrace();
            return null;
        }
        
        return retVal;
    }

}
