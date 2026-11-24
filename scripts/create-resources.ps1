# 创建代码审查多智能体系统所需的 RocketMQ 资源 (所有名称都带学号)
# 用法: .\create-resources.ps1 [-StudentId 2601210448] [-Namesrv 127.0.0.1:9876]
$StudentId = if ($args.Count -gt 0) { $args[0] } else { '2601210448' }
$Namesrv = if ($args.Count -gt 1) { $args[1] } else { '127.0.0.1:9876' }
$rmq = 'D:\codefield\Codex\rocketmq\distribution\target\rocketmq-5.5.1\rocketmq-5.5.1'
$java = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe'

function Invoke-MQAdmin {
    $all = @("-Drocketmq.home.dir=$rmq", '-cp', "$rmq\lib\*", 'org.apache.rocketmq.tools.command.MQAdminStartup') + $args
    Write-Output ("> mqadmin " + ($args -join ' '))
    & $java @all 2>$null | Where-Object { $_ -and $_ -notmatch '^\s*$' }
}

$S = $StudentId
$roles = 'style', 'logic', 'security', 'summary'

Write-Output '==== 1. Normal Topic: 任务分发 (每个审查 Agent 一个) ===='
foreach ($r in $roles) {
    Invoke-MQAdmin updateTopic -n $Namesrv -c DefaultCluster -t "rmqai-$S-review-task-$r" -r 4 -w 4 -a '+message.type=NORMAL'
}

Write-Output '==== 2. 普通消费者组: 订阅各自的任务 Topic ===='
foreach ($r in $roles) {
    Invoke-MQAdmin updateSubGroup -n $Namesrv -c DefaultCluster -g "GID_${S}_$r"
}

Write-Output '==== 3. LiteTopic 父 Topic: 会话级结果回传 ===='
# Supervisor 接收各 Agent 结果; 每个审查会话是其下一个以学号开头的 LiteTopic
Invoke-MQAdmin updateTopic -n $Namesrv -c DefaultCluster -t "rmqai-$S-review-result" -r 4 -w 4 -a '+message.type=LITE'
# 各审查 Agent 的点对点 LiteTopic (SDK 用于 sticky 路由)
Invoke-MQAdmin updateTopic -n $Namesrv -c DefaultCluster -t "rmqai-$S-agent-p2p" -r 4 -w 4 -a '+message.type=LITE'

Write-Output '==== 4. Lite 消费者组: 绑定 LiteTopic 父 Topic ===='
Invoke-MQAdmin updateSubGroup -n $Namesrv -c DefaultCluster -g "GID_${S}_supervisor" --attributes "+lite.bind.topic=rmqai-$S-review-result"
Invoke-MQAdmin updateSubGroup -n $Namesrv -c DefaultCluster -g "GID_${S}_agent_p2p" --attributes "+lite.bind.topic=rmqai-$S-agent-p2p"

Write-Output '==== 5. 校验 ===='
Invoke-MQAdmin topicList -n $Namesrv | Where-Object { $_ -match $S }
