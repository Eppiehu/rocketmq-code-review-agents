package edu.homework.rmqai.common.review;

import edu.homework.rmqai.common.model.Severity;
import java.util.regex.Pattern;

/** LogicAgent: 潜在逻辑缺陷与边界条件。 */
public class LogicReviewer extends RuleReviewer {

    public LogicReviewer() {
        textRule("LOGIC-EMPTY-CATCH", Severity.HIGH, "catch\\s*\\([^)]*\\)\\s*\\{\\s*}",
            "空 catch 块吞掉了异常, 出错时无任何痕迹", "至少记录日志, 或向上抛出包装后的业务异常");
        lineRule("LOGIC-PRINT-STACK", Severity.MEDIUM, "\\.printStackTrace\\(\\)",
            "异常只打印堆栈后继续执行, 调用方无法感知失败", "使用日志记录并按业务需要处理或抛出");
        lineRule("LOGIC-STRING-EQ", Severity.HIGH, "(\"[^\"]*\"\\s*[!=]=\\s*\\w)|(\\w\\s*[!=]=\\s*\"[^\"]*\")",
            "使用 ==/!= 比较字符串, 比较的是引用而不是内容", "改用 equals(), 如 \"PAID\".equals(status)");
        lineRule("LOGIC-NPE-EQUALS", Severity.MEDIUM, Pattern.compile("\\b[a-z]\\w*\\.equals\\(\\s*\"[^\"]*\"\\s*\\)"),
            line -> true, "变量在前调用 equals(\"常量\"), 变量为 null 时抛 NullPointerException",
            "把常量写在前面: \"常量\".equals(变量), 或用 Objects.equals");
        lineRule("LOGIC-OFF-BY-ONE", Severity.HIGH, "for\\s*\\(.*;\\s*\\w+\\s*<=\\s*[\\w.]+\\.(length|size\\(\\))\\s*;",
            "循环条件使用 <= length/size(), 最后一次迭代会越界", "改为 < length / < size()");
        lineRule("LOGIC-FLOAT-EQ", Severity.MEDIUM, "[!=]=\\s*-?\\d+\\.\\d+|\\d+\\.\\d+\\s*[!=]=",
            "浮点数直接用 == 比较, 精度误差会导致判断失败", "金额用 BigDecimal; 浮点比较用差值和误差阈值");
        lineRule("LOGIC-RESOURCE-LEAK", Severity.HIGH,
            Pattern.compile("=\\s*new\\s+(FileInputStream|FileOutputStream|FileReader|FileWriter|BufferedReader|Scanner)\\(|"
                + "=\\s*\\w+\\.getConnection\\(|=\\s*\\w+\\.(createStatement|prepareStatement)\\("),
            line -> !line.contains("try ("), "流 / 连接未放在 try-with-resources 中, 异常路径下不会关闭",
            "使用 try (Resource r = ...) { } 自动关闭");
        lineRule("LOGIC-RETURN-NULL-COLLECTION", Severity.LOW, "return\\s+null\\s*;",
            "返回 null, 调用方容易漏判空", "集合返回 Collections.emptyList(), 单值考虑 Optional");
        lineRule("LOGIC-INT-DIVISION", Severity.MEDIUM, "\\((double|float)\\)\\s*\\(\\s*[\\w.]+(\\(\\))?\\s*/\\s*[\\w.]+(\\(\\))?\\s*\\)",
            "先做整数除法再转 double, 小数部分已经丢失", "先转型再除: (double) a / b");
        lineRule("LOGIC-CATCH-GENERIC", Severity.LOW, "catch\\s*\\(\\s*(Exception|Throwable)\\s+\\w+\\s*\\)",
            "捕获范围过宽的 Exception/Throwable", "捕获具体异常类型, 避免掩盖编程错误");
        lineRule("LOGIC-SLEEP-IN-LOCK", Severity.MEDIUM, "Thread\\.sleep\\(",
            "使用 Thread.sleep 等待/重试, 可能阻塞线程或掩盖时序问题", "使用带超时的等待机制或调度器");
    }

    @Override
    public String agentName() {
        return "LogicAgent";
    }

    @Override
    public String dimension() {
        return "logic";
    }
}
