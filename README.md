# 神奇松果 Magic Pinecone（monorepo）

中央大學校園資訊 App「神奇松果」的前後端整合專案。

| 資料夾 | 內容 | 技術 |
|---|---|---|
| [`app/`](app/) | Android／iOS App | Kotlin Multiplatform、Compose Multiplatform、SwiftUI、Room、Ktor |
| [`backend/`](backend/) | API 與 NCU 資料爬蟲 | FastAPI、SQLModel、PostgreSQL、Celery + Redis |
| [`docs/api-contract.md`](docs/api-contract.md) | 前後端之間的 API 合約 | |
| [`UPSTREAM.md`](UPSTREAM.md) | 上游來源與這裡做過的變更 | |

## 兩邊怎麼接起來

```
NCU 課務系統 ──(Celery 定期爬取)──▶ PostgreSQL ──▶ FastAPI  /api/v1/catalog/…
                                                        │
                                   App 設定頁「課程資料來源」填入後端位址
                                                        ▼
                    RoutingCourseDataSource ──▶ BackendCourseDataSource ──▶ Room ──▶ 畫面
                              └─(沒填位址)──▶ StaticRemoteCourseDataSource（GitHub 上的靜態 JSON）
```

App 預設仍讀取公開的靜態課程資料，行為與上游相同。在設定頁填入後端位址後，課程目錄與課程大綱改由後端提供；後端回傳的 JSON 與靜態檔同形，所以 App 的其他部分不需要知道資料從哪裡來。

## 啟動後端

需要 Docker（Compose 2.20 以上）。

```bash
docker compose up --build
```

第一次啟動後資料庫是空的，觸發一次同步（一兩分鐘，會向 NCU 抓取全部課程）：

```bash
curl -X POST http://localhost:8000/api/v1/scraper/trigger
```

確認後端已有資料：

```bash
curl http://localhost:8000/api/v1/catalog
```

之後 Celery beat 會每 6 小時自動同步。API 文件在 <http://localhost:8000/docs>。要調整資料庫密碼等設定，把 `backend/.env.example` 複製成 `backend/.env` 再修改。

## 讓 App 連到後端

1. 依 [`app/README.zh-TW.md`](app/README.zh-TW.md) 建置並執行 App（debug 版）。
2. 開啟 App 的「設定」，在最下方的「課程資料來源」填入後端位址，按「連線並同步」。

| 執行環境 | 後端位址 |
|---|---|
| Android 模擬器 | `http://10.0.2.2:8000` |
| iOS 模擬器 | `http://localhost:8000` |
| 實體手機（與電腦同一個 Wi-Fi） | `http://<電腦的區域網路 IP>:8000` |

同步成功後，位址會被記住；同步失敗則維持原本的資料來源。按「改用靜態資料」可以切回。已經開啟的選課畫面要重新啟動 App 才會顯示新來源的資料。

明文 HTTP 只在 Android 的 debug 版與 iOS 的區域網路位址開放；正式環境請用 HTTPS。

## 開發

後端（在 `backend/` 下，需要 [uv](https://docs.astral.sh/uv/)）：

```bash
uv sync
```

```bash
uv run pytest
```

```bash
uv run ruff check . && uv run ruff format --check .
```

App（在 `app/` 下，需要 JDK 21 與 Android SDK）：

```bash
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

Git hook（detekt）改放在根目錄，啟用方式：

```bash
git config core.hooksPath .githooks
```

CI 在 `.github/workflows/`：`app-*.yml` 在 `app/` 下執行，`backend-*.yml` 在 `backend/` 下執行且只在後端檔案變動時觸發。

## 新增一個功能時要動哪裡

以「新增一種後端提供的資料」為例，資料由下往上流過這幾層：

| 層 | 後端 `backend/app/` | App `app/shared/src/commonMain/kotlin/org/mpc/` |
|---|---|---|
| 抓取 | `tasks/`（Celery 排程）或 `services/`（即時抓取） | |
| 儲存 | `models/`（SQLModel 資料表，記得在 `models/__init__.py` 與 `core/db.py` 匯出） | `data/local/`（Room） |
| API | `api/routes/`，並在 `api/main.py` 註冊 | `data/dto/`、`data/dataSource/` |
| 領域 | | `domain/model/`、`domain/repository/`、`data/repository/` |
| 畫面 | | `presentation/viewModel/`、`presentation/` |

每新增一個端點，請在 `docs/api-contract.md` 記下合約，並在兩邊各加一個釘住 JSON 形狀的測試。
