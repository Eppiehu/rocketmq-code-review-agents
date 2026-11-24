package edu.homework.rmqai.supervisor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读取待审查的代码: 本地文件 / 代码片段, 或 GitHub PR 链接 (只审查 PR 新增和上下文行)。 */
final class CodeSource {
    private static final Pattern PR_URL = Pattern.compile("https?://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");

    final String source;
    final String code;

    private CodeSource(String source, String code) {
        this.source = source;
        this.code = code;
    }

    static CodeSource load(String arg) throws Exception {
        Matcher m = PR_URL.matcher(arg);
        if (m.find()) {
            return fromPullRequest(arg, m.group(1), m.group(2), m.group(3));
        }
        Path path = Path.of(arg);
        if (Files.exists(path)) {
            return new CodeSource(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8));
        }
        return new CodeSource("代码片段", arg);
    }

    /**
     * 通过 GitHub API 取 PR 的改动文件, 把每个文件 patch 中的上下文行和新增行拼成一段代码
     * (删除行跳过), 每个文件前加一行注释标明文件名。报告中的行号指这段拼接后的代码。
     */
    private static CodeSource fromPullRequest(String url, String owner, String repo, String number) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(
                "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + number + "/files?per_page=100"))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "rmqai-code-review")
            .timeout(Duration.ofSeconds(60))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("读取 PR 失败, HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JSONArray files = JSON.parseArray(resp.body());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            JSONObject f = files.getJSONObject(i);
            String patch = f.getString("patch");
            if (patch == null) {
                continue;
            }
            sb.append("// ===== ").append(f.getString("filename")).append(" =====\n");
            for (String line : patch.split("\n")) {
                if (line.startsWith("@@") || line.startsWith("-")) {
                    continue;
                }
                sb.append(line.isEmpty() ? "" : line.substring(1)).append('\n');
            }
        }
        return new CodeSource(url, sb.toString());
    }
}
