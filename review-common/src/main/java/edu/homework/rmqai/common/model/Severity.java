package edu.homework.rmqai.common.model;

public enum Severity {
    HIGH("高", 10), MEDIUM("中", 5), LOW("低", 2);

    private final String label;
    private final int penalty;

    Severity(String label, int penalty) {
        this.label = label;
        this.penalty = penalty;
    }

    public String label() {
        return label;
    }

    /** 评分时每条发现扣的分 */
    public int penalty() {
        return penalty;
    }
}
