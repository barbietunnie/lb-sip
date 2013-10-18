import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * CallType class file.<BR>
 * <BR>
 * Used to store a single record which links outside <I>ip:port</I> to inside <I>ip:port</I>.<BR>
 * 
 * @author eigorde
 *
 */
public class CallType {

    /**
     * Source port number. Should be <I>5060</I> for most SIP implementations.
     */
    public int srcPort;
    /**
     * Source ip address.
     */    
    public InetAddress srcAddress;
    
    /**
     * Destination port number. Should be <I>5060</I> for most SIP protocol as a default listening port.
     */
    public int dstPort;
    /**
     * Destination ip address.
     */    
    public InetAddress dstAddress;
    
    /**
     * Bye flag, indicating if the call is about to be released. After SIP BYE message, when ACK is received, call is done.
     */
    public boolean bye;
    
    /**
     * <B>CallType</B> data type<BR>
     * <BR>
     * This data type stores source ip address:port and links it to destination address:port.<BR>
     * <BR>
     * Usually destination port is udp port <I>5060</I> for SIP, while source port can be any value.<BR>
     * Some SIP implementations also enforce source port at value <I>5060</I>.<BR>
     * @param srcAddress source ip address
     * @param srcPort source port 
     * @param dstAddress destination ip address
     * @param dstPort destination port 
     */
    public CallType(InetAddress srcAddress, int srcPort, InetAddress dstAddress, int dstPort) {
        this.srcPort = srcPort;
        this.srcAddress = srcAddress;
        this.dstPort = dstPort;
        this.dstAddress = dstAddress;
        bye = false;
    }

    /**
     * <B>CallType</B> data type<BR>
     * <BR>
     * This data type stores source ip address:port and links it to destination address:port.<BR>
     * <BR>
     * Usually destination port is udp port <I>5060</I> for SIP, while source port can be any value.<BR>
     * Some SIP implementations also enforce source port at value <I>5060</I>.<BR> 
     * @param callType string representation in form:<BR>
     * <I>srcAddress:srcPort,dstAddress:dstPort,byeFlag</I><BR>
     * @throws UnknownHostException
     */
    public CallType(String callType) throws UnknownHostException, IndexOutOfBoundsException {
        /*
         * Index pointers for substring function calls.
         */
        int startIndex, endIndex;

        /*
         * Substring to hold temporary value.
         */
        String tmp = "";
        
        /*
         * Source ip address.
         */
        startIndex = 0;
        endIndex = callType.indexOf(':');
        tmp = callType.substring(startIndex, endIndex);
        this.srcAddress = InetAddress.getByName(tmp);

        /*
         * Source port number.
         */
        startIndex = endIndex + 1;
        endIndex = callType.indexOf(',');
        tmp = callType.substring(startIndex, endIndex);
        this.srcPort = Integer.valueOf(tmp);

        /*
         * Destination ip address.
         */
        startIndex = endIndex + 1;
        endIndex = callType.indexOf(':', startIndex);
        tmp = callType.substring(startIndex, endIndex);
        this.dstAddress = InetAddress.getByName(tmp);

        /*
         * Destination port number.
         */
        startIndex = endIndex + 1;
        endIndex = callType.indexOf(',', startIndex);
        tmp = callType.substring(startIndex, endIndex);
        this.dstPort = Integer.valueOf(tmp);

        /*
         * By default, bye flag is set to false in CallType object instance.
         */
        if (callType.endsWith("true"))
            this.bye = true;
        
    }
    
    /**
     * This function returns a value of <B>CallType</B> object in the following format:<BR>
     * <BR> 
     * <I>source ip</I>:<I>source port</I>,<I>destination ip</I>:<I>destination port</I>,<I>bye flag</I><BR>
     * <BR>
     * </B>Example:<B><BR> 
     * <I>88.20.34.6</I>:<I>5060</I>,<I>192.168.0.1</I>:<I>5060</I>,<I>false</I><BR>
     * <BR>
     * <BR>
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return srcAddress.getHostAddress() + ":" + srcPort + "," + dstAddress.getHostAddress() + ":"
                + dstPort + "," + String.valueOf(bye);
    }
    
   
    
}
