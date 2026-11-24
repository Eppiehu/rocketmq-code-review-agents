# 启动 Style / Logic / Security / Summary 四个 Agent 进程 (同一个 jar, 角色由 -DagentRole 决定)
# 用法: .\start-agents.ps1 [学号] [Proxy gRPC 地址]
# 阿里云: .\start-agents.ps1 2601210448 rmq-cn-xxx.cn-hangzhou.rmq.aliyuncs.com:8080  (需先设置 RMQ_ACCESS_KEY / RMQ_SECRET_KEY)
$StudentId = if ($args.Count -gt 0) { $args[0] } else { '2601210448' }
$Endpoint = if ($args.Count -gt 1) { $args[1] } else { '127.0.0.1:8081' }
$java = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$jar = Join-Path $root 'review-agent\target\review-agent-1.0.0-runner.jar'
$logDir = Join-Path $root 'run-logs'
New-Item -ItemType Directory -Force $logDir | Out-Null

$ports = @{ style = 18091; logic = 18092; security = 18093; summary = 18094 }
foreach ($role in 'style', 'logic', 'security', 'summary') {
    $props = @(
        '-Xms128m', '-Xmx256m', '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8',
        "-DstudentId=$StudentId",
        "-DagentRole=$role",
        "-Dquarkus.http.port=$($ports[$role])",
        "-DrocketMQEndpoint=$Endpoint",
        "-DrocketMQNamespace=$env:RMQ_NAMESPACE",
        # 连阿里云实例时: 先设置环境变量 RMQ_ACCESS_KEY / RMQ_SECRET_KEY (实例用户名/密码), 本地集群留空
        "-DrocketMQAK=$env:RMQ_ACCESS_KEY",
        "-DrocketMQSK=$env:RMQ_SECRET_KEY",
        "-DbizTopic=rmqai-$StudentId-review-task-$role",          # Normal Topic: 接收任务
        "-DbizConsumerGroup=GID_${StudentId}_$role",              # 普通消费者组
        "-DworkAgentResponseTopic=rmqai-$StudentId-agent-p2p",    # 点对点 LiteTopic 父 Topic
        "-DworkAgentResponseGroupID=GID_${StudentId}_agent_p2p",
        "-DserverLiteTopicPrefix=$StudentId-$role-",              # 点对点 LiteTopic 以学号开头
        '-jar', $jar)
    $p = Start-Process $java -ArgumentList $props -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDir "$role.log") -RedirectStandardError (Join-Path $logDir "$role.err")
    Write-Output ("{0,-9} pid={1,-6} http=:{2}  topic=rmqai-{3}-review-task-{0}  group=GID_{3}_{0}" -f $role, $p.Id, $ports[$role], $StudentId)
}
