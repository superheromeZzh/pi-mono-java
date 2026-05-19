# 设备巡检 · 三套规则引擎上传包

本目录自包含 **三种规则引擎**、各自规则文件、共用 mock 取数与夹具，路径已改为相对本包，可直接打包上传。

## 目录结构

```
rule-engines-pack/
├── README.md                 # 本说明
├── requirements.txt          # ② rule-engine 依赖
├── shared/                   # 三套共用
│   ├── mock_api_server.py    # 取数 mock（端口 18080）
│   ├── mock_fixtures/        # 按规则 id 命名的时序夹具 *.json
│   └── validate_rules_json.py
├── 01-json-ast/              # ① 自研 JSON AST + eval_expr
│   ├── judge_rules.py
│   ├── validate_output.py    # 结构 + AST 校验
│   └── rules/rules.json
├── 02-rule-engine-pypi/      # ② PyPI rule-engine 表达式
│   ├── judge_rules_re.py
│   ├── requirements.txt
│   └── rules/rules_re.json
└── 03-drools/                # ③ Drools DRL + HTTP 服务
    ├── judge_rules_drools.py
    ├── rules/
    │   ├── ruleset.json      # 元数据（窗、阈值、点位）
    │   └── device_inspection.drl
    ├── scripts/drools-runner/  # 未起 HTTP 时的 Maven 回退（可选）
    └── services/drools-inspection-api/  # Spring Boot（端口 18081）
```

## 环境变量（常用）

| 变量 | 默认 | 说明 |
|------|------|------|
| `DEVICE_INSPECTION_API_URL` | `http://127.0.0.1:18080/fetch` | mock 取数 |
| `DEVICE_INSPECTION_FIXTURES_DIR` | `shared/mock_fixtures` | 夹具目录 |
| `DROOLS_API_BASE_URL` | `http://127.0.0.1:18081` | Drools HTTP 基址 |

## 运行示例

在 **`rule-engines-pack` 目录** 下执行（或把该目录加入工作目录）。

### 启动 mock 取数（①②③ 都需要）

```bash
python shared/mock_api_server.py
```

### ① JSON AST

```bash
python 01-json-ast/judge_rules.py --rules 01-json-ast/rules/rules.json --json
python 01-json-ast/validate_output.py 01-json-ast/rules/rules.json
```

### ② rule-engine

```bash
pip install -r requirements.txt
python 02-rule-engine-pypi/judge_rules_re.py --rules 02-rule-engine-pypi/rules/rules_re.json --json
```

### ③ Drools

先构建并启动 Java 服务：

```bash
cd 03-drools/services/drools-inspection-api
mvn -DskipTests package
java -jar target/drools-inspection-api-1.0.0.jar
```

再巡检：

```bash
python 03-drools/judge_rules_drools.py --json
```

## 规则文件说明

| 引擎 | 规则文件 | 触发条件字段 |
|------|----------|----------------|
| ① | `01-json-ast/rules/rules.json` | `trigger.expr`（JSON AST） |
| ② | `02-rule-engine-pypi/rules/rules_re.json` | `trigger.rule_engine`（字符串） |
| ③ | `03-drools/rules/ruleset.json` + `device_inspection.drl` | 条件在 DRL，`id` 与 `rule "..."` 同名 |

**mock 夹具**：`shared/mock_fixtures/<规则id>.json` 的 `requestId` 须与规则 `id` 一致。

## 可选：OpenClaw 真源

各脚本仍会 **优先** 读取（若存在）：

- `C:\Users\Jason\.openclaw\workspace\rules\rules.json`（①）
- `...\rules\rules_re.json`（②）

上传部署时可忽略，仅用本包内 `rules/` 即可。

## 不包含

- `agents/excel_rules_agent`（Excel → 模型 → rules.json，在仓库 `agents/` 下）
- Cursor `.cursor/skills/` 原文（本包为拷贝快照）
