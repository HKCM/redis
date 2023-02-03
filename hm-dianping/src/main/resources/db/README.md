# 所有命令都在db目录下执行

准备工作
```shell
# 准备redis容器
docker run -d --name myredis -p 6379:6379 redis --requirepass "123456"

# 准备mysql容器
docker run -d --name mysql8 -e MYSQL_ROOT_PASSWORD=123456 -p 3306:3306 mysql:8.0.31

# 准备mysql中的数据表
mysql -h127.0.0.1 -uroot -p123456 -e "CREATE DATABASE hmdp;"
mysql -h127.0.0.1 -uroot -p123456 -Dhmdp < ./hmdp.sql

# 准备Nginx容器并配置
docker run --name nginx -d -p 8080:80 \
-v $PWD/html:/usr/share/nginx/html \
-v $PWD/nginx.conf:/etc/nginx/nginx.conf \
nginx:1.18.0
```


