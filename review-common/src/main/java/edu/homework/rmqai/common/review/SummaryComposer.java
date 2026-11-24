package edu.homework.rmqai.common.review;

import edu.homework.rmqai.common.model.Finding;
import edu.homework.rmqai.common.model.ReviewResult;
import edu.homework.rmqai.common.model.Severity;
import edu.homework.rmqai.common.model.SummaryRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SummaryAgent: 汇总各维度结果, 生成结构化 Review 报告 (Markdown)。 */
public class SummaryComposer {
    private static final List<String> ORDER = List.of("StyleAgent", "LogicAgent", "SecurityAgent");

    public String compose(SummaryRequest req) {
        List<Finding> all = new ArrayList<>();
        Map<String, ReviewResult> byAgent = new LinkedHashMap<>();
        req.getResults().stream()
            .sorted(Comparator.comparingInt(r -> ORDER.indexOf(r.getAgent())))
            .forEach(r -> {
                byAgent.put(r.getAgent(), r);
                all.addAll(r.getFindings());
            });

        Map<Severity, Integer> total = new EnumMap<>(Severity.class);
        int score = 100;
        for (Finding f : all) {
            total.merge(f.getSeverity(), 1, Integer::sum);
            score -= f.getSeverity().penalty();
        }
        score = Math.max(score, 0);
        int high = total.getOrDefault(Severity.HIGH, 0);
        long securityHigh = all.stream().filter(f -> "SecurityAgent".equals(f.getAgent()) && f.getSeverity() == Severity.HIGH).count();
        String verdict;
        if (securityHigh > 0) {
            verdict = "❌ 拒绝合并：存在高危安全问题，必须先修复";
        } else if (high > 0) {
            verdict = "⚠️ 需要修改：存在高严重度逻辑问题";
        } else if (!all.isEmpty()) {
            verdict = "✅ 可以合并：建议顺手修复中低优先级问题";
        } else {
            verdict = "✅ 可以合并：未发现问题";
        }

        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS");
        StringBuilder sb = new StringBuilder();
        sb.append("# 代码审查报告\n\n");
        sb.append("| 项目 | 内容 |\n|---|---|\n");
        sb.append("| 会话 ID (LiteTopic) | `").append(req.getSessionId()).append("` |\n");
        sb.append("| 审查对象 | ").append(req.getSource()).append("（").append(req.getCodeLines()).append(" 行） |\n");
        sb.append("| 生成时间 | ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append(" |\n");
        sb.append("| 综合评分 | **").append(score).append(" / 100** |\n");
        sb.append("| 结论 | ").append(verdict).append(" |\n\n");

        sb.append("## 各 Agent 审查概况\n\n");
        sb.append("| Agent | 维度 | 高 | 中 | 低 | 收到任务 | 完成 | 耗时 |\n|---|---|---|---|---|---|---|---|\n");
        for (ReviewResult r : byAgent.values()) {
            Map<Severity, Integer> c = new EnumMap<>(Severity.class);
            r.getFindings().forEach(f -> c.merge(f.getSeverity(), 1, Integer::sum));
            sb.append("| ").append(r.getAgent()).append(" | ").append(r.getDimension())
                .append(" | ").append(c.getOrDefault(Severity.HIGH, 0))
                .append(" | ").append(c.getOrDefault(Severity.MEDIUM, 0))
                .append(" | ").append(c.getOrDefault(Severity.LOW, 0))
                .append(" | ").append(fmt.format(new Date(r.getReceivedTime())))
                .append(" | ").append(fmt.format(new Date(r.getFinishedTime())))
                .append(" | ").append(r.getFinishedTime() - r.getReceivedTime()).append(" ms |\n");
        }
        sb.append("| **合计** | | **").append(high).append("** | **").append(total.getOrDefault(Severity.MEDIUM, 0))
            .append("** | **").append(total.getOrDefault(Severity.LOW, 0)).append("** | | | |\n\n");

        for (Severity s : Severity.values()) {
            List<Finding> list = all.stream().filter(f -> f.getSeverity() == s)
                .sorted(Comparator.comparingInt(Finding::getLine)).toList();
            if (list.isEmpty()) {
                continue;
            }
            sb.append("## ").append(s.label()).append("优先级问题（").append(list.size()).append("）\n\n");
            sb.append("| 行 | Agent | 规则 | 问题 | 建议 |\n|---|---|---|---|---|\n");
            for (Finding f : list) {
                sb.append("| ").append(f.getLine()).append(" | ").append(f.getAgent().replace("Agent", ""))
                    .append(" | ").append(f.getRule()).append(" | ").append(escape(f.getMessage()))
                    .append(" | ").append(escape(f.getSuggestion())).append(" |\n");
            }
            sb.append('\n');
        }

        // 同一行被多个 Agent 同时指出, 通常是最值得优先处理的位置
        Map<Integer, List<String>> hot = new LinkedHashMap<>();
        all.stream().sorted(Comparator.comparingInt(Finding::getLine))
            .forEach(f -> hot.computeIfAbsent(f.getLine(), k -> new ArrayList<>()).add(f.getAgent()));
        List<String> multi = new ArrayList<>();
        hot.forEach((line, agents) -> {
            if (agents.stream().distinct().count() > 1) {
                multi.add("第 " + line + " 行（" + String.join("、", agents.stream().distinct().toList()) + "）");
            }
        });
        if (!multi.isEmpty()) {
            sb.append("## 交叉命中\n\n多个 Agent 同时指出的位置，建议优先处理：").append(String.join("；", multi)).append("\n");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("|", "\\|");
    }
}
