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
     * @param  callTypeBytes raw bytes of callType object, format:<BR>
     * <TABLE border=2><TR><TD>inetLen</TD><TD>srcAddress</TD><TD>srcPort</TD>
     * <TD>inetLen</TD><TD>dstAddress</TD><TD>dstPort</TD><TD>bye flag</TD></TR></TABLE><BR>
     * where:<BR>
     * <UL>
     *  <LI><I>inetLen</I> is length of source and destination ip address. For ipv4 value is <I>4</I>, for ipv6 address value is <I>16</I></LI>
     *  <LI><I>srcAddress</I> and <I>dstAddress</I> are byte representation of ipv4 or ipv6 address. Length is defined in <I>inetLen</I> byte</LI>
     *  <LI><I>srcPort</I> and <I>dstPort</I> are 2 byte values</LI>
     *  <LI><I>bye flag</I> is 1 byte value, either <I>0</I> or <I>1</I></LI>
     * </UL>
     * @throws UnknownHostException 
     */
    public CallType(byte[] callTypeBytes) throws UnknownHostException {
    	
    	byte srcInetLen = callTypeBytes[0];
    	byte[] srcAddress, dstAddress;
    	byte[] srcPort = {0, 0}, dstPort = {0, 0};
    	
    	srcAddress = new byte[srcInetLen];
    	System.arraycopy(callTypeBytes, 1, srcAddress, 0, srcInetLen);

    	srcPort[0] = callTypeBytes[1 + srcInetLen];
    	srcPort[1] = callTypeBytes[2 + srcInetLen];
    	
    	byte dstInetLen = callTypeBytes[3 + srcInetLen];
    	
    	dstAddress = new byte[dstInetLen];
    	System.arraycopy(callTypeBytes, 4 + srcInetLen, dstAddress, 0, dstInetLen);
    	
    	dstPort[0] = callTypeBytes[4 + srcInetLen + dstInetLen];
    	dstPort[1] = callTypeBytes[5 + srcInetLen + dstInetLen];
    	
        this.srcPort = ((srcPort[0] & 0xFF) << 8) + (srcPort[1] & 0xFF);
        this.srcAddress = InetAddress.getByAddress(srcAddress);
        this.dstPort = ((dstPort[0] & 0xFF) << 8) + (dstPort[1] & 0xFF);
        this.dstAddress = InetAddress.getByAddress(dstAddress);
        
        bye = (callTypeBytes[6 + srcInetLen + dstInetLen] == 1);
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
    
    /**
     * Get raw byte representation of CallType object.
     * @return data bytes,<BR>
     * a raw bytes of callType object, format:<BR>
     * <TABLE border=2><TR><TD>inetLen</TD><TD>srcAddress</TD><TD>srcPort</TD>
     * <TD>inetLen</TD><TD>dstAddress</TD><TD>dstPort</TD><TD>bye flag</TD></TR></TABLE><BR>
     * where:<BR>
     * <UL>
     *  <LI><I>inetLen</I> is length of source and destination ip address. For ipv4 value is <I>4</I>, for ipv6 address value is <I>16</I></LI>
     *  <LI><I>srcAddress</I> and <I>dstAddress</I> are byte representation of ipv4 or ipv6 address. Length is defined in <I>inetLen</I> byte</LI>
     *  <LI><I>srcPort</I> and <I>dstPort</I> are 2 byte values</LI>
     *  <LI><I>bye flag</I> is 1 byte value, either <I>0</I> or <I>1</I></LI>
     * </UL>
     */
    public byte[] getBytes() {
    	
    	byte srcInetLen = (byte) this.srcAddress.getAddress().length;
    	byte dstInetLen = (byte) this.dstAddress.getAddress().length;
    	
    	byte[] data = new byte[1 + srcInetLen + 2 + 1 + dstInetLen + 2 + 1];
    	
    	data[0] = srcInetLen;

    	// Source address and port
    	System.arraycopy(this.srcAddress.getAddress(), 0, data, 1, srcInetLen);
    	data[1 + srcInetLen] = (byte) ((srcPort & 0xFF00) >> 8);
    	data[2 + srcInetLen] = (byte) ((srcPort & 0x00FF));
    	
    	data[3 + srcInetLen] = dstInetLen;
    	
    	// Destination address and port
    	System.arraycopy(this.dstAddress.getAddress(), 0, data, 4 + srcInetLen, dstInetLen);
    	data[4 + srcInetLen + dstInetLen] = (byte) ((dstPort & 0xFF00) >> 8);
    	data[5 + srcInetLen + dstInetLen] = (byte) ((dstPort & 0x00FF));

    	// bye flag
    	if (bye) {    	
    		data[6 + srcInetLen + dstInetLen] = 1;
    	}
    	else {
    		data[6 + srcInetLen + dstInetLen] = 0;
    	}
    	
    	return data;
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (bye ? 1231 : 1237);
		result = prime * result
				+ ((dstAddress == null) ? 0 : dstAddress.hashCode());
		result = prime * result + dstPort;
		result = prime * result
				+ ((srcAddress == null) ? 0 : srcAddress.hashCode());
		result = prime * result + srcPort;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CallType other = (CallType) obj;
		if (bye != other.bye)
			return false;
		if (dstAddress == null) {
			if (other.dstAddress != null)
				return false;
		} else if (!dstAddress.equals(other.dstAddress))
			return false;
		if (dstPort != other.dstPort)
			return false;
		if (srcAddress == null) {
			if (other.srcAddress != null)
				return false;
		} else if (!srcAddress.equals(other.srcAddress))
			return false;
		if (srcPort != other.srcPort)
			return false;
		return true;
	}
    
}
