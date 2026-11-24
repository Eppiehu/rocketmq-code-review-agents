package edu.homework.rmqai.common.model;

/** Supervisor 发给各审查 Agent 的任务 (作为 A2A 消息正文, JSON 序列化)。 */
public class ReviewTask {
    private String sessionId;
    /** 审查维度: style / logic / security */
    private String dimension;
    /** 被审查对象: 文件名或 PR 链接 */
    private String source;
    private String code;
    private long dispatchTime;

    public ReviewTask() {
    }

    public ReviewTask(String sessionId, String dimension, String source, String code) {
        this.sessionId = sessionId;
        this.dimension = dimension;
        this.source = source;
        this.code = code;
        this.dispatchTime = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public long getDispatchTime() { return dispatchTime; }
    public void setDispatchTime(long dispatchTime) { this.dispatchTime = dispatchTime; }
}
