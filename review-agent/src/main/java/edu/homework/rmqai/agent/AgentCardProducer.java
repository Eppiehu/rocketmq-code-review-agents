package edu.homework.rmqai.agent;

import static org.apache.rocketmq.a2a.common.constant.RocketMQA2AConstant.ROCKETMQ_PROTOCOL;
import static org.apache.rocketmq.a2a.common.uitl.RocketMQUtil.formatRocketMQServiceUrl;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;

/**
 * 发布本 Agent 的 AgentCard。Card 通过 HTTP 提供给 Supervisor 做服务发现,
 * 其中的 url 是 rocketmq://{endpoint}/{namespace}/{bizTopic}, 表示真正的调用走 RocketMQ。
 */
@ApplicationScoped
public class AgentCardProducer {
    private static final String ROCKETMQ_ENDPOINT = System.getProperty("rocketMQEndpoint", "");
    private static final String ROCKETMQ_NAMESPACE = System.getProperty("rocketMQNamespace", "");
    private static final String BIZ_TOPIC = System.getProperty("bizTopic", "");

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        AgentRole role = AgentRole.current();
        return new AgentCard.Builder()
            .name(role.agentName())
            .description(role.description())
            .url(formatRocketMQServiceUrl(ROCKETMQ_ENDPOINT, ROCKETMQ_NAMESPACE, BIZ_TOPIC))
            .version("1.0.0")
            .capabilities(new AgentCapabilities.Builder()
                // 审查结果是一次性产出的完整数据, 用非流式: 服务端只回传一条包含完整 Task 的响应。
                // 流式模式下 Task / Artifact / Status 三个事件是三条独立消息, 毫秒级先后发出时
                // LiteTopic 并发消费会乱序, A2A 客户端会丢弃早于 Task 到达的事件 (实测踩坑, 见报告)。
                .streaming(false)
                .pushNotifications(false)
                .stateTransitionHistory(true)
                .build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of(new AgentSkill.Builder()
                .id(role.key() + "-review")
                .name(role.title())
                .description(role.description())
                .tags(List.of("code-review", role.key()))
                .build()))
            .preferredTransport(ROCKETMQ_PROTOCOL)
            .protocolVersion("0.3.0")
            .build();
    }
}
