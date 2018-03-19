package ecs;

import com.google.gson.Gson;
import common.messages.MetaData;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class ECS {
    private static Logger logger = Logger.getRootLogger();
    //private static final String SCRIPT_TEXT = "ssh -n %s nohup java -jar /m2-server.jar %s %s %s %s %s %s &";
    private static final String SCRIPT_TEXT = "ssh -n %s nohup java -jar m2-server.jar %s %s %s %s %s %s &";

    private Gson gson;
    private ZooKeeperWatcher zkWatch;
    private MetaData metaData;

    // Zookeeper specific
    private static final int SESSION_TIMEOUT = 10000;
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String CONNECTION_ADDR = "127.0.0.1:2181";
    private static final String CONNECTION_ADDR_HOST = "127.0.0.1";
    private static final String CONNECTION_ADDR_PORT = "2181";
    private static final String ROOT_PATH = "/ecs";
    private static final String NODE_PATH_SUFFIX = "/ecs/";

    /**
     * if the service is made up of any servers
     **/
//    private boolean running = false;
    public ECS(String configFileName) {
        gson = new Gson();
        metaData = new MetaData(configFileName);
        initZookeeper();
        // prevent it from printing heartbeat message
        Logger.getLogger("org.apache.zookeeper").setLevel(Level.ERROR);
    }

    private void initZookeeper() {
        zkWatch = new ZooKeeperWatcher();
        zkWatch.createConnection(CONNECTION_ADDR, SESSION_TIMEOUT);
        zkWatch.createPath(ROOT_PATH, "");
        zkWatch.watchChildren();
    }

    public void startAllNodes() {
        for (IECSNode node : metaData.getMetaData()){
            sendMetedata(node);
        }
    }

    public void sendMetedata(IECSNode node) {
        logger.info("Sending latest metadata to " + node.getNodeName());
        String json = new Gson().toJson(metaData.getMetaData());
        zkWatch.writeData(NODE_PATH_SUFFIX + node.getNodeName(), json);
    }


    public void executeScript(ECSNode node) {
        zkWatch.clearNode(NODE_PATH_SUFFIX + node.getNodeName());

        String script = String.format(SCRIPT_TEXT, LOCAL_HOST, node.getNodeName(), CONNECTION_ADDR_HOST,
                CONNECTION_ADDR_PORT, node.getNodePort(), node.getCacheStrategy(), node.getCachesize());
        Process proc;
        Runtime run = Runtime.getRuntime();
        try {
            logger.info("Running ... " + script);
            proc = run.exec(script);
        } catch (IOException e) {
            logger.error("Failed to execute script!");
        }
    }

    public boolean removeNodes(Collection<String> nodeNames) {
        return metaData.removeNodes(nodeNames);
    }


    public boolean awaitNodes(int timeout) {
        return zkWatch.awaitNodes(timeout);
    }

    public boolean stop() {
        return zkWatch.writeData(ROOT_PATH, "");
    }

    public boolean shutdown() {
        return zkWatch.deleteAllNodes(ROOT_PATH, NODE_PATH_SUFFIX, serverRepoTaken);
    }

    public void setSemaphore(int count) {
        zkWatch.setSemaphore(count);
    }

    public TreeSet<IECSNode> getNodes() {
        return metaData.getMetaData();
    }

    public void notifyPrecessor(Collection<IECSNode> serversTaken) {
        TreeSet<IECSNode> tmp = (TreeSet<IECSNode>) this.serverRepoTaken.clone();
        tmp.removeAll(serversTaken);
        Iterator itr1 = serversTaken.iterator();
        ECSNode node1 = null;
        ECSNode smallerNode;
        ECSNode largerNode;
        HashMap<String, IECSNode> map = new HashMap<>();
        while (itr1.hasNext() && tmp.size() > 0) {
            smallerNode = null;
            largerNode = null;
            node1 = (ECSNode) itr1.next();
            for (IECSNode node2 : tmp) {
                if (node1.compareTo((ECSNode) node2) <= 0) {
                    largerNode = (ECSNode) node2;
                } else {
                    smallerNode = (ECSNode) node2;
                }
                if (smallerNode == null && largerNode != null) {
                    map.put(tmp.last().getNodeName(), tmp.last());
                    break;
                } else if (smallerNode != null && largerNode != null) {
                    map.put(smallerNode.getNodeName(), smallerNode);
                } else ;
            }
            if (largerNode == null) {
                map.put(smallerNode.getNodeName(), smallerNode);
            }
        }
        for (IECSNode node : map.values()) {
            sendMetedata(node);
        }
    }

    public Collection<IECSNode> setupNodes(int count, String cacheStrategy, int cacheSize) {
        return metaData.setupNodes(count, cacheStrategy, cacheSize);
    }
}
