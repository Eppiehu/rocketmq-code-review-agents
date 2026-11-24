package edu.homework.rmqai.agent;

import com.alibaba.fastjson.JSON;
import edu.homework.rmqai.common.model.ReviewResult;
import edu.homework.rmqai.common.model.ReviewTask;
import edu.homework.rmqai.common.model.SummaryRequest;
import edu.homework.rmqai.common.review.Reviewers;
import edu.homework.rmqai.common.review.RuleReviewer;
import edu.homework.rmqai.common.review.SummaryComposer;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审查 Agent 的执行逻辑。A2A 请求由 RocketMQA2AServerRoutes 从 Normal Topic 消费后交给这里,
 * 这里产出的 Artifact 再由 SDK 写入请求方指定的 LiteTopic (会话) 回传给 Supervisor。
 */
@ApplicationScoped
public class AgentExecutorProducer {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutorProducer.class);
    private static final String HOST = ManagementFactory.getRuntimeMXBean().getName();

    @Produces
    public AgentExecutor agentExecutor() {
        AgentRole role = AgentRole.current();
        log.info("[{}] executor ready, role={}, host={}", role.agentName(), role.key(), HOST);
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
                long received = System.currentTimeMillis();
                Message request = context.getMessage();
                Task task = context.getTask();
                if (task == null) {
                    task = newTask(request);
                    eventQueue.enqueueEvent(task);
                }
                TaskUpdater updater = new TaskUpdater(context, eventQueue);
                try {
                    String text = extractText(request);
                    String output = role == AgentRole.SUMMARY ? summarize(text) : review(role, text, received);
                    updater.addArtifact(List.<Part<?>>of(new TextPart(output, null)));
                    updater.complete();
                } catch (Exception e) {
                    log.error("[{}] review failed, contextId={}", role.agentName(), request.getContextId(), e);
                    updater.startWork(updater.newAgentMessage(List.of(new TextPart("审查失败: " + e.getMessage())), Map.of()));
                    updater.fail();
                }
            }

            @Override
            public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
                Task task = context.getTask();
                if (task == null || task.getStatus() == null) {
                    return;
                }
                TaskState state = task.getStatus().state();
                if (state == TaskState.CANCELED || state == TaskState.COMPLETED) {
                    throw new TaskNotCancelableError();
                }
                new TaskUpdater(context, eventQueue).cancel();
            }
        };
    }

    private static String review(AgentRole role, String text, long received) {
        ReviewTask task = JSON.parseObject(text, ReviewTask.class);
        RuleReviewer reviewer = Reviewers.forRole(role.key());
        log.info("[{}] received task, session={}, source={}, lines={}, queueDelay={}ms", role.agentName(),
            task.getSessionId(), task.getSource(), task.getCode().split("\n").length, received - task.getDispatchTime());
        ReviewResult result = new ReviewResult();
        result.setSessionId(task.getSessionId());
        result.setAgent(reviewer.agentName());
        result.setDimension(reviewer.dimension());
        result.setHost(HOST);
        result.setReceivedTime(received);
        result.setFindings(reviewer.review(task.getCode()));
        result.setFinishedTime(System.currentTimeMillis());
        log.info("[{}] done, session={}, findings={}, cost={}ms -> reply to LiteTopic {}", role.agentName(),
            task.getSessionId(), result.getFindings().size(), result.getFinishedTime() - received, task.getSessionId());
        return JSON.toJSONString(result);
    }

    private static String summarize(String text) {
        SummaryRequest req = JSON.parseObject(text, SummaryRequest.class);
        log.info("[SummaryAgent] received {} results, session={}", req.getResults().size(), req.getSessionId());
        String report = new SummaryComposer().compose(req);
        log.info("[SummaryAgent] report generated, session={}, {} chars -> reply to LiteTopic {}",
            req.getSessionId(), report.length(), req.getSessionId());
        return report;
    }

    private static String extractText(Message message) {
        StringBuilder sb = new StringBuilder();
        if (message != null && message.getParts() != null) {
            for (Part<?> part : message.getParts()) {
                if (part instanceof TextPart textPart) {
                    sb.append(textPart.getText());
                }
            }
        }
        return sb.toString();
    }

    private static Task newTask(Message request) {
        String id = request.getTaskId() != null ? request.getTaskId() : UUID.randomUUID().toString();
        String contextId = request.getContextId() != null ? request.getContextId() : UUID.randomUUID().toString();
        return new Task(id, contextId, new TaskStatus(TaskState.SUBMITTED), null, List.of(request), null);
    }
}
