# 启动进阶题 3 用的本地 RocketMQ: NameServer + Broker(开启 LMQ) + Proxy(gRPC 8081)
# 数据目录 E:\rmq-a2a-store, 之前创建的 Topic / 消费者组都保存在里面, 重启后仍然有效
$rmq = 'D:\codefield\Codex\rocketmq\distribution\target\rocketmq-5.5.1\rocketmq-5.5.1'
$java = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe'
$conf = Join-Path (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)) 'cluster-conf'
$logs = 'E:\rmq-a2a-work\cluster\logs'
New-Item -ItemType Directory -Force $logs | Out-Null

function Start-Java($name, $heap, $rest) {
    $a = @("-Xms$heap", "-Xmx$heap", "-Drocketmq.home.dir=$rmq", '-cp', "`"$rmq\lib\*`"") + $rest
    $p = Start-Process $java -ArgumentList $a -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$logs\$name.out" -RedirectStandardError "$logs\$name.err"
    Write-Output "$name pid=$($p.Id)"
}

Start-Java namesrv 256m @('org.apache.rocketmq.namesrv.NamesrvStartup'); Start-Sleep 6
Start-Java broker 1g @('-XX:MaxDirectMemorySize=1g', 'org.apache.rocketmq.broker.BrokerStartup', '-c', "$conf\broker.conf"); Start-Sleep 15
Start-Java proxy 512m @('org.apache.rocketmq.proxy.ProxyStartup', '-pc', "$conf\rmq-proxy.json", '-pm', 'Cluster'); Start-Sleep 20
Get-Content "$logs\namesrv.out", "$logs\broker.out", "$logs\proxy.out" -Tail 1
