
# 系统命令

命令查询：https://www.redis.com.cn/commands

## 连接
```shell
# 通过电脑自带的redis-cli进行连接
redis-cli -h 127.0.0.1 -p 6379 -a 123456 --raw

# redis-cli进行连接集群
redis-cli -h 127.0.0.1 -p 6379 -a 123456 -c --raw

# 或者先连接再认证
redis-cli -h 127.0.0.1
auth 123456
```

## 保存数据

```shell
# 由Redis主进程来执行RDB，会阻塞所有命令
redis> save

# 开启子进程执行RDB，避免主进程受到影响
redis> bgsave
```

## 显示信息

https://www.redis.com.cn/commands/info.html

```shell
# 查看内存信息
redis> INFO memory

# 查看客户端信息
redis> INFO client

# 查看内存分配
redis> MEMORY stats



# 查看从节点状态
redis> INFO replication
```

## Redis通用命令

通用指令是部分数据类型的，都可以使用的指令，常见的有：

- KEYS：查看符合模板的所有key
- DEL：删除一个指定的key
- EXISTS：判断key是否存在
- EXPIRE：给一个key设置有效期，有效期到期时该key会被自动删除
- TTL：查看一个KEY的剩余有效期

## 慢查询命令

```shell
# 让 slow log 记录所有查询时间大于等于 100 微秒的查询
redis> CONFIG SET slowlog-log-slower-than 100

# 以下命令让 slow log 最多保存 1000 条日志
redis> CONFIG SET slowlog-max-len 1000

redis> CONFIG GET slowlog-log-slower-than
1) "slowlog-log-slower-than"
2) "1000"

redis> CONFIG GET slowlog-max-len
1) "slowlog-max-len"
2) "1000"

redis> SLOWLOG GET
1) 1) (integer) 12                      # 唯一性(unique)的日志标识符
   2) (integer) 1324097834              # 被记录命令的执行时间点，以 UNIX 时间戳格式表示
   3) (integer) 16                      # 查询执行时间，以微秒为单位
   4) 1) "CONFIG"                       # 执行的命令，以数组的形式排列
      2) "GET"                          # 这里完整的命令是 CONFIG GET slowlog-log-slower-than
      3) "slowlog-log-slower-than"

2) 1) (integer) 11
   2) (integer) 1324097825
   3) (integer) 42
   4) 1) "CONFIG"
      2) "GET"
      3) "*"

redis> SLOWLOG LEN     # 查看当前慢查询的数量
(integer) 14

redis> SLOWLOG RESET   # 清空当前慢查询记录
OK

redis> SLOWLOG LEN
(integer) 0
```

```shell
127.0.0.1:6379> keys *
1) "name"
2) "age"

127.0.0.1:6379> keys a* # 查询以a开头的key
1) "age"

127.0.0.1:6379> MSET k1 v1 k2 v2 k3 v3 #批量添加数据
OK

127.0.0.1:6379> del k1 k2 k3 k4
(integer) 3   #此处返回的是成功删除的key，由于redis中只有k1,k2,k3 所以只成功删除3个，最终返回

127.0.0.1:6379> exists age  # key存在返回1 不存在返回2
(integer) 1  

127.0.0.1:6379> ttl age   #当这个key过期了或不存在，那么此时查询出来就是-2 
(integer) -2  

#如果没有设置过期时间 ttl的返回值就是-1
127.0.0.1:6379> set age 10 
OK

127.0.0.1:6379> ttl age
(integer) -1  

# 暴力删除带有特定前缀的所有key
redis-cli -h 127.0.0.1 -a 123456 keys "login:token:*" | xargs redis-cli -h 127.0.0.1 -a 123456 del

# 通过SCAN方式删除
redis-cli --scan --pattern "login:token:*" | xargs -L 2000 redis-cli del
```
# Redis数据类型

- String
- Hash
- List
- Set
- SortedSet
- GEO
- BitMap
- HyperLog

## 2.2.1.String类型

String的常见命令有：

- SET：添加或者修改已经存在的一个String类型的键值对
- GET：根据key获取String类型的value
- MSET：批量添加多个String类型的键值对
- MGET：根据多个key获取多个String类型的value
- INCR：让一个整型的key自增1
- INCRBY:让一个整型的key自增并指定步长
- INCRBYFLOAT：让一个浮点类型的数字自增并指定步长
- SETNX：添加一个String类型的键值对，前提是这个key不存在，否则不执行
- SETEX：添加一个String类型的键值对，并且指定有效期

```shell
127.0.0.1:6379> set name Rose  # 原来不存在就是新增
OK

127.0.0.1:6379> get name 
"Rose"

127.0.0.1:6379> set name Jack # 原来存在，就是修改
OK

127.0.0.1:6379> get name
"Jack"

127.0.0.1:6379> MSET k1 v1 k2 v2 k3 v3
OK

127.0.0.1:6379> MGET name age k1 k2 k3
1) "Jack" #之前存在的name
2) "10"   #之前存在的age
3) "v1"
4) "v2"
5) "v3"

127.0.0.1:6379> set name Jack  #设置名称 如果key不存在，则添加成功
OK
127.0.0.1:6379> setnx name lisi # 由于name已经存在，所以lisi的操作失败
(integer) 0
127.0.0.1:6379> get name
"Jack"

127.0.0.1:6379> setex name 10 jack # 设置过期时间
OK
127.0.0.1:6379> ttl name
(integer) 8

# 设置互斥锁 NX表示互斥 EX设置超时时间
SET lock thread1 NX EX 10
```

## 2.2.2 Hash类型

Hash类型，也叫散列，其value是一个无序字典，类似于Java中的HashMap结构。

String结构是将对象序列化为JSON字符串后存储，当需要修改对象某个字段时很不方便

Hash结构可以将对象中的每个字段独立存储，可以针对单个字段做CRUD

Hash的常见命令有：

- HSET key field value：添加或者修改hash类型key的field的值
- HGET key field：获取一个hash类型key的field的值
- HMSET：批量添加多个hash类型key的field的值
- HMGET：批量获取多个hash类型key的field的值
- HGETALL：获取一个hash类型的key中的所有的field和value
- HKEYS：获取一个hash类型的key中的所有的field
- HINCRBY:让一个hash类型key的字段值自增并指定步长
- HSETNX：添加一个hash类型的key的field值，前提是这个field不存在，否则不执行

```shell
127.0.0.1:6379> HSET heima:user:3 name Lucy # 大key是 heima:user:3 小key是name，小value是Lucy
(integer) 1
127.0.0.1:6379> HSET heima:user:3 age 21  # 如果操作不存在的数据，则是新增
(integer) 1
127.0.0.1:6379> HSET heima:user:3 age 17 # 如果操作存在的数据，则是修改
(integer) 0

# 一次设置多个Hash值
127.0.0.1:6379> HMSET heima:user:4 name LiLei age 20 sex man
OK
127.0.0.1:6379> HMGET heima:user:4 name age sex
1) "LiLei"
2) "20"
3) "man"
```

## 2.2.3 List类型

Redis中的List类型与Java中的LinkedList类似，可以看做是一个双向链表结构。既可以支持正向检索和也可以支持反向检索。

特征也与LinkedList类似：

- 有序
- 元素可以重复
- 插入和删除快
- 查询速度一般

常用来存储一个有序数据，例如：朋友圈点赞列表，评论列表等。

List的常见命令有：

- LPUSH key element ... ：向列表左侧插入一个或多个元素
- LPOP key：移除并返回列表左侧的第一个元素，没有则返回nil
- RPUSH key element ... ：向列表右侧插入一个或多个元素
- RPOP key：移除并返回列表右侧的第一个元素
- LRANGE key star end：返回一段角标范围内的所有元素
- BLPOP和BRPOP：与LPOP和RPOP类似，只不过在没有元素时等待指定时间，而不是直接返回nil

## 2.2.4 Set类型

Redis的Set结构与Java中的HashSet类似，可以看做是一个value为null的HashMap。因为也是一个hash表，因此具备与HashSet类似的特征：

- 无序
- 元素不可重复
- 查找快
- 支持交集、并集、差集等功能

Set的常见命令有：

- SADD key member ... ：向set中添加一个或多个元素
- SREM key member ... : 移除set中的指定元素
- SCARD key： 返回set中元素的个数
- SISMEMBER key member：判断一个元素是否存在于set中
- SMEMBERS：获取set中的所有元素
- SINTER key1 key2 ... ：求key1与key2的交集

## 2.2.5 SortedSet类型

Redis的SortedSet是一个可排序的set集合，与Java中的TreeSet有些类似，但底层数据结构却差别很大。

SortedSet中的每一个元素都带有一个score属性，可以基于score属性对元素排序，底层的实现是一个跳表（SkipList）加 hash表。

SortedSet具备下列特性：

- 可排序
- 元素不重复
- 查询速度快

因为SortedSet的可排序特性，经常被用来实现排行榜这样的功能。

SortedSet的常见命令有：

- ZADD key score member：添加一个或多个元素到sorted set ，如果已经存在则更新其score值
- ZREM key member：删除sorted set中的一个指定元素
- ZSCORE key member : 获取sorted set中的指定元素的score值
- ZRANK key member：获取sorted set 中的指定元素的排名
- ZCARD key：获取sorted set中的元素个数
- ZCOUNT key min max：统计score值在给定范围内的所有元素的个数
- ZINCRBY key increment member：让sorted set中的指定元素自增，步长为指定的increment值
- ZRANGE key min max：按照score排序后，获取指定排名范围内的元素
- ZRANGEBYSCORE key min max：按照score排序后，获取指定score范围内的元素
- ZDIFF.ZINTER.ZUNION：求差集.交集.并集

注意：所有的排名默认都是升序，如果要降序则在命令的Z后面添加REV即可，例如：

- **升序**获取sorted set 中的指定元素的排名：ZRANK key member
- **降序**获取sorted set 中的指定元素的排名：ZREVRANK key member

## 2.2.6 基于Stream的消息队列

```shell
redis> XADD mystream * name Sara surname OConnor
"1675577014682-0"
redis> XADD mystream * field1 value1 field2 value2 field3 value3
"1675577014683-0"
redis> XLEN mystream
(integer) 2
redis> XRANGE mystream - +
1) 1) "1675577014682-0"
   2) 1) "name"
      2) "Sara"
      3) "surname"
      4) "OConnor"
2) 1) "1675577014683-0"
   2) 1) "field1"
      2) "value1"
      3) "field2"
      4) "value2"
      5) "field3"
      6) "value3"
```

## 2.2.7 基于Stream的消息队列-消费者组

创建消费者组: `XGROUP CREATE s1 g1 0`
- key:队列名称
- groupName:消费者组名称
- ID:起始ID标示,$代表队列中最后一个消息,O则代表队列中第一个消息
- MKSTREAM:队列不存在时自动创建队列

其它常见命令:
```shell
# 删除指定的消费者组
XGROUP DESTORY key groupName

# 给指定的消费者组添加消费者
XGROUP CREATECONSUMER key groupname consumername

# 删除消费者组中的指定消费者
XGROUP DELCONSUMER key groupname consumername
```

消费者组读消息
```shell
XREADGROUP GROUP g1 consumer COUNT 1 BLOCK 2000 STREAMS s1 >
```
- group：消费组名称
- consumer：消费者名称，如果消费者不存在，会自动创建一个消费者 count：本次查询的最大数量
- BL0CK milliseconds：当没有消息时最长等待时间
- NOACK：无需手动ACK，获取到消息后自动确认
- STREAMS key：指定队列名称
- ID：获取消息的起始ID： “>“：从下一个未消费的消息开始

消息确认返回
```shell
XACK s1 g1 ID [ID..]
```

查看pending-list
```shell

```


