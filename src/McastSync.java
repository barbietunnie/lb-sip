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
import java.util.List;

/**
 * <H1>Multicast synchronizer</H1>
 * <BR>
 * This class is for keeping in sync two Load Balacer's.<BR>
 * 
 * @author eigorde
 * 
 */
public class McastSync {

    private static final int BUFFER_LENGTH = 4096;

    private DatagramSocket socket;

    private MulticastSocket mSocket;

    private String mcastAddr = "226.13.25.1";
    private int mcastPort = 5555;

    private String callID = "";
    private String callType = null;

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
     * </TABLE>
     * <BR>
     * Where:<BR>
     * <I>Len1</I> is length of <I>CallID</I> string, 1 byte<BR>
     * <I>Len2</I> is length of <I>CallType</I> string, 1 byte<BR>
     * <I>CallType</I> is custom string in format:<BR>
     * <I>source ip</I>:<I>source port</I>,<I>destination ip</I>:<I>destination
     * port</I>,<I>bye flag</I><BR>
     * <BR>
     * </B>Example:<B><BR>
     * <I>88.20.34.6</I>:<I>5060</I>,<I>192.168.0.1</I>:<I>5060</I>,<I>false</I><BR>
     * <BR>
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
     * <B>Request for synchronization</B><BR>
     * <BR>
     * This function call will send multicast datagram with word <I>ALL</I><BR>
     * and peer who receive such datagram should respond with full call record
     * table.<BR>
     * <BR>
     * That should happen only once when application starts.<BR>
     * <BR>
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
     * 
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
