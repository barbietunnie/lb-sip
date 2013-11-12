import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * <H1>Telnet Server</H1>
 * <BR>
 * This class will provide a telnet service to your application.<BR>
 * It support some common telnet options and codes, like:
 * <UL>
 *  <LI>ECHO</LI>
 *  <LI>SUPPRESS GO AHEAD</LI>
 *  <LI>TERMINAL TYPE</LI>
 *  <LI>TERMINAL SPEED</LI>
 *  <LI>WINDOW SIZE</LI>
 *  <LI>...</LI>  
 * </UL>
 * However, most of the telnet codes and options are recognized, but not in any use in the code.<BR>
 * <BR>
 * This class supports setting prompt, exit and help command strings, adding commands to list for auto-complete, disconnecting client,... etc.<BR>
 * <BR>
 * Normally, once the client establish a connection, server will try to force a character mode transmission in order for
 * some special characters to work on server side, like <I>tab</I> \t, <I>backspace</I> \b and <I>question mark</I> ?.<BR>
 * <H2>Example</H2>
 * <BR>
 * This code shows how to start telnet service and listen on port 4444.
 * <PRE>
 *      TelnetServer ts = new TelnetServer(4444);
 *      Thread t = new Thread(ts, "telnetService()");
 *      t.start();
 * </PRE>
 * In order to make a real use of this class, you need to locate function:<BR>
 * <I>private String processCommand(String command)</I><BR>
 * and change line:
 * <PRE>retVal = "Command " + command + " not implemented.\r\n";</PRE>
 * into something useful. Eg. to call your real process command function.
 * <BR>
 * @author eigorde
 *
 */
public class TelnetServer implements Runnable {
    
    /**
     * Welcome string.
     */
    private final String ver = "Telnet interface v.0.2";
    
    /**
     * Exit string, built-in command.
     */
    private String commandExit = "exit";
    
    /**
     * Help string, built-in command.
     */
    private String commandHelp = "help";
    
    /**
     * Port number to listen.
     */
    private int portNumber;
    
    /**
     * Server socket to listen on port number.
     */
    private ServerSocket serverSocket;
    
    /**
     * Client socket, once when client establish 
     * telnet connection.
     */
    private Socket clientSocket;
    
    /**
     * Is client connected to server.
     */
    private boolean telnetClientConnected;
    
    /**
     * Terminate flag. This flag will
     * be set on if user of this class 
     * wants to stop telnet service.
     */
    private boolean terminateFlag;
    
    /**
     * Telnet command prompt.
     */
    private String prompt;
    
    /**
     * Command list for auto-complete and 
     * short usage printout.
     */
    private List<String> commandList;

    /**
     * Command history for arrow keys.
     * Up arrow \033[A and down arrow \033[B sequence.
     */
    private List<String> commandHistory;
    
    /**
     * Index pointer where to take next el. from command history.
     * <BR><B>Rule:</B> up on execution of new command, a command
     * is added to list and this pointer points to last item in list.<BR>
     * Arrow keys up and down change pointer value by 1.
     */
    private int commandHistoryIndexPointer;
        
    /**
     * Flag which controls console printouts for debugging.
     */
    private boolean debugFlag;
    
    /**
     * <H1>Telnet server</H1>
     * <BR>
     * This function will start a telnet service
     * on a given port number.<BR>
     * 
     * @param port
     *            desired tcp port number
     * @throws IOException
     *             Error in socket communication
     */
    public TelnetServer(int port) throws IOException {
        portNumber = port;
        terminateFlag = false;
        
        serverSocket = new ServerSocket(portNumber);
        
        debugFlag = false;
        
        prompt = "--> ";
        
        commandList = new ArrayList<String>();
        commandHistory = new ArrayList<String>();
        
        /*
         * Add first exit string, then help string.
         * Order is important, please look at setters 
         * for exit and help commands !!
         */
        commandList.add(commandExit);
        commandList.add(commandHelp);
    }
    
    @SuppressWarnings("unused")
	@Override
    public void run() {
        
        telnetClientConnected = false;
        
        try {
            /*
             * Here this function waits, for client to connect.
             */
            clientSocket = serverSocket.accept();
        } catch (SocketException e) {
            return;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
        telnetClientConnected = true;

        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            out = new BufferedOutputStream(clientSocket.getOutputStream());
            in = new BufferedInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        byte[] buff = new byte[1024];
        
        String inputLine = "";
        int inputLen = 0;
        
        /*
         * Check for Telnet control codes. Ref.
         * http://support.microsoft.com/kb/231866
         */                
        byte IAC = (byte) 0xff;
        // type of operation
        byte DO = (byte) 0xfd;
        byte DONT = (byte) 0xfe;
        byte WILL = (byte) 0xfb;
        byte WONT = (byte) 0xfc;                        
        byte SB = (byte) 0xfa;
        byte SE = (byte) 0xf0;            
        // telnet option
        byte ECHO = 0x01;
        byte SUPPRESS_GO_AHEAD = 0x03;
        byte WINDOW_SIZE = 0x1f;
        byte REMOTE_FLOW_CONTROL = 0x21;
        byte TERMINAL_SPEED = 0x20;
        byte TERMINAL_TYPE = 0x18;
        byte LINEMODE = 0x22;
        byte X_DISPLAY_LOCATION = 0x23;
        byte NEW_ENVIRONMENT = 0x27;
        
        /* ************************
         * Telnet variables.
         * ************************/
        // WINDOW_SIZE, width and height
        int width = 100, height = 50;
        // TERMINAL_SPEED, separated by , char.
        // Eg 38600,56400
        String terminalSpeed = "";
        // TERMINAL_TYPE, separated by , char.
        // Eg vt104,xterm
        String terminalType = "";
        // New environment variables. Usually empty.
        String newEnvironment = "";
        // X Display location.
        String XdispalyLocation = "";
        // Remote flow control.
        String remoteFlowControl = "";
        /* ********************** */
                        
        try {
            
            // Inform that this telnet server will echo characters back to client
            out.write(new byte[] { IAC, WILL, ECHO });
            out.flush();
            // Inform that this telnet server will suppress go ahead obsolete option
            out.write(new byte[] { IAC, WILL, SUPPRESS_GO_AHEAD });
            out.flush();
            
            // Print welcome message and prompt.
            out.write((ver + "\r\n").getBytes());
            out.flush();
            
            out.write((prompt).getBytes());
            out.flush();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        try {
            while ((inputLen = in.read(buff)) > 0) {                
                
                /*
                 * Index, from where to build command. 
                 */
                int idx = 0;
                while (buff[idx] == IAC) {
                                  
                    /* Flags, according to which
                     * telnet service should respond:
                     * - for DO request, WILL replay
                     * - for WILL, SB replay if needed
                     * - for SB, no replay is needed
                     * - WONT and DONT are not needed
                     */ 
                    boolean doFlag = false;
                    boolean willFlag = false;
                    boolean sbFlag = false;
                    
                    if (buff[idx + 1] == DO) {
                        doFlag = true;
                    }
                    else if (buff[idx + 1] == DONT) {

                    }
                    else if (buff[idx + 1] == WILL) {
                        willFlag = true;
                    }
                    else if (buff[idx + 1] == WONT) {

                    }
                    else if (buff[idx + 1] == SB) {
                        sbFlag = true;
                    }
                    else {
                        if (debugFlag)                        
                            System.out.print("Unknown type operation: " + buff[idx + 1] + " ");
                    }
                    
                    if (buff[idx + 2] == ECHO ||
                            buff[idx + 2] == SUPPRESS_GO_AHEAD) {                        
                        if (doFlag) {
                            // Replay with WILL on DO ECHO and DO SUPPRESS_GO_AHEAD requests
                            if (debugFlag)                        
                                System.out.println("DO " + translateTelnetOption(buff[idx + 2]));
                            out.write(new byte[] { IAC, WILL, buff[idx + 2] });
                            out.flush();
                        }
                        else if (willFlag) {
                            // Replay with DO on WILL ECHO and WILL SUPPRESS_GO_AHEAD messages
                            if (debugFlag)                        
                                System.out.println("WILL " + translateTelnetOption(buff[idx + 2]));
                            out.write(new byte[] { IAC, DO, buff[idx + 2] });
                            out.flush();
                        }
                        else {
                            if (debugFlag)                        
                                System.out.println("Unsupported type operation: " + buff[idx + 1] + " for " + translateTelnetOption(buff[idx + 2]));
                        }
                    }
                    else if (buff[idx + 2] == WINDOW_SIZE) {
                        // Stay ready to accept new values for WINDOW_SIZE or request a WINDOW_SIZE values
                        if (sbFlag) {
                            width = (buff[idx + 3] << 8) + buff[idx + 4];
                            height = (buff[idx + 5] << 8) + buff[idx + 6];  
                            idx = idx + 6;
                        }
                        else if (willFlag) {
                            out.write(new byte[] { IAC, DO, WINDOW_SIZE });
                            out.flush();
                            byte[] newBuff = new byte[9];
                            in.read(newBuff);
                            width = (newBuff[3] << 8) + newBuff[4];
                            height = (newBuff[5] << 8) + newBuff[6];                
                        }
                        else {
                            if (debugFlag)                        
                                System.out.println("Unsupported type operation: " + buff[idx + 1] + " for " + translateTelnetOption(buff[idx + 2]));
                        }                        
                    }
                    else if ( buff[idx + 2] == REMOTE_FLOW_CONTROL ||
                            buff[idx + 2] == TERMINAL_SPEED ||
                            buff[idx + 2] == TERMINAL_TYPE ||
                            buff[idx + 2] == X_DISPLAY_LOCATION ||
                            buff[idx + 2] == NEW_ENVIRONMENT ||
                            buff[idx + 2] == LINEMODE) {

                        // Here we extract values from usual telnet options, like terminal type, speed ... etc. 
                        if (willFlag) {
                            out.write(new byte[] { IAC, SB, buff[idx + 2], 1, IAC, SE });
                            out.flush();
                            byte[] newBuff = new byte[255];
                            in.read(newBuff);
                            int len = 0, offset = 4;
                            for (int i = offset; i < newBuff.length; i++) {
                                if (newBuff[i] == IAC) {
                                    break;
                                }
                                len++;
                            }
                            // Do nothing special, just print it.
                            String value = new String(newBuff, offset, len);
                            if (debugFlag)                        
                                System.out.println(translateTelnetOption(buff[idx + 2]) + " = " + value);
                        }
                        else {
                            if (debugFlag)                        
                                System.out.println("Unsupported type operation: " + buff[idx + 1] + " for " + translateTelnetOption(buff[idx + 2]));
                        }
                    }     
                    else {
                        if (debugFlag)                        
                            System.out.print("Unknown telnet option: " + translateTelnetOption(buff[idx + 2]));
                    }
                    
                    idx = idx + 3;
                    
                    
                }
                
                /*
                 * The rest should be command string, if any byte left in buffer.
                 */
                if (idx < inputLen) {
                    /*
                     * Translate byte buffer into string.
                     */
                    String newInput = new String(buff, idx, inputLen - idx);
                    inputLine = inputLine + newInput;

                    /* ******************
                     *  Process command.
                     * ****************** */

                    // Ending, if input line ends with LF or CRLF. 
                    int ending = 0;
                    // Check for CR/LF ending. It means command confirmation.
                    if (inputLine.endsWith("\r\n") || inputLine.endsWith("\r\000")) {
                        ending = 2;
                    } else if (inputLine.endsWith("\n")) {
                        ending = 1;
                    }
                        
                    if (ending > 0) {
                        inputLine = removeChars(inputLine, ending);
                        /*
                         * Recognize and process command.
                         */
                        String retVal = processCommand(inputLine);
                        out.write(("\r\n" + retVal).getBytes());
                        /*
                         * Print prompt.
                         */
                        out.write(("\r\n" + prompt).getBytes());
                        /*
                         * Store it in command history.
                         */
                        if (inputLine.trim().length() > 0) {
                            // put only non-empty non-existing lines in history
                            if (!commandHistory.contains(inputLine)) {
                                commandHistory.add(inputLine);
                                commandHistoryIndexPointer = commandHistory.size() - 1;
                            }
                        }
                        
                        /*
                         * Clear input line for new command.
                         */
                        inputLine = "";

                        if (retVal.equalsIgnoreCase(commandExit)) {
                            /*
                             * Terminate while() loop. This will close sockets
                             * and start telnet interface again. Look after end
                             * of while() loop.
                             */
                            break;
                        }
                        
                    }
                    else if (inputLine.endsWith("\t") || inputLine.endsWith("?") ) {
                        inputLine = removeChars(inputLine, 1);
                        String[] possibleCommands = autoComplete(inputLine);
                        if (possibleCommands.length == 0) {
                            out.write(("\r\nNothing to complete available.\r\n" + prompt + inputLine).getBytes());
                        }
                        else if (possibleCommands.length == 1) {
                            out.write((possibleCommands[0].substring(inputLine.length() )).getBytes());
                            inputLine = possibleCommands[0];
                        }
                        else {
                            if (inputLine.length() == 0) {
                                out.write(("\r\n" + usage() + "\r\n" + prompt).getBytes());
                            }
                            else {
                                for (String item : possibleCommands) {
                                    out.write(("\r\n" + item).getBytes());
                                }
                                String prefix = findPrefix(possibleCommands);
                                if (prefix.length() > 0) {
                                    if (prefix.length() > inputLine.length()) {                                    
                                        inputLine = inputLine + prefix.substring(inputLine.length());
                                    }
                                }
                                out.write(("\r\n" + prompt + inputLine).getBytes());
                            }
                        }
                    }
                    else if (inputLine.endsWith("\b") ||
                            inputLine.endsWith(new String(new byte[] {127}))) {
                        
                        inputLine = removeChars(inputLine, 2);
                        //out.write(("\r\n" + inputLine).getBytes());
                        // Esc. sequence for backspace
                        out.write(("\b\033[K").getBytes());
                    }
                    else if (inputLine.endsWith("\033[A") ||
                            inputLine.endsWith("\033[B")) {

                        if (commandHistory.size() > 0) {
                            // Up arrow key: \033[A
                            // Down arrow key: \033[B

                            if (inputLine.endsWith("\033[A")) {
                                String historyCommand = commandHistory.get(commandHistoryIndexPointer);
                                inputLine = historyCommand;
                                out.write(("\033[1K\r" + prompt + historyCommand).getBytes());
                                commandHistoryIndexPointer--;
                                if (commandHistoryIndexPointer < 0) {
                                    commandHistoryIndexPointer = commandHistory.size() - 1;
                                }
                            }
                            else if (inputLine.endsWith("\033[B")) {
                                String historyCommand = commandHistory.get(commandHistoryIndexPointer);
                                inputLine = historyCommand;
                                out.write(("\033[1K\r" + prompt + historyCommand).getBytes());
                                commandHistoryIndexPointer++;
                                if (commandHistoryIndexPointer >= commandHistory.size()) {
                                    commandHistoryIndexPointer = 0;
                                }
                            }
                        }
                    }
                    else {
                        // Echo new input char(s).
                        out.write(newInput.getBytes());
                    }
                    
                    out.flush();
                }
                
            }
        } catch (SocketException e) {
            telnetClientConnected = false;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        /*
         * If while() loop breaks, this will close all. No more telnet
         * connection.
         */
        try {
            out.close();
            in.close();
            clientSocket.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        /*
         * Call again itself, to start telnet interface. This usually happens,
         * if a telnet client violates protocol, and terminates without byebye
         * message (exit or quit commands). Or if user sends quit or exit
         * commands, while() loop breaks and execution continues here.
         * 
         * This will work only if user of this class didn't yet decide to 
         * terminate telnet service.
         */
        if (!terminateFlag)        
            run();
        
    }
    
    /**
     * <H1>Translate telnet option code</H1>
     * <BR>
     * Translate telnet option code to meaningful string.
     * <BR>
     * Ref.<BR>
     * <A HREF="http://pcmicro.com/netfoss/telnet.html">
     * http://pcmicro.com/netfoss/telnet.html</A>
     * @param option telnet option code, eg. ECHO
     * @return string, eg. <I>"ECHO"</I>, or hex value if the code is not in list
     */
    private String translateTelnetOption(byte option) {
        String retVal = Integer.toHexString(option);
        
        // Telnet options table
        byte ECHO = 0x01;
        byte SUPPRESS_GO_AHEAD = 0x03;
        byte WINDOW_SIZE = 0x1f;
        byte REMOTE_FLOW_CONTROL = 0x21;
        byte TERMINAL_SPEED = 0x20;
        byte TERMINAL_TYPE = 0x18;
        byte LINEMODE = 0x22;
        byte X_DISPLAY_LOCATION = 0x23;
        byte NEW_ENVIRONMENT = 0x27;
        
        if (option == ECHO) {
            retVal = "ECHO";
        }
        else if (option == SUPPRESS_GO_AHEAD) {
            retVal = "SUPPRESS_GO_AHEAD";
        }
        else if (option == WINDOW_SIZE) {
            retVal = "WINDOW_SIZE";
        }
        else if (option == REMOTE_FLOW_CONTROL) {
            retVal = "REMOTE_FLOW_CONTROL";
        }
        else if (option == TERMINAL_SPEED) {
            retVal = "TERMINAL_SPEED";
        }
        else if (option == TERMINAL_TYPE) {
            retVal = "TERMINAL_TYPE";
        }
        else if (option == LINEMODE) {
            retVal = "LINEMODE";
        }
        else if (option == X_DISPLAY_LOCATION) {
            retVal = "X_DISPLAY_LOCATION";
        }
        else if (option == NEW_ENVIRONMENT) {
            retVal = "NEW_ENVIRONMENT";
        }
        
        return retVal;
    }

    /**
     * Function prototype for processing commands.
     * @param command command name
     * @return command result or printout
     */
    private String processCommand(String command) {
    
        String retVal = "";
    
        if (command.trim().startsWith("ver")) {
            retVal = ver + "\r\n";
        } else if (command.trim().startsWith(commandHelp)) {
            retVal = usage();
        } else if (command.trim().startsWith(commandExit)) {
            retVal = commandExit;        
        }
        else {
            retVal = LoadBalancer.telnetCommand(command);
        }
        
        return retVal;
    }
    
    /**
     * Add new command to list.
     * @param commandName name of the command to add
     */
    public void addCommand(String commandName) {
        commandList.add(commandName);
    }
    
    /**
     * Find first several characters in list of strings,
     * which are same for each item in list.<BR>
     * Eg. If you have a list:<BR>
     * <I>showBuddy</I><BR>
     * <I>showFriend</I><BR>
     * <I>showFoo</I><BR>
     * should result in return string <I>show</I>.<BR>
     * @param list a list of strings where to search, don't use <I>null</I> value
     * @return common prefix, if list has only 1 item, then it's value is returned by this function 
     */
    private String findPrefix(String[] list) {
        String retVal = "";
        if (list != null) {
            if (list.length == 1) {
                retVal = list[0];
            }
            else {
                // find shortest item first
                int shortest = 0;
                int idx = 0;
                for (String item : list) {
                    if (item.length() < list[shortest].length()) {
                        shortest = idx;
                    }
                    idx++;
                }
                
                // char by char search and compare
                for (int col = 0; col < list[shortest].length(); col++) {
                    String letter = list[shortest].substring(col, col + 1);
                    boolean common = true;
                    for (String item : list) {
                        if ( !item.startsWith(retVal + letter)) {
                            common = false;
                            break;
                        }
                    }
                    if (common) {
                        retVal = retVal + letter;
                    }
                    else {
                        break;
                    }
                }
            }
        }
        return retVal;
    }
    
    /**
     * Auto complete command, and return a list of possible commands that match.
     * @param command uncompleted command
     * @return a list of commands that match a list
     */
    private String[] autoComplete(String command) {
        int count = 0;
        for (String item : commandList) {
            if (item.startsWith(command)) {
                count++;
            }
        }
        String[] retVal = new String[count];
        
        count = 0;
        for (String item : commandList) {
            if (item.startsWith(command)) {
                retVal[count] = item;
                count++;
            }
        }              
        
        return retVal;
                
    }
    
    /**
     * Make a list of commands.
     * @return list of commands separated by CRLF
     */
    private String usage() {
        String retVal = "Usage:\r\n";
        for (String item : commandList) {
            retVal = retVal + item + "\r\n";
        } 
        return retVal;
    }
    
    /**
     * Remove last character(s) from string.
     * @param str string variable, don't use <I>null</I> value
     * @param count how many characters to remove
     * @return resulting string, or empty string ""
     */
    private String removeChars(String str, int count) {
        int endPos = str.length() - count;
        if (endPos < 0) {
            endPos = 0;
        }
        return str.substring(0, endPos);
    }
    
    /**
     * Terminate telnet service. First make sure no client is connected.
     * @throws IOException
     */
    public void terminate() throws IOException {
        terminateFlag = true;
        serverSocket.close();
    }
    
    /**
     * Disconnect client, and make telnet service free again.
     * @throws IOException
     */
    public void disconnect() throws IOException {
        terminateFlag = false;
        if (isTelnetClientConnected()) {
            telnetClientConnected = false;
            clientSocket.close();
        }
    }
    /**
     * <H1>Debug printouts</H1>
     * <BR>
     * Turn on or off printing of debug messages, mainly for telnet op.codes.
     * @param debugFlag <I>true</I> for debugging, or <I>false</I> when you don't
     * want to fill console with messages from this telnet implementation
     */
    public void setDebug(boolean debugFlag) {                        
        this.debugFlag = debugFlag;
    }
    
    /**
     * <H1>Is telnet client connected</H1>
     * <BR>
     * Check if telnet service has already a connected client.
     * @return the telnetClientConnected, <I>true</I> if client is connected
     */
    public boolean isTelnetClientConnected() {
        return telnetClientConnected;
    }

    /**
     * <H1>Telnet port number</H1>
     * <BR>
     * Get telnet service listening port number.
     * @return the port number on which telnet service listens 
     */
    public int getPortNumber() {
        return portNumber;
    }

    /**
     * <H1>Prompt</H1>
     * <BR>
     * Get current prompt value
     * @return the prompt string
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * <H1>Prompt</H1>
     * <BR>
     * Set prompt value
     * @param prompt the prompt to set, eg. <I>prompt></I>
     */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /**
     * <H1>Exit command</H1>
     * <BR>
     * This will set the keyword for exit command. Exit command
     * is built-in command and should be recognized by telnet service.
     * @return the command exit keyword
     */
    public String getCommandExit() {
        return commandExit;
    }

    /**
     * <H1>Exit command</H1>
     * <BR>
     * This will set the keyword for exit command. Exit command
     * is built-in command and should be recognized by telnet service.
     * @param commandExit the command keyword for Exit. Eg. <I>quit</I>
     */
    public void setCommandExit(String commandExit) {
        this.commandExit = commandExit;
        commandList.set(0, commandExit);
    }

    /**
     * <H1>Help command</H1>
     * <BR>
     * This will get the keyword for help command. Help command
     * is built-in command and should be recognized by telnet service.
     * @return the command keyword for help
     */
    public String getCommandHelp() {
        return commandHelp;
    }

    /**
     * <H1>Help command</H1>
     * <BR>
     * This will set the keyword for help command. Help command
     * is built-in command and should be recognized by telnet service.
     * @param commandHelp the command string for Help command. Eg. <I>help</I>
     */
    public void setCommandHelp(String commandHelp) {
        this.commandHelp = commandHelp;
        commandList.set(1, commandHelp);
    }
    
}
