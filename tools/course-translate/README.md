# course-translate

用 Apple 裝置端的 Translation 框架，把 [magic-pinecone-lite](https://github.com/magic-pinecone/magic-pinecone-lite) 的靜態課程資料翻成英文與越南文。只在 macOS 26.4 以上的 Mac 執行，不需要網路或 API 金鑰。

## 輸出

每個範圍、每個語言一個檔案，內容是「原文一行 → 譯文」的對照表：

| 檔案 | 來源欄位 |
|---|---|
| `out/catalog.<lang>.json` | 課名、學院、系所、分發條件 |
| `out/detail.<lang>.json` | 課程目標、授課內容、教科書、授課方式、評量配分 |

使用端把欄位以 `\n` 切行，逐行查表，查不到就保留原文。教師姓名不翻。

## 執行

先在「系統設定 › 一般 › 語言與地區 › 翻譯語言」下載中文（台灣）、英文、越南文，並開啟 Apple Intelligence（`highFidelity` 需要它）。

```bash
cd tools/course-translate
swift run -c release course-translate --lite ../../../magic-pinecone-lite
```

| 參數 | 預設 | 說明 |
|---|---|---|
| `--lite DIR` | `../../../magic-pinecone-lite` | lite repo 的位置 |
| `--out DIR` | `out` | 輸出目錄 |
| `--lang en,vi` | `en,vi` | 目標語言 |
| `--scope catalog,detail` | 兩者 | 要翻的範圍 |
| `--fast` | 關 | 改用傳統翻譯模型（`lowLatency`），快但品質差 |
| `--limit N` | 無上限 | 每個檔案最多翻幾行，試跑用 |

已翻過的行會跳過，每 200 行存檔一次，中斷後重跑會接著做。新學期只需要翻新出現的行。
