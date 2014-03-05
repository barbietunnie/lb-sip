import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;

/**
 * <H1>Registrator</H1><BR>
 * Registrator should collect SIP User Agents (VoIP phones)
 * and keep tracking who has registered, when, from which ip address,
 * under which name and password (secret).<BR>
 * 
 * @author eigorde
 *
 */
public class Registrator {

	/**
	 * User record structure.
	 */
	private class Record {
		String password;
		String ipAddress;
		long timestamp;
		boolean registered;
	}
	
	private Hashtable<String, Record> udb;
	
	/**
	 * Initialize new user database.
	 */
	public Registrator() {
		udb = new Hashtable<String, Record>();
	}
	
	/**
	 * Create or update existing user with empty password.<BR>
	 * This will allow register without authorization.<BR>
	 * Later, user record can be updated with <I>addUser(String user, String password)</I>
	 * function if necessary.
	 * @param user name of phone number, eg. <I>1001</I>
	 */
	public void addUser(String user) {
		Record r = new Record();
		r.password = "";
		r.ipAddress = "";
		r.registered = false;
		r.timestamp = 0;
		
		udb.put(user, r);
	}
	
	/**
	 * Create or update existing user with non-empty password.<BR>
	 * This will allow register without authorization.<BR>
	 * @param user name of phone number, eg. <I>1001</I>
	 * @param password user password for <I>register</I> validation
	 */	
	public void addUser(String user, String password) {
		Record r = new Record();
		r.password = password;
		r.ipAddress = "";
		r.registered = false;
		r.timestamp = 0;
		
		udb.put(user, r);		
	}
	
	/**
	 * Register user and validate if successful.
	 * This will work if user exists in db and <I>user</I> argument is supplied.<BR>
	 * If user record contains a password, then digest value will be compared.<BR>
	 * Optional arguments may be <I>null</I> or empty strings.
	 * @param user phone number or user name, <B>mandatory</B>, eg. <I>1001</I>
	 * @param ipAddress user ip address, <B>mandatory</B>, eg. <I>192.168.1.101</I>
	 * @param realm name of the realm, <B>optional</B>, eg. <I>asterisk</I>
	 * @param nonce random value provided by registrator, <B>optional</B>
	 * @param uri request uri of method, <B>optional</B>
	 * @param md5result result provided by SIP UA (phone) for comparison with password stored in db, <I>optional</I>
	 * @return <I>true</I> if registered, otherwise <I>false</I>
	 */
	public boolean register(String user, String ipAddress, String realm, String nonce, String uri, String md5result) {
		
		boolean retVal = false;
		
		Record userRecord = udb.get(user);
		
		/*
		 * Look for the user in db.
		 */
		if (userRecord != null) {
		
			/*
			 * Check is there is any password.
			 * If password is blank, then we accept subscriber.
			 */
			if (userRecord.password.isEmpty()) {
				
				userRecord.registered = true;
				userRecord.timestamp = System.currentTimeMillis();
				userRecord.ipAddress = ipAddress;
				
				retVal = true;
			}
			else {
				/*
				 * If it's not empty, then challenge with md5 digest.
				 */
				try {
					if (md5result
							.equalsIgnoreCase(toHex(digest(user,
									userRecord.password, realm, "REGISTER",
									uri, nonce)))) {
						
						userRecord.registered = true;
						userRecord.timestamp = System.currentTimeMillis();
						userRecord.ipAddress = ipAddress;
						
						retVal = true;
					}
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
		}
		return retVal;
	}
	
	/**
	 * Unregister selected user name of phone number.
	 * This will put <I>registered</I> flag to <I>false</I>
	 * and clear ip address and timestamp.<BR>
	 * If invalid user is supplied, nothing will happen.
	 * @param user phone or name
	 */
	public void unregister(String user) {
		Record r = udb.get(user);
		if (r != null) {
			r.registered = false;
			r.ipAddress = "";
			r.timestamp = 0;
		}
	}
	
	/**
	 * Check if user is registered.
	 * @param user name of the user or phone number, eg. <I>1001</I>
	 * @return <I>true</I> only if user is in db and is registered, otherwise <I>false</I>
	 */
	public boolean isRegistered(String user) {
		boolean retVal = false;
		if (udb.containsKey(user)) {
			retVal = udb.get(user).registered;
		}
		return retVal;
	}
	
	/**
	 * Read user db from file. If you don't put full path and name,
	 * it will try to read in current application directory.<BR>
	 * File format is:<PRE>
	 * user1,password1
	 * user2,password2
	 * ...
	 * userX
	 * userX+1
	 * ...
	 * userX+n,passwordX+n
	 * </PRE>
	 * It is allowed to have users with and without password.
	 * @param filename name of the file, eg. <I>user.db</I>
	 */
	public void dbRead(String filename) {
		
		File file = new File(filename);
		
		if (file.exists()) {
		
			try {
		
				BufferedReader rd = new BufferedReader(new FileReader(file));
		
				String line;
				while ( (line = rd.readLine()) != null) {
					if (line.length() > 0) {
					
						Record record = new Record();
						int idx = line.indexOf(',');
					
						if (idx > 0) {
							record.password = line.substring(idx + 1);
							udb.put(line.substring(0, idx), record);
						}
						else {
							record.password = "";
							udb.put(line, record);
						}
					}
				}	
			
				rd.close();
		
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Save user db to file. If you don't put full path and name,
	 * it will save in current application directory.<BR>
	 * File format is:<PRE>
	 * user1,password1
	 * user2,password2
	 * ...
	 * userX
	 * userX+1
	 * ...
	 * userX+n,passwordX+n
	 * </PRE>
	 * It is allowed to have users with and without password.
	 * @param filename name of the file, eg. <I>user.db</I>
	 */
	public void dbWrite(String filename) {
		File file = new File(filename);
		
		try {
		
			BufferedWriter wr = new BufferedWriter(new FileWriter(file));
		
			for (String user : udb.keySet()) {
				String password = udb.get(user).password;
				if (password.isEmpty()) {
					wr.write(user);
				}
				else {
					wr.write(user + "," + password);
				}
				wr.newLine();
			}
			wr.flush();		
			wr.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Calculate response digest according to method, username, password, realm, uri
	 * and nonce strings.<BR>
	 * Example message with <I>Authorization</I> information:
	 * <PRE>
	 * REGISTER sip:192.168.110.1;transport=UDP SIP/2.0
	 * Via: SIP/2.0/UDP 192.168.110.6:5060;branch=z9hG4bK-d8754z-1a3438d5139b6c11-1---d8754z-
	 * Max-Forwards: 70
	 * Contact: <sip:1001@192.168.110.6:5060;rinstance=9a9ec697d3af4137;transport=UDP>
	 * To: <sip:1001@192.168.110.1;transport=UDP>
	 * From: <sip:1001@192.168.110.1;transport=UDP>;tag=485af632
	 * Call-ID: ZDg0NWM3YjFmYmE1NmFlM2UzMTYzN2NkODc0MTQ5MTk.
	 * CSeq: 3 REGISTER
	 * Expires: 60
	 * Authorization: Digest username="1001",realm="asterisk",nonce="26f2cc46",uri="sip:192.168.110.1;transport=UDP",response="a5be0271a5e1b9296396bc80448e2dfe",algorithm=MD5
	 * Content-Length: 0
	 * </PRE>
	 * Digest value is calculated according to:<PRE>
	 * HA1 = md5(username:realm:password)<BR>
	 * HA2 = md5(method:uri)<BR>
	 * digest = md5(HA1:nonce:HA2)<BR>
	 * </PRE>
	 * Ref. http://www.sieraybould.net/Software/SipMD5Calc/
	 * @param username phone number or name, eg. <I>1001</I>
	 * @param password password for username, eg. <I>1001</I>
	 * @param realm name, eg. <I>asterisk</I>
	 * @param method SIP method name, eg. <I>REGISTER</I>
	 * @param uri request-uri field, eg. <I>sip:192.168.110.1;transport=UDP</I>
	 * @param nonce random value generated from registrator 
	 * @return md5 digest according to supplied values
	 * @throws NoSuchAlgorithmException
	 */
	private byte[] digest(String username, String password, String realm, String method, String uri, String nonce) throws NoSuchAlgorithmException {
		
		String part1 = username + ":" + realm + ":" + password;
		String part2 = method + ":" + uri;

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		
		byte[] digest1 = md5.digest(part1.getBytes());
		byte[] digest2 = md5.digest(part2.getBytes());
		
		byte[] digest3 = md5.digest((toHex(digest1) + ":" + nonce + ":" + toHex(digest2)).getBytes());
		
		return digest3;

	}
	
	/**
	 * Calculate hex value from array of bytes.
	 * @param digest array of bytes
	 * @return hexadecimal value
	 */
	private String toHex(byte[] digest) {
		StringBuilder hexString = new StringBuilder();
		for (int i = 0; i < digest.length; i++) {
			String hex = Integer.toHexString(0xFF & digest[i]);
			if (hex.length() == 1) {
				// could use a for loop, but we're only dealing with a single
				// byte
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}
	
    /**
	 * Left adjustment of string with filler char.<BR>
	 * If <I>arg</I> is longer then <I>len</I>, then
	 * return value will be truncated with three dots.
	 * @param arg string to fill with spaces
	 * @param len total length, should be greater than <I>arg</I>
	 * @param fillerChar a char or string which is appended to <I>arg</I>
	 * @return arg + fillerChar(s)
	 */
	private String leftAdjust(String arg, int len, String fillerChar) {
		
		if (arg == null) {
			arg = "";
		}
		
		String retVal = arg;
		
		int argLen = arg.length();
		
		if (len > argLen) {
			for (int i = 0; i < (len - argLen); i++) {
				retVal = retVal + fillerChar;
			}
		}
		else {
			retVal = arg.substring(0, len - 3) + "...";
		}
		return retVal;
	}
	
    /**
	 * Right adjustment of string with filler char.<BR>
	 * If <I>arg</I> is longer then <I>len</I>, then
	 * return value will be truncated with three dots.
	 * @param arg string to fill with spaces
	 * @param len total length, should be greater than <I>arg</I>
	 * @param fillerChar a char or string which is prepended to <I>arg</I>
	 * @return fillerChar(s) + arg
	 */
	private String rightAdjust(String arg, int len, String fillerChar) {

		if (arg == null) {
			arg = "";
		}

		String retVal = arg;
		
		int argLen = arg.length();
		
		if (len > argLen) {
			retVal = "";
			for (int i = 0; i < (len - argLen); i++) {
				retVal = retVal + fillerChar;
			}
			retVal = retVal + arg;
		}
		else {
			retVal = arg.substring(0, len - 3) + "...";
		}
		return retVal;
	}
	
	/**
	 * Present register data in tabular form.
	 * @param columnWidth width of each column
	 * @return table with data
	 */
	public String getRegisterTable(int columnWidth) {
		
		String header = leftAdjust("", columnWidth * 5, "-") + "\r\n";
		header = header + leftAdjust("|USER", columnWidth, " ") +
				leftAdjust("|PASSWORD", columnWidth, " ") +
				leftAdjust("|REGISTERED", columnWidth, " ") +
				leftAdjust("|IP ADDRESS", columnWidth, " ") +
				leftAdjust("|TIMESTAMP", columnWidth - 1, " ") + "|\r\n";
		header = header + leftAdjust("", columnWidth * 5, "-") + "\r\n";
		
		StringBuilder body = new StringBuilder();

		for (String user : udb.keySet()) {
			Record r = udb.get(user);
			
			String timestamp = "";
			if (r.timestamp > 0) {
				timestamp = String.valueOf(System.currentTimeMillis() - r.timestamp) + " sec. ago";
			}
			
			body.append("|" + leftAdjust(user, columnWidth - 1, " ") +
					"|" + leftAdjust(r.password, columnWidth - 1, " ") +
					"|" + rightAdjust(String.valueOf(r.registered), columnWidth - 1, " ") +
					"|" + leftAdjust(r.ipAddress, columnWidth - 1, " ") +
					"|" + rightAdjust(timestamp, columnWidth - 2, " ") +
					"|\r\n");
		}
		
		
		return header + body + leftAdjust("", columnWidth * 5, "-") + "\r\n";		
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("User\t\tPassword\t\tRegistered\t\tIP Address\t\tTimestamp\r\n");
		for (String user : udb.keySet()) {
			Record r = udb.get(user);
			sb.append(user + "\t\t" + r.password + "\t\t" + r.registered);
			if (r.registered) {
				sb.append("\t\t" + r.ipAddress + "\t\t" + String.valueOf(System.currentTimeMillis() - r.timestamp) + " sec. ago");
			}
			sb.append("\r\n");
		}
		return sb.toString();
	}	
	
	
}
