import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Hashtable;

public class LoadBalancer {

    /**
     * Version string.
     */
    final static String ver = "LB v.0.2";

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
     * TCP port for telnet management. By default, it will bind on all interfaces.
     */
    static int telnetPort = 4444;

    /**
     * Watchdog udp port number to receive keep alive messages from nodes.<BR>
     * If it is 0, then watchdog should be disabled.<BR>
     */
    static int watchdogPort = 5556;

    /**
     * Any socket.
     */
    static SocketAddress anySocket;
    static DatagramSocket anyDatagramSocket;

    /**
     * List of nodes defined statically at startup as command argument(s).<BR>
     * This will disable watchdog feature.<BR>
     */
    static Hashtable<Integer, String> nodeList;

    /**
     * An ip address which points to next SIP server where to forward SIP
     * INVITE.<BR>
     * 
     */
    static String nodePointer;

    /**
     * Call table.
     */
    final static Hashtable<String, CallType> callTable = new Hashtable<String, CallType>();

    /**
     * Watchdog table for storing info about nodes which have reported their status to watchdog process.
     */
    final static Hashtable<String, Long> watchdogTable = new Hashtable<String, Long>();

    /**
     * Flag to keep dispatcher and other threads running forever.<BR>
     */
    static boolean alive = true;

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
                + " java -jar lb.jar [node1] [node2] ... [nodeX] [--bindPort XX] [--watchdogPort XX] [--telnetPort XX] [--verbose X]\n"
                + " where:\n"
                + "  --bindPort XX  will bind to udp port XX and wait for SIP messages. Default is 5060.\n"
                + "  --telnetPort XX  will start telnet management interface on tcp port XX and wait for client. Default is 4444.\n"
                + "  --watchdogPort XX will start watchdog listener on udp port XX. Default is 5556.\n"
                + "  --verbose X determine the level of logging. If 0, logging is turned off, if 1 only errors are shown (default), 4 is max. verbosity.\n\n"
                + "  node1, node2, ... nodeX are ip addresses of nodes manually defined in case that watchdog service is not in use.\n"
                + "  If you specify static nodes, then watchdog is disabled, an no new nodes will be added in list for load balancing.\n");        
    }
    
    /**
     * Main, program entry point.
     * @param args see <I>usage()</I> on how to use program arguments and switches
     * @throws IOException
     */
    public static void main(String args[]) throws IOException {

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
        nodePointer = "";

        if (args.length == 0) {
            usage();
            return;
        } else {
            GetOpts op = new GetOpts(args);
            /*
             * Read command line switches.
             */
            for (String switchName : op.getSwitches()) {
                if (op.isSwitch(switchName)) {
                    if (switchName.equalsIgnoreCase("--bindPort"))
                        bindPort = Integer.parseInt(op.getSwitch(switchName));
                    else if (switchName.equalsIgnoreCase("--watchdogPort"))
                        watchdogPort = Integer.parseInt(op.getSwitch(switchName));
                    else if (switchName.equalsIgnoreCase("--telnetPort"))
                        telnetPort = Integer.parseInt(op.getSwitch(switchName));
                    else if (switchName.equalsIgnoreCase("--verbose"))
                        verbose = Integer.parseInt(op.getSwitch(switchName));
                    else
                        log(Thread.currentThread().getName(), "Unkown switch option: " + switchName);
                }
            }
            
            /*
             * If user request help, print usage and quit.
             */
            if (op.isSwitch("-h") || op.isSwitch("--help")) {
                usage();
                return;
            }
            
            /*
             * Add list of static nodes.
             */
            int counter = 0;
            for (String nodeAddress : op.getArguments()) {
                nodeList.put(counter++, nodeAddress);
            }
            nodePointer = nodeList.get(0);

            /*
             * Disable watchdog if there are manually added nodes to list.
             */
            if (!nodeList.isEmpty())
                watchdogPort = 0;

        }

        /*
         * Bind to any socket and sip port.
         */
        anySocket = new InetSocketAddress(bindPort);
        anyDatagramSocket = new DatagramSocket(anySocket);

        log(Thread.currentThread().getName(), "Listening on: " + bindPort + ".");

        if (nodeList.size() > 0) {
            String nodes = "Node list: ";
            for (Integer key : nodeList.keySet()) {
                nodes = nodes + nodeList.get(key) + " ";
            }
            log(Thread.currentThread().getName(), nodes);
        } else
            log(Thread.currentThread().getName(), "Node list is empty.");

        /*
         * Start dispatcher.
         */
        Thread dispacherThread = new Thread(new Dispatcher(), "dispacherThread");
        log(Thread.currentThread().getName(), "Starting dispatcher process.");
        dispacherThread.start();

        Thread watchdogThread = new Thread(new Watchdog(), "watchdogThread");
        /*
         * Check if watchdog is disabled. If not, start watchdog process.
         */
        if (watchdogPort > 0) {
            log(Thread.currentThread().getName(), "Starting watchdog process.");
            watchdogThread.start();
        }
        else {
            log(Thread.currentThread().getName(), "Watchdog is disabled.");
        }
        
        Thread syncThread = new Thread(new Synchronization(), "syncThread");
        log(Thread.currentThread().getName(), "Starting McastSync process.");
        syncThread.start();

        TelnetServer ts = new TelnetServer(telnetPort);
        configTelnetServer(ts);
        Thread telnetThread = new Thread(ts, "telnetThread");
        log(Thread.currentThread().getName(), "Starting telnet interface.");
        telnetThread.start();

        McastSync mcastSync = new McastSync();
        log(Thread.currentThread().getName(), "Broadcasting synchronization request message to all peers.");
        mcastSync.requestSync();
        mcastSync.close();

    }
    
    /**
     * This helper function will configure telnet server instance.
     * @param ts reference to TelnetServer object
     */
    private static void configTelnetServer(TelnetServer ts) {
        
        /*
         * First we add some commands to the list of auto complete commands.
         */
        ts.addCommand("show mem");
        ts.addCommand("show cpu");
        ts.addCommand("show nodes");
        ts.addCommand("show table");
        ts.addCommand("show watchdog");
        
        /*
         * Turn off debugging so we don't mess up console printouts.
         */
        ts.setDebug(false);
    }
    
    /**
     * Process telnet command. This function is used by Telnet server process.
     * @param command name of the command
     * @return printout or result
     */
    public static String telnetCommand(String command) {
        String retVal  = "";
        
        if (command.startsWith("show ")) {
            // Strip show word.
            command = command.substring("show ".length());
            
            Runtime rt = Runtime.getRuntime();
            
            final int mb = 1024 * 1024;
            if (command.startsWith("mem")) {                
                retVal = "Mem [free/total, max]: "
                        + (rt.freeMemory() / mb) + "/"
                        + (rt.totalMemory() /mb) + " MB, "
                        + (rt.maxMemory() / mb) + " MB\r\n";
            }
            else if (command.startsWith("cpu")) {
                retVal = "CPU core count: " + rt.availableProcessors() + "\r\n";
            }
            else if (command.startsWith("nodes")) {
                retVal = "Nodes:\r\n";
                for (Integer key : nodeList.keySet()) {
                    retVal = retVal + nodeList.get(key) + "\r\n";
                }
                if (nodeList.isEmpty()) {
                    retVal = retVal + "Empty.\r\n";
                }
            }
            else if (command.startsWith("table")) {
                StringBuilder sb = new StringBuilder("");
                sb.append("Call table:\r\nCall ID\t\tSource:port, destination:port\r\n");
                for (String key : callTable.keySet()) {
                    sb.append(key + "\t\t" + callTable.get(key) + "\r\n");
                }
                if (callTable.isEmpty()) {
                    sb.append("Empty.\r\n");
                }
                else {
                    sb.append("Total: " + callTable.size() + "\r\n");
                }
                retVal = sb.toString();
            }
            else if (command.startsWith("watchdog")) {
                StringBuilder sb = new StringBuilder("");
                sb.append("Watchdog table:\r\nIP address\t\tTime\r\n");
                for (String key : watchdogTable.keySet()) {
                    long ago = (System.currentTimeMillis() - watchdogTable.get(key)) / 1000;
                    sb.append(key + "\t\t" + ago + " sec.\r\n");
                }
                if (watchdogTable.isEmpty()) {
                    sb.append("Empty.\r\n");
                }
                retVal = sb.toString();
            }
            else {
                retVal = "Unknown show command:" + command + "\r\n";
            }
        }
        else if (command.startsWith("set ")) {
            // Strip set word.
            command = command.substring("set ".length());            
        }
        else {
            retVal = "Unknown command.\r\n";
        }
        
        return retVal;
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
