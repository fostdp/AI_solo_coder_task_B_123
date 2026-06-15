# 🏯 古代兵营士兵膳食营养监测与疫病预警系统

> 汉代居延遗址复原兵营的膳食营养监测与肠道传染病暴发预警全栈应用

## 系统架构

```
                              ┌─────────────────────────────────────────┐
                              │           Docker Network                │
                              │         (barracks-net)                  │
                              │                                         │
 ┌──────────┐   MQTT    ┌────┴─────┐   Events   ┌──────────────┐       │
 │ Simulator ├─────────►│mqtt-ingest├───────────►│nutrition-rf  │       │
 │ (25士兵)  │  :1883   │  :8081   │            │  :8082       │       │
 └──────────┘          └────┬─────┘            └──────┬───────┘       │
                            │                          │               │
                            │   Events          ┌──────┴───────┐       │
                            ├──────────────────►│outbreak-     │       │
                            │                   │satscan :8083 │       │
                            │                   └──────┬───────┘       │
                            │                          │               │
                       ┌────┴─────┐            ┌──────┴───────┐       │
                       │PostgreSQL│            │alert-broker  │       │
                       │ +PostGIS │◄───────────┤  :8080       │       │
                       │  :5432   │   JPA      │  REST API    │       │
                       └──────────┘            └──────┬───────┘       │
                                                      │               │
                                               ┌──────┴───────┐       │
                                               │  Frontend    │       │
                                               │  Nginx :80   │       │
                                               │  Gzip + D3   │       │
                                               └──────────────┘       │
                                                      │               │
                              ┌─────────────────────────────────────────┘
                              │
                    ┌─────────┴──────────┐
                    │  Monitoring Stack  │
                    │ Prometheus :9090   │
                    │ Grafana    :3000   │
                    └────────────────────┘
```

### 模块间通信

| 事件 | 发布方 | 监听方 | 说明 |
|------|--------|--------|------|
| `NutritionDataReceivedEvent` | mqtt-ingest | nutrition-rf | 营养传感器数据已入库 |
| `FecalDataReceivedEvent` | mqtt-ingest | outbreak-satscan | 粪便检测数据已入库 |
| `NutritionRiskComputedEvent` | nutrition-rf | alert-broker | 营养风险计算完成 |
| `EpidemicAlertTriggeredEvent` | outbreak-satscan | alert-broker | 疫情聚类告警触发 |

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Canvas 2D + D3.js Force-directed + Nginx Gzip |
| API网关 | alert-broker (Spring Boot REST) |
| 消息总线 | Spring Events + MQTT (EMQX 5.5) |
| 算法 | 随机森林(自实现) + SaTScan时空扫描(自实现) |
| 存储 | PostgreSQL 16 + PostGIS 3 + GiST空间索引 + RANGE月分区 |
| 监控 | Micrometer + Prometheus + Grafana |
| 容器 | Docker多阶段构建 + docker-compose编排 |

## 快速部署

### 前置条件

- Docker 24+ & Docker Compose v2+
- 8GB+ 内存（推荐16GB）
- 4核+ CPU

### 一键启动

```bash
# 克隆项目
git clone <repo-url> && cd barracks-monitor

# 启动所有服务
docker compose up -d --build

# 查看服务状态
docker compose ps
```

### 服务端口

| 服务 | 端口 | 用途 |
|------|------|------|
| 前端 (Nginx) | http://localhost:80 | 兵营平面图仪表盘 |
| API (alert-broker) | http://localhost:8080/api | REST API |
| EMQX管理面板 | http://localhost:18083 | MQTT管理 (admin/public) |
| Prometheus | http://localhost:9090 | 指标查询 |
| Grafana | http://localhost:3000 | 可视化 (admin/admin) |
| mqtt-ingest | http://localhost:8081 | 营养数据接入 |
| nutrition-rf | http://localhost:8082 | 营养风险预测 |
| outbreak-satscan | http://localhost:8083 | 疫情扫描检测 |

### 验证部署

```bash
# 检查PostgreSQL连接
docker compose exec postgres pg_isready -U barracks

# 检查PostGIS扩展
docker compose exec postgres psql -U barracks -d barracks -c "SELECT PostGIS_Version();"

# 检查EMQX
curl -s http://localhost:18083/status

# 检查API健康
curl http://localhost:8080/api/actuator/health

# 检查Prometheus指标
curl http://localhost:8080/api/actuator/prometheus | head -5
```

## 模拟器使用

模拟器持续上报5座兵营25名士兵的传感器数据，每2小时一个周期。

### 正常模式

```bash
# Docker内运行（默认2小时间隔）
docker compose up simulator -d

# 本地运行（快速测试，60秒间隔）
python mqtt-simulator/sensor_simulator.py continuous --host localhost --interval 60
```

### 🍋 坏血病注入模式 (`--scurvy=1`)

启用后，所有士兵的维生素C摄入降至 2~35mg/天（正常40~120mg/天），远低于60mg/天的告警阈值，60%概率伴随蛋白质不足。系统应触发**高风险营养告警**。

```bash
# Docker方式：设置环境变量
SCURVY=1 docker compose up simulator -d

# 或修改 docker-compose.yml:
#   environment:
#     SCURVY: "1"

# 本地命令行
python mqtt-simulator/sensor_simulator.py continuous --scurvy=1 --interval 60
```

### 模拟器参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `mode` | continuous | continuous / single / historical |
| `--host` | localhost | MQTT Broker地址 |
| `--port` | 1883 | MQTT Broker端口 |
| `--interval` | 7200 | 上报间隔秒数（7200=2小时） |
| `--scurvy` | 0 | 1=注入坏血病（维生素C<35mg/天） |
| `--cycles` | 0 | 运行周期数，0=无限 |
| `--days` | 7 | 历史模式回溯天数 |

### 单次快速测试

```bash
python mqtt-simulator/sensor_simulator.py single --host localhost
```

### 历史数据回放

```bash
python mqtt-simulator/sensor_simulator.py historical --days 3 --host localhost
```

## 告警规则

| 条件 | 阈值 | 推送渠道 |
|------|------|---------|
| 维生素C摄入不足 | < 60mg/天 | 企业微信 + 短信 |
| 肠道感染阳性率 | > 20% | 企业微信 + 短信 |

## 数据库

### 初始化脚本

1. `database/init.sql` — 建表 + 种子数据（5座兵营、25名士兵、7天膳食）
2. `db/spatial_index.sql` — GiST空间索引 + 覆盖索引 + 部分索引
3. `database/init_postgis.sql` — PostGIS扩展 + 传感器数据月分区表

### 分区策略

`nutrition_sensor_data` 和 `fecal_sensor_data` 按月 RANGE 分区，自动按 `recorded_at` 字段路由：

```sql
CREATE TABLE nutrition_sensor_data_partitioned (
    ...
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

-- 每月一个分区
CREATE TABLE nutrition_data_2024_01 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

## 监控

### Prometheus指标

各Java模块通过 `/actuator/prometheus` 暴露 Micrometer 指标：

- `http_server_requests_seconds` — HTTP请求延迟
- `jvm_memory_used_bytes` — JVM内存使用
- `system_cpu_usage` — CPU使用率
- `hikaricp_connections_active` — 数据库连接池

### Grafana仪表盘

访问 `http://localhost:3000`（admin/admin），自动加载"兵营总览"仪表盘，包含：

- 兵营营养风险分布（饼图）
- 传感器数据接收速率（时间序列）
- 疫情告警次数（计数器）
- JVM内存使用（面积图）
- HTTP请求延迟P50/P95/P99

## 生产配置

各模块 `application-prod.yml` 支持环境变量外置：

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `DB_URL` | PostgreSQL连接串 | jdbc:postgresql://postgres:5432/barracks |
| `DB_USERNAME` | 数据库用户 | barracks |
| `DB_PASSWORD` | 数据库密码 | barracks_secret |
| `MQTT_BROKER` | EMQX地址 | tcp://emqx:1883 |
| `WECHAT_WEBHOOK` | 企业微信Webhook | 空 |
| `SMS_API_KEY` | 短信API密钥 | 空 |
| `ML_MODEL_PATH` | 模型持久化路径 | /var/models/ |
| `LOG_PATH` | 日志目录 | /var/log/barracks/ |

## 项目结构

```
barracks-monitor/
├── backend/
│   ├── pom.xml                    # 父POM聚合
│   ├── common/                    # 共享模块（实体/Repository/DTO/事件）
│   ├── mqtt-ingest/               # MQTT接入（:8081）
│   ├── nutrition-rf/              # 随机森林预测（:8082）
│   ├── outbreak-satscan/          # SaTScan检测（:8083）
│   └── alert-broker/              # 告警推送+REST API（:8080）
├── frontend/
│   ├── index.html                 # 主页面
│   ├── BarrackMap.js              # 兵营平面图组件
│   ├── NutritionLegend.js         # 营养图例组件
│   ├── nginx.conf                 # Nginx Gzip配置
│   └── Dockerfile                 # 前端容器
├── mqtt-simulator/
│   ├── sensor_simulator.py        # MQTT模拟器
│   ├── requirements.txt
│   └── Dockerfile
├── database/
│   ├── init.sql                   # 建表+种子
│   └── init_postgis.sql           # PostGIS+分区
├── db/
│   └── spatial_index.sql          # GiST空间索引
├── monitoring/
│   ├── prometheus.yml             # Prometheus采集配置
│   └── grafana/                   # Grafana仪表盘+Provisioning
├── docker-compose.yml             # 全栈编排
└── README.md
```

## 停止与清理

```bash
# 停止所有服务
docker compose down

# 停止并删除数据卷
docker compose down -v

# 仅重建Java服务
docker compose up -d --build mqtt-ingest nutrition-rf outbreak-satscan alert-broker
```
