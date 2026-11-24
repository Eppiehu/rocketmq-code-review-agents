package edu.homework.rmqai.common.model;

import java.util.ArrayList;
import java.util.List;

/** Supervisor 收齐三个维度的结果后, 发给 SummaryAgent 的汇总请求。 */
public class SummaryRequest {
    private String sessionId;
    private String source;
    private int codeLines;
    private long dispatchTime;
    private List<ReviewResult> results = new ArrayList<>();

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getCodeLines() { return codeLines; }
    public void setCodeLines(int codeLines) { this.codeLines = codeLines; }
    public long getDispatchTime() { return dispatchTime; }
    public void setDispatchTime(long dispatchTime) { this.dispatchTime = dispatchTime; }
    public List<ReviewResult> getResults() { return results; }
    public void setResults(List<ReviewResult> results) { this.results = results; }
}
