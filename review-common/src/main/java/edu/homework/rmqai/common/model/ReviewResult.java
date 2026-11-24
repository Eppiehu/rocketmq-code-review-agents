package edu.homework.rmqai.common.model;

import java.util.ArrayList;
import java.util.List;

/** 审查 Agent 回传给 Supervisor 的结果 (经 LiteTopic)。 */
public class ReviewResult {
    private String sessionId;
    private String agent;
    private String dimension;
    private String host;
    private long receivedTime;
    private long finishedTime;
    private List<Finding> findings = new ArrayList<>();

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public long getReceivedTime() { return receivedTime; }
    public void setReceivedTime(long receivedTime) { this.receivedTime = receivedTime; }
    public long getFinishedTime() { return finishedTime; }
    public void setFinishedTime(long finishedTime) { this.finishedTime = finishedTime; }
    public List<Finding> getFindings() { return findings; }
    public void setFindings(List<Finding> findings) { this.findings = findings; }
}
