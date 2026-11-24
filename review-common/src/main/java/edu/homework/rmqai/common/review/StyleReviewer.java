package edu.homework.rmqai.common.review;

import edu.homework.rmqai.common.model.Severity;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** StyleAgent: 代码风格与命名规范 (参照《阿里巴巴 Java 开发手册》常见条目)。 */
public class StyleReviewer extends RuleReviewer {
    private static final int MAX_LINE_LENGTH = 120;
    private static final Pattern METHOD_DECL = Pattern.compile(
        "^\\s*(public|private|protected)?\\s*(static\\s+)?(final\\s+)?[\\w<>\\[\\],\\s]+?\\s+(\\w+)\\s*\\([^;]*\\)\\s*(throws [\\w.,\\s]+)?\\{");
    private static final Pattern CONSTANT_DECL = Pattern.compile("static\\s+final\\s+[\\w<>\\[\\]]+\\s+(\\w+)\\s*=");
    private static final Pattern LOCAL_DECL = Pattern.compile(
        "^\\s*(int|long|String|boolean|double|float|List<[^>]+>|Map<[^>]+>)\\s+(\\w+)\\s*[=;]");

    public StyleReviewer() {
        lineRule("STYLE-CLASS-NAME", Severity.MEDIUM, "\\b(class|interface|enum)\\s+[a-z_]\\w*",
            "类名不符合 UpperCamelCase", "类、接口、枚举名使用大驼峰, 如 OrderService");
        customRule((lines, code, out) -> {
            for (int i = 0; i < lines.length; i++) {
                if (isComment(lines[i]) || lines[i].contains(" new ") || lines[i].trim().startsWith("return")) {
                    continue;
                }
                Matcher m = METHOD_DECL.matcher(lines[i]);
                if (m.find()) {
                    String name = m.group(4);
                    boolean keyword = name.equals("if") || name.equals("for") || name.equals("while")
                        || name.equals("switch") || name.equals("catch") || name.equals("synchronized");
                    if (!keyword && (Character.isUpperCase(name.charAt(0)) || name.contains("_"))
                        && !isConstructor(lines, name)) {
                        out.add(finding(Severity.MEDIUM, i + 1, "STYLE-METHOD-NAME",
                            "方法名 " + name + " 不符合 lowerCamelCase", "方法名使用小驼峰, 如 queryOrder", lines[i]));
                    }
                }
            }
        });
        customRule((lines, code, out) -> {
            for (int i = 0; i < lines.length; i++) {
                Matcher m = CONSTANT_DECL.matcher(lines[i]);
                if (!isComment(lines[i]) && m.find() && !m.group(1).matches("[A-Z][A-Z0-9_]*")
                    && !m.group(1).equals("serialVersionUID")) {
                    out.add(finding(Severity.LOW, i + 1, "STYLE-CONSTANT-NAME",
                        "常量 " + m.group(1) + " 未使用全大写下划线命名", "常量命名如 MAX_RETRY_TIMES", lines[i]));
                }
            }
        });
        customRule((lines, code, out) -> {
            for (int i = 0; i < lines.length; i++) {
                Matcher m = LOCAL_DECL.matcher(lines[i]);
                if (!isComment(lines[i]) && m.find()) {
                    String name = m.group(2);
                    if (name.contains("_") || Character.isUpperCase(name.charAt(0))) {
                        out.add(finding(Severity.LOW, i + 1, "STYLE-VARIABLE-NAME",
                            "变量 " + name + " 不符合 lowerCamelCase", "变量名使用小驼峰, 如 orderCount", lines[i]));
                    } else if (name.length() == 1 && !lines[i].contains("for")) {
                        out.add(finding(Severity.LOW, i + 1, "STYLE-VARIABLE-NAME",
                            "变量名 " + name + " 过短, 无法表达含义", "使用有业务含义的名称", lines[i]));
                    }
                }
            }
        });
        customRule((lines, code, out) -> {
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].length() > MAX_LINE_LENGTH) {
                    out.add(finding(Severity.LOW, i + 1, "STYLE-LINE-LENGTH",
                        "单行 " + lines[i].length() + " 个字符, 超过 " + MAX_LINE_LENGTH, "拆分长表达式或参数列表", lines[i]));
                }
                if (lines[i].startsWith("\t")) {
                    out.add(finding(Severity.LOW, i + 1, "STYLE-TAB-INDENT", "使用了 Tab 缩进", "统一使用 4 个空格缩进", lines[i]));
                }
                if (!lines[i].isEmpty() && Character.isWhitespace(lines[i].charAt(lines[i].length() - 1)) && !lines[i].isBlank()) {
                    out.add(finding(Severity.LOW, i + 1, "STYLE-TRAILING-SPACE", "行尾有多余空白", "删除行尾空白", lines[i]));
                }
            }
        });
        lineRule("STYLE-WILDCARD-IMPORT", Severity.LOW, "^\\s*import\\s+[\\w.]+\\.\\*;",
            "使用了通配符 import", "显式导入需要的类");
        lineRule("STYLE-SYSTEM-OUT", Severity.LOW, "System\\.(out|err)\\.print",
            "业务代码中直接使用 System.out/err", "使用 SLF4J 等日志框架输出");
        lineRule("STYLE-BRACES", Severity.MEDIUM, "^\\s*(if|for|while)\\s*\\(.*\\)\\s*[^{\\s].*;\\s*$",
            "if/for/while 语句省略了大括号", "即使只有一行也加上大括号");
    }

    private static boolean isConstructor(String[] lines, String name) {
        for (String l : lines) {
            if (l.matches(".*\\b(class|enum)\\s+" + Pattern.quote(name) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String agentName() {
        return "StyleAgent";
    }

    @Override
    public String dimension() {
        return "style";
    }
}
