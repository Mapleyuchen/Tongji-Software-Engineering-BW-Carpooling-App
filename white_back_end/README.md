# 接口文档

https://apifox.com/apidoc/shared-88f04649-cafb-4306-8871-554ebc0f0529/269606053e0

# 后端启动说明

后端会话模块现在依赖 Redis、Flask-SocketIO、Celery worker 和 Celery beat。开发时建议打开4个终端窗口分别启动。

## 1、安装依赖

```
cd white_back_end
pip install -r requirements.txt
```

## 2、启动 Redis

推荐使用 Docker：

```
docker run --name carpool-redis -p 6379:6379 -d redis:7-alpine
```

之后再次启动同一个 Redis 容器：

```
docker start carpool-redis
```

## 3、启动 Flask SocketIO 服务

```
cd white_back_end
python run.py
```

服务默认监听：`http://localhost:8443`

## 4、启动 Celery worker

```
cd white_back_end
celery -A app.celery_app.celery worker --loglevel=info --pool=solo
```

## 5、启动 Celery beat

```
cd white_back_end
celery -A app.celery_app.celery beat --loglevel=info
```
