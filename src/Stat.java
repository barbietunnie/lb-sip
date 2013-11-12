/**
 * <H1>Statistic</H1>
 * Holds various statistical data collected during runtime.
 * @author eigorde
 *
 */
public class Stat {

	/*
	 * Some SIP counters.
	 */
	public long sipInvite = 0;
	public long sipBye = 0;
	public long sipNotFound = 0;
	
	/*
	 * Watchdog counters.
	 */
	public long watchdogNodes = 0;
	
	/*
	 * Sync. counters.
	 */
	public long syncAll = 0;
	public long syncInvite = 0;
	public long syncBye = 0;
	
}
