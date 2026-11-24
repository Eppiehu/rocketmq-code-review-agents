package edu.homework.rmqai.agent;

import java.util.Locale;

/** 当前进程扮演的 Agent 角色, 由 -DagentRole 指定。 */
public enum AgentRole {
    STYLE("StyleAgent", "代码风格与命名规范审查", "检查命名、格式、导入、日志使用等风格问题"),
    LOGIC("LogicAgent", "逻辑缺陷与边界条件审查", "检查空 catch、字符串比较、越界、资源泄漏等逻辑问题"),
    SECURITY("SecurityAgent", "安全漏洞模式审查", "识别 SQL 注入、XSS、硬编码密钥、命令注入等安全问题"),
    SUMMARY("SummaryAgent", "审查结果汇总", "汇总各维度审查结果, 生成结构化 Review 报告");

    private final String agentName;
    private final String title;
    private final String description;

    AgentRole(String agentName, String title, String description) {
        this.agentName = agentName;
        this.title = title;
        this.description = description;
    }

    public static AgentRole current() {
        String role = System.getProperty("agentRole");
        if (role == null || role.isBlank()) {
            throw new IllegalStateException("缺少启动参数 -DagentRole=style|logic|security|summary");
        }
        return valueOf(role.trim().toUpperCase(Locale.ROOT));
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String agentName() {
        return agentName;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
