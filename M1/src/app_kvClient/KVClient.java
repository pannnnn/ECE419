package app_kvClient;

import client.KVCommInterface;
import client.KVStore;
import common.messages.KVMessage;
import logger.LogSetup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

public class KVClient implements IKVClient, ClientSocketListener {
    private static Logger logger =  Logger.getRootLogger();;
    private static final String PROMPT = "B9Client> ";
    private BufferedReader stdin;
    private KVStore kvStore = null;
    private boolean stop = false;

    private String serverAddress;
    private int serverPort;


    public void run() {
        while(!stop) {
            stdin = new BufferedReader(new InputStreamReader(System.in));
            System.out.print(PROMPT);

            try {
                String cmdLine = stdin.readLine();
                this.handleCommand(cmdLine);
            } catch (IOException e) {
                stop = true;
                printError("CLI does not respond - Application terminated ");
            }
        }
    }

    public void handleCommand(String cmdLine) {
        String[] tokens = cmdLine.split("\\s+");

        if (tokens[0].equals("connect")){
            if(tokens.length == 3) {
                try{
                    serverAddress = tokens[1];
                    serverPort = Integer.parseInt(tokens[2]);
                    newConnection(serverAddress, serverPort);
//                    kvStore.addListener(this);
//                    kvStore.connect();
                } catch(NumberFormatException nfe) {
                    printError("No valid address. Port must be a number!");
                    logger.info("Unable to parse argument <port>", nfe);
                } catch (UnknownHostException e) {
                    printError("Unknown Host!");
                    logger.info("Unknown Host!", e);
                } catch (IOException e) {
                    printError("Could not establish connection!");
                    logger.warn("Could not establish connection!", e);
                }
            } else {
                printError("Usage: connect <address> <port>");
            }

        } else if(tokens[0].equals("disconnect")) {
            disconnect();

        } else  if (tokens[0].equals("put")) {
            if(tokens.length == 1) {
                printError("Usage: put <key> <value>");
            } else {
                if (kvStore != null) {
                    StringBuilder msg = new StringBuilder();
                    for (int i = 2; i < tokens.length; i++) {
                        msg.append(tokens[i]);
                        if (i != tokens.length - 1) {
                            msg.append(" ");
                        }
                    }
                    put(tokens[1], msg.toString());
                } else {
                    printError("Not connected!");
                }
            }

        } else  if (tokens[0].equals("get")) {
            if(tokens.length != 2) {
                printError("Usage: get <key>");
            } else {
                if (kvStore != null) {
                    get(tokens[1]);
                } else {
                    printError("Not connected!");
                }
            }

        } else if(tokens[0].equals("logLevel")) {
            if(tokens.length == 2) {
                String level = setLevel(tokens[1]);
                if(level.equals(LogSetup.UNKNOWN_LEVEL)) {
                    printError("No valid log level!");
                    printPossibleLogLevels();
                } else {
                    System.out.println(PROMPT +
                            "Log level changed to level " + level);
                }
            } else {
                printError("Invalid number of parameters!");
            }

        } else if(tokens[0].equals("help")) {
            printHelp();

        } else if(tokens[0].equals("quit")) {
            stop = true;
            disconnect();
            System.out.println(PROMPT + "Application exit!");

        } else {
            printError("Unknown command");
            printHelp();
        }
    }

    private void put(String key, String value){
        try {
            KVMessage res = kvStore.put(key, value);
            printResponse(res);
        } catch (IOException e) {
            printError("Unable to put <key> <value>!");
            disconnect();
        }
    }

    private void get(String msg){
        try {
            KVMessage res =  kvStore.get(msg);
            printResponse(res);
        } catch (IOException e) {
            printError("Unable to get <key>!");
            disconnect();
        }
    }

    private void disconnect() {
        if(kvStore != null) {
            kvStore.disconnect();
            kvStore = null;
        }
    }

    private void printResponse(KVMessage res) {
        System.out.println("Status: " + res.getStatus());
        System.out.println("Key: " + res.getKey());
        System.out.println("Value: " + res.getValue());
    }

    private void printHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append(PROMPT).append("B9 CLIENT HELP (Usage):\n");
        sb.append(PROMPT);
        sb.append("::::::::::::::::::::::::::::::::");
        sb.append("::::::::::::::::::::::::::::::::\n");
        sb.append(PROMPT).append("connect <host> <port>");
        sb.append("\t establishes a connection to a server\n");
        sb.append(PROMPT).append("put <key> <value>");
        sb.append("\t\t put <key> <value> pair to the server \n");
        sb.append(PROMPT).append("get <key>");
        sb.append("\t\t get the <value> of <key> from the server \n");
        sb.append(PROMPT).append("disconnect");
        sb.append("\t\t\t disconnects from the server \n");

        sb.append(PROMPT).append("logLevel");
        sb.append("\t\t\t changes the logLevel \n");
        sb.append(PROMPT).append("\t\t\t\t ");
        sb.append("ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF \n");

        sb.append(PROMPT).append("quit ");
        sb.append("\t\t\t exits the program");
        System.out.println(sb.toString());
    }

    private void printPossibleLogLevels() {
        System.out.println(PROMPT
                + "Possible log levels are:");
        System.out.println(PROMPT
                + "ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF");
    }

    private String setLevel(String levelString) {

        if(levelString.equals(Level.ALL.toString())) {
            logger.setLevel(Level.ALL);
            return Level.ALL.toString();
        } else if(levelString.equals(Level.DEBUG.toString())) {
            logger.setLevel(Level.DEBUG);
            return Level.DEBUG.toString();
        } else if(levelString.equals(Level.INFO.toString())) {
            logger.setLevel(Level.INFO);
            return Level.INFO.toString();
        } else if(levelString.equals(Level.WARN.toString())) {
            logger.setLevel(Level.WARN);
            return Level.WARN.toString();
        } else if(levelString.equals(Level.ERROR.toString())) {
            logger.setLevel(Level.ERROR);
            return Level.ERROR.toString();
        } else if(levelString.equals(Level.FATAL.toString())) {
            logger.setLevel(Level.FATAL);
            return Level.FATAL.toString();
        } else if(levelString.equals(Level.OFF.toString())) {
            logger.setLevel(Level.OFF);
            return Level.OFF.toString();
        } else {
            return LogSetup.UNKNOWN_LEVEL;
        }
    }

    @Override
    public void handleNewMessage(String msg) {
        if(!stop) {
            System.out.println(msg);
        }
    }

    @Override
    public void handleNewMessage(KVMessage msg) {
        if(!stop) {
            System.out.println(msg.getStatus());
            System.out.println(msg.getKey());
            System.out.println(msg.getValue());
        }
    }

    @Override
    public void handleStatus(SocketStatus status) {
        if(status == SocketStatus.CONNECTED) {

        } else if (status == SocketStatus.DISCONNECTED) {
            System.out.print(PROMPT);
            System.out.println("Connection terminated: "
                    + serverAddress + " / " + serverPort);

        } else if (status == SocketStatus.CONNECTION_LOST) {
            System.out.println("Connection lost: "
                    + serverAddress + " / " + serverPort);
            System.out.print(PROMPT);
        }

    }

    private void printError(String error){
        System.out.println(PROMPT + "Error! " +  error);
    }


    @Override
    public void newConnection(String hostname, int port)
            throws UnknownHostException, IOException {
        // TODO Auto-generated method stub
        kvStore =  new KVStore(hostname, port);
        kvStore.addListener(this);
        kvStore.connect();
//        kvStore.start();
    }

    @Override
    public KVCommInterface getStore(){
        // TODO Auto-generated method stub
        return kvStore;
    }

    /**
     * Main entry point for the echo server application.
     * @param args contains the port number at args[0].
     */
    public static void main(String[] args) {
        try {
            new LogSetup("logs/client.log", Level.ALL);
        KVClient app = new KVClient();
        app.run();
        } catch (IOException e) {
            System.out.println("Error! Unable to initialize logger!");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
