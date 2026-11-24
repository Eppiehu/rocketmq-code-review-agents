package edu.homework.rmqai.common.review;

import edu.homework.rmqai.common.model.Severity;
import java.util.regex.Pattern;

/** SecurityAgent: 常见安全漏洞模式 (OWASP Top 10 中能用模式识别的部分)。 */
public class SecurityReviewer extends RuleReviewer {

    public SecurityReviewer() {
        lineRule("SEC-SQL-INJECTION", Severity.HIGH,
            Pattern.compile("(?i)\"\\s*(select|insert|update|delete)\\b[^\"]*\"\\s*\\+|(?i)\\b(where|values|set)\\b[^\"]*'?\"\\s*\\+\\s*\\w"),
            line -> true, "SQL 语句由字符串拼接用户输入构造, 存在 SQL 注入风险 (CWE-89)",
            "使用 PreparedStatement 占位符 ? 或 ORM 参数绑定");
        lineRule("SEC-SQL-EXECUTE", Severity.HIGH,
            Pattern.compile("\\.(executeQuery|executeUpdate|execute)\\(\\s*(\"[^\"]*\"\\s*\\+|\\w+\\s*\\+)"),
            line -> true, "执行由拼接得到的 SQL (CWE-89)", "改用 PreparedStatement 并绑定参数");
        lineRule("SEC-XSS", Severity.HIGH,
            Pattern.compile("getWriter\\(\\)\\.(print|println|write)\\(.*(getParameter|\\+)|innerHTML\\s*=|document\\.write\\("),
            line -> true, "未经转义把请求参数直接输出到页面, 存在 XSS 风险 (CWE-79)",
            "输出前做 HTML 转义 (如 StringEscapeUtils.escapeHtml4), 或使用模板引擎自动转义");
        lineRule("SEC-HARDCODED-SECRET", Severity.HIGH,
            Pattern.compile("(?i)\\b\\w*(password|passwd|pwd|secret|secretkey|apikey|accesskey|token)\\w*\\s*=\\s*\"[^\"]{4,}\""),
            line -> true, "代码中硬编码了密码 / 密钥 (CWE-798)", "放到配置中心或环境变量, 并加密存储");
        lineRule("SEC-COMMAND-INJECTION", Severity.HIGH,
            "Runtime\\.getRuntime\\(\\)\\.exec\\(.*\\+|new ProcessBuilder\\(.*\\+",
            "拼接外部输入执行系统命令, 存在命令注入风险 (CWE-78)", "使用参数数组形式并对输入做白名单校验");
        lineRule("SEC-PATH-TRAVERSAL", Severity.HIGH, "new\\s+File\\w*\\(.*(getParameter|\\+\\s*\\w*(name|path|file))",
            "用外部输入拼接文件路径, 可能被 ../ 穿越访问任意文件 (CWE-22)", "规范化路径后校验必须位于允许的目录内");
        lineRule("SEC-WEAK-HASH", Severity.MEDIUM, "MessageDigest\\.getInstance\\(\"(MD5|SHA-?1)\"\\)",
            "使用 MD5/SHA-1 等弱哈希 (CWE-327)", "密码用 BCrypt/PBKDF2, 摘要用 SHA-256 及以上");
        lineRule("SEC-INSECURE-RANDOM", Severity.MEDIUM, "new\\s+Random\\(",
            "java.util.Random 可预测, 不能用于生成令牌、验证码", "使用 SecureRandom");
        lineRule("SEC-DESERIALIZATION", Severity.MEDIUM, "new\\s+ObjectInputStream\\(",
            "反序列化不可信数据可能导致远程代码执行 (CWE-502)", "避免 Java 原生反序列化, 或使用白名单过滤");
        lineRule("SEC-LOG-SENSITIVE", Severity.LOW, "(?i)(log\\.\\w+|System\\.out\\.print\\w*)\\(.*(password|pwd|token|idcard)",
            "日志中输出了敏感信息", "对敏感字段脱敏后再记录");
    }

    @Override
    public String agentName() {
        return "SecurityAgent";
    }

    @Override
    public String dimension() {
        return "security";
    }
}
