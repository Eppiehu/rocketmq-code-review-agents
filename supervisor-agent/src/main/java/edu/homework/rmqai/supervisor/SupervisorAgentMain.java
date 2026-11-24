package edu.homework.rmqai.supervisor;

import com.alibaba.fastjson.JSON;
import edu.homework.rmqai.common.Naming;
import edu.homework.rmqai.common.model.ReviewResult;
import edu.homework.rmqai.common.model.ReviewTask;
import edu.homework.rmqai.common.model.SummaryRequest;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.apache.rocketmq.a2a.transport.config.RocketMQTransportConfig;
import org.apache.rocketmq.a2a.transport.impl.RocketMQTransport;

/**
 * SupervisorAgent。
 * <ol>
 *   <li>接收代码片段 / 文件 / GitHub PR 链接</li>
 *   <li>拆成 style、logic、security 三个审查维度, 通过 A2A over RocketMQ 并行分发到各 Agent 的 Normal Topic</li>
 *   <li>各 Agent 把结果回传到本次会话的 LiteTopic (会话 ID = {学号}-session-...)</li>
 *   <li>收齐后把结果发给 SummaryAgent, 拿到结构化报告</li>
 * </ol>
 * 用法: java -jar supervisor-agent.jar &lt;文件路径 | PR 链接 | 代码片段&gt;
 */
public final class SupervisorAgentMain {
    private static final String[] REVIEWERS = {"style", "logic", "security"};
    private static final Map<String, String> AGENT_URLS = new LinkedHashMap<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS");

    static {
        AGENT_URLS.put("style", System.getProperty("styleAgentUrl", "http://127.0.0.1:18091"));
        AGENT_URLS.put("logic", System.getProperty("logicAgentUrl", "http://127.0.0.1:18092"));
        AGENT_URLS.put("security", System.getProperty("securityAgentUrl", "http://127.0.0.1:18093"));
        AGENT_URLS.put("summary", System.getProperty("summaryAgentUrl", "http://127.0.0.1:18094"));
    }

    private SupervisorAgentMain() {
    }

    /**
     * 连接参数: 先取 -D 系统属性, 再取环境变量, 都没有则为空 (本地开源集群不需要认证)。
     * 连阿里云实例时用环境变量传实例用户名/密码, 不写进代码和命令行。
     */
    private static String conf(String property, String env) {
        String v = System.getProperty(property);
        if (v == null || v.isEmpty()) {
            v = System.getenv(env);
        }
        return v == null ? "" : v;
    }

    public static void main(String[] args) {
        try {
            run(args);
            System.exit(0);
        } catch (Throwable t) {
            // RocketMQ / A2A 客户端有非守护线程, 出错时必须显式退出, 否则进程会一直挂着
            System.err.println("[" + TIME.format(new Date()) + "] 审查失败: " + t);
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("用法: java -jar supervisor-agent.jar <文件路径 | GitHub PR 链接 | 代码片段>");
            System.exit(2);
        }
        long timeoutSec = Long.getLong("timeoutSeconds", 60);
        CodeSource input = CodeSource.load(args[0]);
        String sessionId = Naming.newSessionId();
        int lines = input.code.split("\n", -1).length;

        log("==== SupervisorAgent 启动, 学号 " + Naming.studentId() + " ====");
        log("审查对象: " + input.source + " (" + lines + " 行)");
        log("会话 ID / LiteTopic: " + sessionId);
        log("结果回传: LiteTopic 父 Topic " + Naming.resultLiteParentTopic() + ", 消费者组 " + Naming.supervisorLiteGroup());

        // 1. 服务发现: 通过 HTTP 取 AgentCard, card.url 是 rocketmq://endpoint/namespace/topic
        Map<String, Client> clients = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : AGENT_URLS.entrySet()) {
            AgentCard card = new A2ACardResolver(e.getValue()).getAgentCard();
            RocketMQTransportConfig cfg = RocketMQTransportConfig.builder()
                .namespace(conf("rocketMQNamespace", "RMQ_NAMESPACE"))
                .accessKey(conf("rocketMQAK", "RMQ_ACCESS_KEY"))
                .secretKey(conf("rocketMQSK", "RMQ_SECRET_KEY"))
                .workAgentResponseTopic(Naming.resultLiteParentTopic())
                .workAgentResponseGroupID(Naming.supervisorLiteGroup())
                .liteTopic(sessionId)
                .build();
            clients.put(e.getKey(), Client.builder(card)
                .withTransport(RocketMQTransport.class, cfg)
                .build());
            log("发现 Agent: " + card.name() + " -> " + card.url());
        }

        // 2. 拆分维度, 并行分发
        long dispatchStart = System.currentTimeMillis();
        Map<String, CompletableFuture<String>> pending = new LinkedHashMap<>();
        for (String dim : REVIEWERS) {
            ReviewTask task = new ReviewTask(sessionId, dim, input.source, input.code);
            pending.put(dim, send(clients.get(dim), dim, sessionId, JSON.toJSONString(task)));
        }
        log("已并行分发 3 个审查任务, 等待结果回传到 LiteTopic " + sessionId + " ...");

        SummaryRequest summary = new SummaryRequest();
        summary.setSessionId(sessionId);
        summary.setSource(input.source);
        summary.setCodeLines(lines);
        summary.setDispatchTime(dispatchStart);
        List<ReviewResult> results = new ArrayList<>();
        for (Map.Entry<String, CompletableFuture<String>> e : pending.entrySet()) {
            String json = e.getValue().get(timeoutSec, TimeUnit.SECONDS);
            ReviewResult r = JSON.parseObject(json, ReviewResult.class);
            results.add(r);
            log(String.format("收到 %-13s 结果: %2d 条发现, Agent 处理 %d ms, 往返 %d ms",
                r.getAgent(), r.getFindings().size(), r.getFinishedTime() - r.getReceivedTime(),
                System.currentTimeMillis() - dispatchStart));
        }
        summary.setResults(results);

        // 3. 交给 SummaryAgent 汇总
        log("3 个维度全部返回, 转交 SummaryAgent 汇总 ...");
        String report = send(clients.get("summary"), "summary", sessionId, JSON.toJSONString(summary))
            .get(timeoutSec, TimeUnit.SECONDS);
        log("SummaryAgent 报告已回传, 端到端耗时 " + (System.currentTimeMillis() - dispatchStart) + " ms");

        Path out = Path.of(System.getProperty("reportDir", "reports"));
        Files.createDirectories(out);
        Path reportFile = out.resolve(sessionId + ".md");
        Files.writeString(reportFile, report, StandardCharsets.UTF_8);
        Files.writeString(out.resolve(sessionId + "-results.json"), JSON.toJSONString(summary, true), StandardCharsets.UTF_8);
        System.out.println();
        System.out.println(report);
        log("报告已保存: " + reportFile.toAbsolutePath());

        clients.values().forEach(Client::close);
    }

    /** 发送一条 A2A 消息 (contextId = 会话 ID, SDK 据此把它作为回传 LiteTopic), 返回 Agent 最终输出的文本。 */
    private static CompletableFuture<String> send(Client client, String dim, String sessionId, String text) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Message message = new Message.Builder()
            .role(Message.Role.USER)
            .parts(List.<Part<?>>of(new TextPart(text)))
            .contextId(sessionId)
            .messageId(sessionId + "-" + dim + "-" + UUID.randomUUID().toString().substring(0, 8))
            .build();
        // 流式模式下结果分两类事件到达: TaskArtifactUpdateEvent 携带产出内容, TaskStatusUpdateEvent 携带状态。
        // 最终的 COMPLETED 状态事件里不一定带 artifacts, 所以要边收边累积。
        StringBuilder streamed = new StringBuilder();
        BiConsumer<ClientEvent, AgentCard> consumer = (event, card) -> {
            if (Boolean.getBoolean("debugEvents")) {
                debugEvent(dim, event);
            }
            Task task = null;
            if (event instanceof TaskUpdateEvent e) {
                task = e.getTask();
                if (e.getUpdateEvent() instanceof TaskArtifactUpdateEvent a && a.getArtifact() != null) {
                    synchronized (streamed) {
                        streamed.append(textOf(a.getArtifact().parts()));
                    }
                }
            } else if (event instanceof TaskEvent e) {
                task = e.getTask();
            } else if (event instanceof MessageEvent e) {
                future.complete(textOf(e.getMessage().getParts()));
                return;
            }
            if (task == null || task.getStatus() == null) {
                return;
            }
            TaskState state = task.getStatus().state();
            if (state == TaskState.COMPLETED) {
                StringBuilder fromTask = new StringBuilder();
                if (task.getArtifacts() != null) {
                    for (Artifact a : task.getArtifacts()) {
                        fromTask.append(textOf(a.parts()));
                    }
                }
                String resultText;
                synchronized (streamed) {
                    resultText = fromTask.length() >= streamed.length() ? fromTask.toString() : streamed.toString();
                }
                if (resultText.isBlank()) {
                    future.completeExceptionally(new IllegalStateException(card.name() + " 返回了空结果"));
                } else {
                    future.complete(resultText);
                }
            } else if (state == TaskState.FAILED || state == TaskState.CANCELED || state == TaskState.REJECTED) {
                future.completeExceptionally(new IllegalStateException(card.name() + " 任务结束状态 " + state));
            }
        };
        CompletableFuture.runAsync(() -> {
            try {
                log("-> 分发给 " + dim + " Agent, messageId=" + message.getMessageId());
                client.sendMessage(message, List.of(consumer), future::completeExceptionally, null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static void debugEvent(String dim, ClientEvent event) {
        StringBuilder sb = new StringBuilder("[debug " + dim + "] " + event.getClass().getSimpleName());
        Task task = null;
        if (event instanceof TaskUpdateEvent e) {
            task = e.getTask();
            sb.append(" update=").append(e.getUpdateEvent() == null ? null : e.getUpdateEvent().getClass().getSimpleName());
            if (e.getUpdateEvent() instanceof TaskArtifactUpdateEvent a && a.getArtifact() != null) {
                sb.append(" artifactParts=").append(a.getArtifact().parts() == null ? null
                    : a.getArtifact().parts().stream().map(p -> p.getClass().getSimpleName()).toList());
            }
        } else if (event instanceof TaskEvent e) {
            task = e.getTask();
        }
        if (task != null) {
            sb.append(" state=").append(task.getStatus() == null ? null : task.getStatus().state())
                .append(" artifacts=").append(task.getArtifacts() == null ? null : task.getArtifacts().size());
        }
        log(sb.toString());
    }

    private static String textOf(List<Part<?>> parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (Part<?> p : parts) {
                if (p instanceof TextPart t) {
                    sb.append(t.getText());
                }
            }
        }
        return sb.toString();
    }

    private static synchronized void log(String s) {
        System.out.println("[" + TIME.format(new Date()) + "] " + s);
    }
}
