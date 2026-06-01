package com.etl.engine.callback;

public class LocalNodeInfo {

    private final String nodeId;
    private final String nodeIp;
    private final int serverPort;

    public LocalNodeInfo(String nodeId, String nodeIp, int serverPort) {
        this.nodeId = nodeId;
        this.nodeIp = nodeIp;
        this.serverPort = serverPort;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeIp() {
        return nodeIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public boolean isLocalNode(String nodeIp, int port) {
        return this.nodeIp.equals(nodeIp) && this.serverPort == port;
    }

    public boolean isLocalNode(String nodeIp, Integer port) {
        return port != null && this.nodeIp.equals(nodeIp) && this.serverPort == port;
    }

    public String buildInternalCallbackUrl(String callbackId, Integer port) {
        int targetPort = port != null ? port : this.serverPort;
        return "http://" + this.nodeIp + ":" + targetPort + "/callback/internal/" + callbackId;
    }
}
