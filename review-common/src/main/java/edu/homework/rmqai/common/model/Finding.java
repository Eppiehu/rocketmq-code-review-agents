package edu.homework.rmqai.common.model;

/** 一条审查发现。 */
public class Finding {
    private String agent;
    private Severity severity;
    private int line;
    private String rule;
    private String message;
    private String suggestion;
    private String snippet;

    public Finding() {
    }

    public Finding(String agent, Severity severity, int line, String rule, String message, String suggestion, String snippet) {
        this.agent = agent;
        this.severity = severity;
        this.line = line;
        this.rule = rule;
        this.message = message;
        this.suggestion = suggestion;
        this.snippet = snippet;
    }

    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}
