package edu.homework.rmqai.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 资源命名规范。所有 RocketMQ 资源都带学号, 避免与其他同学重名串台, 也便于按学号溯源消息轨迹。
 *
 * <pre>
 * Normal Topic (任务分发)   rmqai-{学号}-review-task-{style|logic|security|summary}
 * 普通消费者组               GID_{学号}_{style|logic|security|summary}
 * LiteTopic 父 Topic (回传)  rmqai-{学号}-review-result        绑定组 GID_{学号}_supervisor
 * 点对点 LiteTopic 父 Topic  rmqai-{学号}-agent-p2p            绑定组 GID_{学号}_agent_p2p
 * 会话 (LiteTopic)          {学号}-session-{时间}-{随机串}
 * 消息 Key                   会话 ID (+ requestId), 以学号开头
 * </pre>
 */
public final class Naming {
    public static final String DEFAULT_STUDENT_ID = "2601210448";

    private Naming() {
    }

    public static String studentId() {
        return System.getProperty("studentId", DEFAULT_STUDENT_ID);
    }

    public static String taskTopic(String role) {
        return "rmqai-" + studentId() + "-review-task-" + role;
    }

    public static String consumerGroup(String role) {
        return "GID_" + studentId() + "_" + role;
    }

    public static String resultLiteParentTopic() {
        return "rmqai-" + studentId() + "-review-result";
    }

    public static String supervisorLiteGroup() {
        return "GID_" + studentId() + "_supervisor";
    }

    public static String p2pLiteParentTopic() {
        return "rmqai-" + studentId() + "-agent-p2p";
    }

    public static String p2pLiteGroup() {
        return "GID_" + studentId() + "_agent_p2p";
    }

    /** 新建一次审查会话的 ID, 它同时就是 Supervisor 接收结果的 LiteTopic。 */
    public static String newSessionId() {
        String time = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return studentId() + "-session-" + time + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
