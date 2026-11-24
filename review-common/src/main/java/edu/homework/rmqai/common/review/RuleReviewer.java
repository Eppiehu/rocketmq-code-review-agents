package edu.homework.rmqai.common.review;

import edu.homework.rmqai.common.model.Finding;
import edu.homework.rmqai.common.model.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于规则的审查器基类。规则分两种:
 * <ul>
 *   <li>行规则: 对每一行 (跳过注释行) 做正则匹配</li>
 *   <li>全文规则: 对整段代码做跨行正则匹配, 再换算成行号</li>
 * </ul>
 * 同一行同一规则只报告一次。
 */
public abstract class RuleReviewer {

    protected interface Rule {
        void apply(String[] lines, String code, List<Finding> out);
    }

    private final List<Rule> rules = new ArrayList<>();

    /** Agent 名称, 写入每条 Finding */
    public abstract String agentName();

    /** 审查维度 */
    public abstract String dimension();

    protected void lineRule(String id, Severity severity, String regex, String message, String suggestion) {
        lineRule(id, severity, Pattern.compile(regex), line -> true, message, suggestion);
    }

    protected void lineRule(String id, Severity severity, Pattern pattern, Predicate<String> extra, String message, String suggestion) {
        rules.add((lines, code, out) -> {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (isComment(line)) {
                    continue;
                }
                if (pattern.matcher(line).find() && extra.test(line)) {
                    out.add(finding(severity, i + 1, id, message, suggestion, line));
                }
            }
        });
    }

    protected void textRule(String id, Severity severity, String regex, String message, String suggestion) {
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        rules.add((lines, code, out) -> {
            Matcher m = pattern.matcher(code);
            while (m.find()) {
                int line = lineOf(code, m.start());
                out.add(finding(severity, line, id, message, suggestion, lines[line - 1]));
            }
        });
    }

    protected void customRule(Rule rule) {
        rules.add(rule);
    }

    protected Finding finding(Severity severity, int line, String rule, String message, String suggestion, String snippet) {
        String s = snippet == null ? "" : snippet.trim();
        if (s.length() > 100) {
            s = s.substring(0, 97) + "...";
        }
        return new Finding(agentName(), severity, line, rule, message, suggestion, s);
    }

    public List<Finding> review(String code) {
        String normalized = code == null ? "" : code.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<Finding> raw = new ArrayList<>();
        for (Rule rule : rules) {
            rule.apply(lines, normalized, raw);
        }
        // 同一行同一规则去重
        Set<String> seen = new LinkedHashSet<>();
        List<Finding> result = new ArrayList<>();
        for (Finding f : raw) {
            if (seen.add(f.getRule() + "@" + f.getLine())) {
                result.add(f);
            }
        }
        result.sort(Comparator.comparingInt(Finding::getLine).thenComparing(Finding::getRule));
        return result;
    }

    protected static boolean isComment(String line) {
        String t = line.trim();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*");
    }

    protected static int lineOf(String code, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
