package edu.homework.rmqai.common.review;

import java.util.Locale;

public final class Reviewers {
    private Reviewers() {
    }

    public static RuleReviewer forRole(String role) {
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "style" -> new StyleReviewer();
            case "logic" -> new LogicReviewer();
            case "security" -> new SecurityReviewer();
            default -> throw new IllegalArgumentException("unknown review role: " + role);
        };
    }
}
