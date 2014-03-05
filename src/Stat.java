/**
 * <H1>Statistic</H1>
 * Holds various statistical data collected during runtime.
 * @author eigorde
 *
 */
public class Stat {

	/**
	 * SIP <I>INVITE</I>, <I>BYE</I> and <I>CallID not found</I> column indexes.
	 */
	final public int SIP_INVITE = 0, SIP_BYE = 1, SIP_NOT_FOUND = 2;

	/**
	 * Watchdog node update counter, column index.
	 */	
	final public int WATCHDOG_NODES = 3;

	/**
	 * Multicast sync. ALL request, INVITE update and BYE update column indexes.
	 */	
	final public int SYNC_ALL = 4, SYNC_INVITE = 5, SYNC_BYE = 6;
	
	private long[][] data = {
			{0, 0, 0}, // SIP INVITE
			{0, 0, 0}, // SIP BYE
			{0, 0, 0}, // CallID not found
			
			{0, 0, 0}, // watchdog node signal
			
			{0, 0, 0}, // sync request all
			{0, 0, 0}, // sync invite update
			{0, 0, 0}  // sync bye update
	};
	
	private long lastUpdate;
	
	private void lastUpdate() {
		long delta = System.currentTimeMillis() - lastUpdate;
		// 5 sec. time interval for moving columns
		if (delta > 5000) {
			for (int row = 0; row < data.length; row++) {
				data[row][0] = data[row][1];
				data[row][1] = data[row][2];
			}
			lastUpdate = System.currentTimeMillis();
		}
	}
	
	/**
	 * Statistic data.
	 */
	public Stat() {
		lastUpdate = System.currentTimeMillis();
	}
	
	/**
	 * Clear all data.
	 */
	public void clear() {
		for (int col = 0; col < data[0].length; col++)
			for (int row = 0; row < data.length; row++)
				data[row][col] = 0;
	}
	
	/**
	 * Increment counter.
	 * @param index index of counter 
	 */
	public void increment(int index) {
		lastUpdate();
		data[index][2]++;		
	}

	private String getColumnName(int index) {
		switch (index) {
		case SIP_INVITE:     return "    SIP INVITE";
		case SIP_BYE:        return "    SIP    BYE";
		case SIP_NOT_FOUND:  return "CALL NOT FOUND";
		case WATCHDOG_NODES: return "WATCHDOG NODES";
		case SYNC_ALL:       return "   SYNC    ALL";
		case SYNC_INVITE:    return "   SYNC INVITE";
		case SYNC_BYE:       return "   SYNC    BYE";
		}
		return "-";
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
	 * Present statistic data in tabular form.
	 * @param columnWidth width of each column
	 * @return table with data
	 */
	public String getStatTable(int columnWidth) {
		
		String header = leftAdjust("", columnWidth * 4, "-") + "\r\n";
		header = header + leftAdjust("|COUNTER NAME", columnWidth, " ") +
				leftAdjust("|15sec.", columnWidth, " ") +
				leftAdjust("|10sec.", columnWidth, " ") +
				leftAdjust("|5sec.", columnWidth, " ") + "|\r\n";
		header = header + leftAdjust("", columnWidth * 4, "-") + "\r\n";
		
		String body = "";
		
		for (int row = 0; row < data.length; row++) {
			body = body + leftAdjust("|" + getColumnName(row), columnWidth, " ");
			for (int col = 0; col < data[0].length; col++) {
				body = body + "|" + rightAdjust(String.valueOf(data[row][col]), columnWidth - 1, " ");
			}
			body = body + "|\r\n";
		}
		return header + body + leftAdjust("", columnWidth * 4, "-") + "\r\n";		
	}
	
	@Override
	public String toString() {
		
		String header = "COUNTER NAME\t\t 15sec.\t\t 10sec.\t\t 5sec.\t\t\r\n";
		String body = "";
		
		for (int row = 0; row < data.length; row++) {
			body = body + getColumnName(row) + ": \t\t";
			for (int col = 0; col < data[0].length; col++) {
				body = body + String.valueOf(data[row][col]) + "\t\t";
			}
			body = body + "\r\n";
		}
		return header + body;
	}
	
	
}
