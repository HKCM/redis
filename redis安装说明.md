<!-- TOC -->
* [Redis安装说明](#redis安装说明)
* [1.单机安装Redis](#1单机安装redis)
  * [1.1.安装Redis依赖](#11安装redis依赖)
  * [1.2.下载安装包并解压](#12下载安装包并解压)
  * [1.3.启动](#13启动)
    * [1.3.1.默认启动](#131默认启动)
    * [1.3.2.指定配置启动](#132指定配置启动)
    * [1.3.3.开机自启](#133开机自启)
  * [1.4.配置文件](#14配置文件)
* [2. Docker运行](#2-docker运行)
* [3.Redis客户端](#3redis客户端)
  * [3.1.Redis命令行客户端](#31redis命令行客户端)
  * [3.2.Java客户端](#32java客户端)
<!-- TOC -->

# Redis安装说明

Redis的官方网站地址：https://redis.io/

# 1.单机安装Redis

## 1.1.安装Redis依赖

Redis是基于C语言编写的，因此首先需要安装Redis所需要的gcc依赖：

```sh
yum install -y gcc tcl
```

## 1.2.下载安装包并解压

下载地址: https://redis.io/download/#redis-downloads

例如在/usr/local/src 目录：

解压缩：

```sh
tar -xzf redis-6.2.6.tar.gz
```

进入redis目录：

```sh
cd redis-6.2.6
```

运行编译命令：

```sh
make && make install
```

如果没有出错，应该就安装成功了。

默认的安装路径是在 `/usr/local/bin`目录下：

该目录以及默认配置到环境变量，因此可以在任意目录下运行这些命令。其中：

- redis-cli：是redis提供的命令行客户端
- redis-server：是redis的服务端启动脚本
- redis-sentinel：是redis的哨兵启动脚本

## 1.3.启动

redis的启动方式有很多种，例如：

- 默认启动
- 指定配置启动
- 开机自启

### 1.3.1.默认启动

安装完成后，在任意目录输入redis-server命令即可启动Redis：

```
redis-server
```

这种启动属于`前台启动`，会阻塞整个会话窗口，窗口关闭或者按下`CTRL + C`则Redis停止。不推荐使用。

### 1.3.2.指定配置启动

如果要让Redis以`后台`方式启动，则必须修改Redis配置文件，就在之前解压的redis安装包下（`/usr/local/src/redis-6.2.6`），名字叫redis.conf：

先将这个配置文件备份一份：

```
cp redis.conf redis.conf.bck
```


然后修改redis.conf文件中的一些配置：

```properties
# 允许访问的地址，默认是127.0.0.1，会导致只能在本地访问。修改为0.0.0.0则可以在任意IP访问，生产环境不要设置为0.0.0.0
bind 0.0.0.0
# 守护进程，修改为yes后即可后台运行
daemonize yes 
# 密码，设置后访问Redis必须输入密码
requirepass 123456
```

启动Redis：

```sh
# 进入redis安装目录 
cd /usr/local/src/redis-6.2.6
# 启动
redis-server redis.conf
```


停止服务：

```sh
# 利用redis-cli来执行 shutdown 命令，即可停止 Redis 服务，
# 因为之前配置了密码，因此需要通过 -u 来指定密码
redis-cli -u 123456 shutdown
```

### 1.3.3.开机自启

也可以通过配置来实现开机自启。

首先，新建一个系统服务文件：

```sh
vi /etc/systemd/system/redis.service
```

内容如下：

```conf
[Unit]
Description=redis-server
After=network.target

[Service]
Type=forking
ExecStart=/usr/local/bin/redis-server /usr/local/src/redis-6.2.6/redis.conf
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

然后重载系统服务：

```sh
systemctl daemon-reload
```

现在可以用下面这组命令来操作redis了：

```sh
# 启动
systemctl start redis
# 停止
systemctl stop redis
# 重启
systemctl restart redis
# 查看状态
systemctl status redis
```

执行下面的命令，可以让redis开机自启：

```sh
systemctl enable redis
```

# 2.Redis主从集群

## 2.1.集群结构

一个主节点，两个从节点,共包含三个节点，

这里在同一台虚拟机中开启3个redis实例，模拟主从集群，信息如下：

|       IP        | PORT |  角色  |
| :-------------: | :--: | :----: |
| 192.168.150.101 | 7001 | master |
| 192.168.150.101 | 7002 | slave  |
| 192.168.150.101 | 7003 | slave  |

## 2.2.准备实例和配置

要在同一台虚拟机开启3个实例，必须准备三份不同的配置文件和目录，配置文件所在目录也就是工作目录。

1）创建目录

我们创建三个文件夹，名字分别叫7001、7002、7003：

```sh
# 进入/tmp目录
cd /tmp
# 创建目录
mkdir 7001 7002 7003
```

2）恢复原始配置

修改redis-6.2.4/redis.conf文件，将其中的持久化模式改为默认的RDB模式，AOF保持关闭状态。

```properties
# 开启RDB
# save ""
save 3600 1
save 300 100
save 60 10000

# 关闭AOF
appendonly no
```

3）拷贝配置文件到每个实例目录

然后将redis-6.2.4/redis.conf文件拷贝到三个目录中（在/tmp目录执行下列命令）：

```sh
# 方式一：逐个拷贝
cp redis-6.2.4/redis.conf 7001
cp redis-6.2.4/redis.conf 7002
cp redis-6.2.4/redis.conf 7003

# 方式二：管道组合命令，一键拷贝
echo 7001 7002 7003 | xargs -t -n 1 cp redis-6.2.4/redis.conf
```

4）修改每个实例的端口、工作目录

修改每个文件夹内的配置文件，将端口分别修改为7001、7002、7003，将rdb文件保存位置都修改为自己所在目录（在/tmp目录执行下列命令）：

```sh
sed -i -e 's/6379/7001/g' -e 's/dir .\//dir \/tmp\/7001\//g' 7001/redis.conf
sed -i -e 's/6379/7002/g' -e 's/dir .\//dir \/tmp\/7002\//g' 7002/redis.conf
sed -i -e 's/6379/7003/g' -e 's/dir .\//dir \/tmp\/7003\//g' 7003/redis.conf
```

5）修改每个实例的声明IP

虚拟机本身有多个IP，为了避免将来混乱，需要在redis.conf文件中指定每一个实例的绑定ip信息，格式如下：

```properties
# redis实例的声明 IP
replica-announce-ip 192.168.150.101
```

每个目录都要改，一键完成修改（在/tmp目录执行下列命令）：

```sh
# 逐一执行
sed -i '1a replica-announce-ip 192.168.150.101' 7001/redis.conf
sed -i '1a replica-announce-ip 192.168.150.101' 7002/redis.conf
sed -i '1a replica-announce-ip 192.168.150.101' 7003/redis.conf

# 或者一键修改
printf '%s\n' 7001 7002 7003 | xargs -I{} -t sed -i '1a replica-announce-ip 192.168.150.101' {}/redis.conf
```

## 2.3.启动

分别启动3个redis实例，启动命令：

```sh
# 第1个
redis-server 7001/redis.conf
# 第2个
redis-server 7002/redis.conf
# 第3个
redis-server 7003/redis.conf
```

如果要一键停止，可以运行下面命令：

```sh
printf '%s\n' 7001 7002 7003 | xargs -I{} -t redis-cli -p {} shutdown
```





## 2.4.开启主从关系

现在三个实例还没有任何关系，要配置主从可以使用replicaof 或者slaveof（5.0以前）命令。

有临时和永久两种模式：

- 修改配置文件（永久生效）
  - 在redis.conf中添加一行配置：`slaveof <masterip> <masterport>`
- 使用redis-cli客户端连接到redis服务，执行slaveof命令（重启后失效）：

```sh
slaveof <masterip> <masterport>
```

在5.0以后新增命令replicaof，与salveof效果一致。

通过redis-cli命令连接7002，执行下面命令：

```sh
# 连接 7002
redis-cli -p 7002
# 执行slaveof
slaveof 192.168.150.101 7001
```

通过redis-cli命令连接7003，执行下面命令：

```sh
# 连接 7003
redis-cli -p 7003
# 执行slaveof
slaveof 192.168.150.101 7001
```

然后连接 7001节点，查看集群状态：

```sh
# 连接 7001
redis-cli -p 7001
# 查看状态
info replication
```

## 2.5.测试

执行下列操作以测试：

- 利用redis-cli连接7001，执行```set num 123```
- 利用redis-cli连接7002，执行```get num```，再执行```set num 666```
- 利用redis-cli连接7003，执行```get num```，再执行```set num 888```

可以发现，只有在7001这个master节点上可以执行写操作，7002和7003这两个slave节点只能执行读操作。

# 3.搭建哨兵集群

## 3.1.集群结构

搭建一个三节点形成的Sentinel集群，来监管之前的Redis主从集群。

三个sentinel实例信息如下：

| 节点  |       IP        | PORT  |
|-----|:---------------:|:-----:|
| s1  | 192.168.150.101 | 27001 |
| s2  | 192.168.150.101 | 27002 |
| s3  | 192.168.150.101 | 27003 |

## 3.2.准备实例和配置

要在同一台虚拟机开启3个实例，必须准备三份不同的配置文件和目录，配置文件所在目录也就是工作目录。

我们创建三个文件夹，名字分别叫s1、s2、s3：

```sh
# 进入/tmp目录
cd /tmp
# 创建目录
mkdir s1 s2 s3
```

然后在s1目录创建一个sentinel.conf文件，添加下面的内容：

```ini
port 27001
sentinel announce-ip 192.168.150.101
sentinel monitor mymaster 192.168.150.101 7001 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 60000
dir "/tmp/s1"
```

解读：

- `port 27001`：是当前sentinel实例的端口
- `sentinel monitor mymaster 192.168.150.101 7001 2`：指定主节点信息
  - `mymaster`：主节点名称，自定义，任意写
  - `192.168.150.101 7001`：主节点的ip和端口
  - `2`：选举master时的quorum值



然后将s1/sentinel.conf文件拷贝到s2、s3两个目录中（在/tmp目录执行下列命令）：

```sh
# 方式一：逐个拷贝
cp s1/sentinel.conf s2
cp s1/sentinel.conf s3
# 方式二：管道组合命令，一键拷贝
echo s2 s3 | xargs -t -n 1 cp s1/sentinel.conf
```



修改s2、s3两个文件夹内的配置文件，将端口分别修改为27002、27003：

```sh
sed -i -e 's/27001/27002/g' -e 's/s1/s2/g' s2/sentinel.conf
sed -i -e 's/27001/27003/g' -e 's/s1/s3/g' s3/sentinel.conf
```



## 3.3.启动

为了方便查看日志，打开3个ssh窗口，分别启动3个redis实例，启动命令：

```sh
# 第1个
redis-sentinel s1/sentinel.conf
# 第2个
redis-sentinel s2/sentinel.conf
# 第3个
redis-sentinel s3/sentinel.conf
```


## 3.4.测试

尝试让master节点7001宕机，查看sentinel日志：

查看7002，7003的日志

# 4.搭建分片集群

## 4.1.集群结构

分片集群需要的节点数量较多，搭建一个最小的分片集群，包含3个master节点，每个master包含一个slave节点，结构如下：

在同一台虚拟机中开启6个redis实例，模拟分片集群，信息如下：

|       IP        | PORT |  角色  |
| :-------------: | :--: | :----: |
| 192.168.150.101 | 7001 | master |
| 192.168.150.101 | 7002 | master |
| 192.168.150.101 | 7003 | master |
| 192.168.150.101 | 8001 | slave  |
| 192.168.150.101 | 8002 | slave  |
| 192.168.150.101 | 8003 | slave  |


## 4.2.准备实例和配置

删除之前的7001、7002、7003这几个目录，重新创建出7001、7002、7003、8001、8002、8003目录：

```sh
# 进入/tmp目录
cd /tmp
# 删除旧的，避免配置干扰
rm -rf 7001 7002 7003
# 创建目录
mkdir 7001 7002 7003 8001 8002 8003
```

在/tmp下准备一个新的redis.conf文件，内容如下：

```properties
port 6379
# 开启集群功能
cluster-enabled yes
# 集群的配置文件名称，不需要我们创建，由redis自己维护
cluster-config-file /tmp/6379/nodes.conf
# 节点心跳失败的超时时间
cluster-node-timeout 5000
# 持久化文件存放目录
dir /tmp/6379
# 绑定地址
bind 0.0.0.0
# 让redis后台运行
daemonize yes
# 注册的实例ip
replica-announce-ip 192.168.150.101
# 保护模式
protected-mode no
# 数据库数量
databases 1
# 日志
logfile /tmp/6379/run.log
```

将这个文件拷贝到每个目录下：

```sh
# 进入/tmp目录
cd /tmp
# 执行拷贝
echo 7001 7002 7003 8001 8002 8003 | xargs -t -n 1 cp redis.conf
```

修改每个目录下的redis.conf，将其中的6379修改为与所在目录一致：

```sh
# 进入/tmp目录
cd /tmp
# 修改配置文件
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t sed -i 's/6379/{}/g' {}/redis.conf
```

## 4.3.启动

因为已经配置了后台启动模式，所以可以直接启动服务：

```sh
# 进入/tmp目录
cd /tmp
# 一键启动所有服务
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t redis-server {}/redis.conf
```

通过ps查看状态：

```sh
ps -ef | grep redis
```

如果要关闭所有进程，可以执行命令：

```sh
ps -ef | grep redis | awk '{print $2}' | xargs kill
```

或者（推荐这种方式）：

```sh
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t redis-cli -p {} shutdown
```

## 4.4.创建集群

虽然服务启动了，但是目前每个服务之间都是独立的，没有任何关联。

需要执行命令来创建集群，在Redis5.0之前创建集群比较麻烦，5.0之后集群管理命令都集成到了redis-cli中。

1）Redis5.0之前

Redis5.0之前集群命令都是用redis安装包下的src/redis-trib.rb来实现的。因为redis-trib.rb是有ruby语言编写的所以需要安装ruby环境。

 ```sh
 # 安装依赖
 yum -y install zlib ruby rubygems
 gem install redis
 ```

然后通过命令来管理集群：

```sh
# 进入redis的src目录
cd /tmp/redis-6.2.4/src
# 创建集群
./redis-trib.rb create --replicas 1 192.168.150.101:7001 192.168.150.101:7002 192.168.150.101:7003 192.168.150.101:8001 192.168.150.101:8002 192.168.150.101:8003
```

2）Redis5.0以后

集群管理以及集成到了redis-cli中，格式如下：

```sh
redis-cli --cluster create --cluster-replicas 1 192.168.150.101:7001 192.168.150.101:7002 192.168.150.101:7003 192.168.150.101:8001 192.168.150.101:8002 192.168.150.101:8003
```

命令说明：

- `redis-cli --cluster`或者`./redis-trib.rb`：代表集群操作命令
- `create`：代表是创建集群
- `--replicas 1`或者`--cluster-replicas 1` ：指定集群中每个master的副本个数为1，此时`节点总数 ÷ (replicas + 1)` 得到的就是master的数量。因此节点列表中的前n个就是master，其它节点都是slave节点，随机分配到不同master

运行后输入yes，则集群开始创建

通过命令可以查看集群状态：

```sh
redis-cli -p 7001 cluster nodes
```


## 4.5.测试

尝试连接7001节点，存储一个数据：

```sh
# 连接
redis-cli -p 7001
# 存储数据
set num 123
# 读取数据
get num
# 再次存储， 可能会出错因为插入数据会插入随机插槽中
set a 1
```

集群操作时，需要给`redis-cli`加上`-c`参数才可以：

```sh
redis-cli -c -p 7001
```


# 配置文件

```properties
# 允许访问的地址，默认是127.0.0.1，会导致只能在本地访问。修改为0.0.0.0则可以在任意IP访问，生产环境不要设置为0.0.0.0
bind 0.0.0.0

# 守护进程，修改为yes后即可后台运行
daemonize yes 

# 密码，设置后访问Redis必须输入密码
requirepass 123456

# 监听的端口
port 6379

# 工作目录，默认是当前目录，也就是运行redis-server时的命令，日志、持久化等文件会保存在这个目录
dir .

# 数据库数量，设置为1，代表只使用1个库，默认有16个库，编号0~15
databases 1

# 设置redis能够使用的最大内存
maxmemory 512mb

# 日志文件，默认为空，不记录日志，可以指定日志文件名
logfile "redis.log"

# 慢查询阈值，单位是微秒。默认是10000，建议1000
slowlog-log-slower-than：5000

# 慢查询日志（本质是一个队列）的长度。默认是128，建议1000
slowlog-max-len：1000

# 通过rename-command 保证命令不被使用，此时在redis-cli中会找不到config命令
rename-command CONFIG ajksdhajksdhkjahsdla

# 如果处理大量的bigvalue，会导致输出结果过多，输出缓存区过大，会导致redis直接断开，默认配置下没有大小限制
# client-output-buffer-limit <class> <hard_limit> <soft_limit> <soft_seconds> 
# normal 代表
client-output-buffer-limit normal 256mb 128mb 60

######################## RDB #############################
# 900秒内,如果至少有1个key被修改,,则执行bgsave,如果是save "" 则表示禁用RDB 
save 900 1
save 300 10
save 60 10000

# 是否压缩 ,建议不开启,压缩也会消耗cpu,磁盘不值钱 
rdbcompression no

# RDB文件名称
dbfilename dump.rdb

############## AOF ###############
# 是否开启AOF功能,默认是no 
appendonly yes

# AOF文件的名称 后缀是aof
appendfilename "appendonly.aof"

# 表示每执行一次写命令，立即记录到AOF文件 性能不好，安全
appendfsync always
# 写命令执行完先放入AOF缓冲区，然后表示每隔1秒将缓冲区数据写到AOF文件，是默认方案 性能适中
appendfsync everysec
# 写命令执行完先放入AOF缓冲区，由操作系统决定何时将缓冲区内容写回磁盘，性能号，不安全
appendfsync no

# A0F文件比上次文件增长超过多少百分比则触发重写
auto-aof-rewrite-percentage 100

# AOF文件体积最小多大以上才触发重写
auto-aof-rewrite-min-size 64mb

# 禁止在rewrite期间做aof，避免因AOF引起的阻塞
no-appendfsync-on-rewrite yes

# 主从复制的repl_backlog_buf，如果太小可能导致频繁的全量复制，影响性能。通过replbacklog-size来设置，默认1mb
repl_backlog_buf 3mb

########## 配置从节点 #########
slaveof <masterip> <masterport>

# 从节点连接主节点密码
masterauth <password>

# yes启用无磁盘复制，避免全量同步时的磁盘IO 通过数据流同步,需要足够的网络带宽 
repl-diskless-sync no


########## 开启集群功能 ###########
cluster-enabled yes
# 集群的配置文件名称，不需要我们创建，由redis自己维护
cluster-config-file /tmp/6379/nodes.conf
# 节点心跳失败的超时时间
cluster-node-timeout 5000
# 持久化文件存放目录
dir /tmp/6379
# 绑定地址
bind 0.0.0.0
# 让redis后台运行
daemonize yes
# 注册的实例ip
replica-announce-ip 192.168.150.101
# 保护模式
protected-mode no
# 数据库数量
databases 1
# 日志
logfile /tmp/6379/run.log
# 全插槽可用，如果为yes代表如果有部分插槽不可用，则整个集群停止工作（数据完整性高，但可用性降低）
cluster-require-full-coverage no
```


# Docker运行

镜像地址: https://hub.docker.com/_/redis 

```shell
docker run -d --name myredis -p 6379:6379 redis --requirepass "123456"

# 通过电脑自带的redis-cli进行连接
redis-cli -h 127.0.0.1 -a 123456
# 或者先连接再认证
redis-cli -h 127.0.0.1
auth 123456
```

# 3.Redis客户端

安装完成Redis，就可以操作Redis，实现数据的CRUD了。这需要用到Redis客户端，包括：

- 命令行客户端
- 编程客户端

## 3.1.Redis命令行客户端

在mac中仅安装redis-cli

```shell
brew tap ringohub/redis-cli
brew update && brew doctor
brew install redis-cli
```

Redis安装完成后就自带了命令行客户端：redis-cli，使用方式如下：

```sh
redis-cli [options] [commonds]
```

其中常见的options有：

- `-h 127.0.0.1`：指定要连接的redis节点的IP地址，默认是127.0.0.1
- `-p 6379`：指定要连接的redis节点的端口，默认是6379
- `-a 123456`：指定redis的访问密码 

其中的commands就是Redis的操作命令，例如：

- `ping`：与redis服务端做心跳测试，服务端正常会返回`pong`

不指定command时，会进入`redis-cli`的交互控制台：

```
auth 123456
```

Redis默认有16个仓库，编号从0至15.  通过配置文件可以设置仓库数量，但是不超过16，并且不能自定义仓库名称。

如果是基于redis-cli连接Redis服务，可以通过select命令来选择数据库：

```sh
# 选择 0号库
select 0
```

## 3.2.Java客户端

Java连接Redis看这里： [redis-java.md](./redis-java.md)




