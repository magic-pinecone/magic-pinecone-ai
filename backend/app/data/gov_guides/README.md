# 政府機關外籍生資訊

中華民國中央政府機關（勞動部、移民署、健保署、教育部、僑委會）針對僑外生在臺就學期間工作、保險、居留的官方說明，由 `app/services/gov_guides.py` 透過 `GET /api/v1/intl/guides` 提供給 App 的「政府資訊」頁。

| 檔案 | 內容 | 怎麼產生 |
|---|---|---|
| `sources.json` | 來源清單，每筆有 `topic`（`work`、`insurance`、`residence`、`help`） | 手動維護 |
| `corpus/<id>.md` | 每個頁面的全文，開頭有來源網址、抓取日與原檔雜湊 | `scripts/build_graduation_language_corpus.py --dataset gov_guides` |

## 原則

- 以政府機關官方頁面為主。新聞報導（如 Taipei Times）只收對官方新制的報導，`org` 寫報社名稱，讓使用者看得出不是官方文件。
- `"extract": "link"` 是線上申辦系統（工作許可申辦網、移民署學生線上申辦），只列連結、不抓內容。
- HTML 來源都設 `"blocks": true`，只在區塊元素換行，讓句子不會被連結或粗體切開，App 直接顯示這份文字。
- 這裡是全國通用的規定；中央大學自己的作法在 `../student_services/`。

## 更新

在 `backend/` 下執行：

```bash
uv run python scripts/build_graduation_language_corpus.py --dataset gov_guides --check   # 看頁面有沒有變
uv run python scripts/build_graduation_language_corpus.py --dataset gov_guides           # 重建語料
```

PDF 來源需要 poppler 的 `pdftotext`。部分頁面含點閱次數，每次抓取雜湊都會不同，`--check` 會一直回報 CHANGED，要看內文差異再判斷。
