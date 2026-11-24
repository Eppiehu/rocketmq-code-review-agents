# rocketmq-a2a 1.0.9 补丁说明

来源：Maven Central `org.apache.rocketmq:rocketmq-a2a:1.0.9` 的 `sources.jar`（已校验 SHA1）。
构建产物版本号为 `1.0.9-homework`，只安装在本地 Maven 仓库 `E:\maven-repo`，与官方版本区分。

## 补丁 1：发送消息时设置消息 Key

官方 1.0.9 发送 A2A 请求和响应消息时没有设置 Key，无法按 Key 在控制台或 mqadmin 中检索某次会话的消息。

`common/uitl/RocketMQUtil.java`：

| 位置 | 改动 |
|---|---|
| `buildMessageForResponse`（响应，写入 LiteTopic） | `.setKeys(liteTopic)` |
| `sendRocketMQRequest` 普通路径（请求，写入 Agent 的 Normal Topic） | `.setKeys(liteTopic, requestId)` |
| `sendRocketMQRequest` sticky 路径（请求，写入服务端点对点 LiteTopic） | `.setKeys(liteTopic, requestId)` |

LiteTopic 就是 A2A 的 contextId（会话 ID），本作业中以学号开头，因此每条消息的 Key 都带学号。

## 补丁 2：服务端点对点 LiteTopic 可加前缀

`server/RocketMQA2AServerRoutes.java` 中服务端实例的点对点 LiteTopic 原为随机 UUID。
新增可选系统属性 `-DserverLiteTopicPrefix=...`，未设置时与原行为完全一致。
