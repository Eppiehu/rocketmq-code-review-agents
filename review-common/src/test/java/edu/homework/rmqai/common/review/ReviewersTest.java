package edu.homework.rmqai.common.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.homework.rmqai.common.Naming;
import edu.homework.rmqai.common.model.Finding;
import edu.homework.rmqai.common.model.ReviewResult;
import edu.homework.rmqai.common.model.SummaryRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReviewersTest {

    private static final String CLEAN = String.join("\n",
        "package com.example;",
        "",
        "import java.util.Objects;",
        "",
        "public class OrderService {",
        "    private static final int MAX_RETRY = 3;",
        "",
        "    public boolean isPaid(String status) {",
        "        return \"PAID\".equals(status);",
        "    }",
        "",
        "    public int sum(int[] values) {",
        "        int total = 0;",
        "        for (int i = 0; i < values.length; i++) {",
        "            total += values[i];",
        "        }",
        "        return total;",
        "    }",
        "}");

    private static String sample() throws Exception {
        return Files.readString(Path.of("..", "samples", "OrderController.java"), StandardCharsets.UTF_8);
    }

    private static Set<String> rules(List<Finding> findings) {
        return findings.stream().map(Finding::getRule).collect(Collectors.toSet());
    }

    @Test
    void cleanCodeHasNoFindings() {
        assertEquals(List.of(), new StyleReviewer().review(CLEAN).stream().map(Finding::getRule).toList());
        assertEquals(List.of(), new LogicReviewer().review(CLEAN).stream().map(Finding::getRule).toList());
        assertEquals(List.of(), new SecurityReviewer().review(CLEAN).stream().map(Finding::getRule).toList());
    }

    @Test
    void styleRulesOnSample() throws Exception {
        Set<String> r = rules(new StyleReviewer().review(sample()));
        assertTrue(r.containsAll(Set.of("STYLE-CLASS-NAME", "STYLE-METHOD-NAME", "STYLE-CONSTANT-NAME",
            "STYLE-VARIABLE-NAME", "STYLE-WILDCARD-IMPORT", "STYLE-SYSTEM-OUT", "STYLE-BRACES")), r.toString());
    }

    @Test
    void logicRulesOnSample() throws Exception {
        Set<String> r = rules(new LogicReviewer().review(sample()));
        assertTrue(r.containsAll(Set.of("LOGIC-EMPTY-CATCH", "LOGIC-STRING-EQ", "LOGIC-NPE-EQUALS",
            "LOGIC-OFF-BY-ONE", "LOGIC-RESOURCE-LEAK", "LOGIC-INT-DIVISION")), r.toString());
    }

    @Test
    void securityRulesOnSample() throws Exception {
        Set<String> r = rules(new SecurityReviewer().review(sample()));
        assertTrue(r.containsAll(Set.of("SEC-SQL-INJECTION", "SEC-XSS", "SEC-HARDCODED-SECRET",
            "SEC-COMMAND-INJECTION", "SEC-PATH-TRAVERSAL", "SEC-WEAK-HASH", "SEC-INSECURE-RANDOM")), r.toString());
    }

    @Test
    void lineNumbersPointAtTheRightLine() throws Exception {
        String[] lines = sample().replace("\r\n", "\n").split("\n", -1);
        for (RuleReviewer reviewer : List.of(new StyleReviewer(), new LogicReviewer(), new SecurityReviewer())) {
            for (Finding f : reviewer.review(sample())) {
                assertEquals(lines[f.getLine() - 1].trim().length() > 100
                    ? lines[f.getLine() - 1].trim().substring(0, 97) + "..." : lines[f.getLine() - 1].trim(), f.getSnippet(),
                    f.getRule() + " line " + f.getLine());
            }
        }
    }

    @Test
    void summaryRejectsWhenSecurityHigh() throws Exception {
        SummaryRequest req = new SummaryRequest();
        req.setSessionId(Naming.newSessionId());
        req.setSource("OrderController.java");
        for (RuleReviewer reviewer : List.of(new StyleReviewer(), new LogicReviewer(), new SecurityReviewer())) {
            ReviewResult r = new ReviewResult();
            r.setAgent(reviewer.agentName());
            r.setDimension(reviewer.dimension());
            r.setFindings(reviewer.review(sample()));
            req.getResults().add(r);
        }
        String report = new SummaryComposer().compose(req);
        assertTrue(report.contains("拒绝合并"), report);
        assertTrue(report.contains("交叉命中"), report);
        assertTrue(req.getSessionId().startsWith(Naming.DEFAULT_STUDENT_ID + "-session-"));
    }
}
