# 畢業語言門檻資料

中央大學與「畢業前要達到的語言條件」有關的官方資料，供外籍生服務使用。

| 檔案 | 內容 | 怎麼產生 |
|---|---|---|
| `sources.json` | 官方來源清單（網址、單位、學制、生效日） | 人工維護 |
| `corpus/<id>.md` | 每個來源的全文，開頭有來源網址、抓取日與原檔雜湊 | `scripts/build_graduation_language_corpus.py` |
| `requirements.json` | 從語料人工整理的結構化門檻，每條規則列出來源 id | 人工維護 |
| `programs.json` | 招收外國學位生的系所、授課語言、畢業學分、修業規定連結 | `scripts/build_intl_programs.py` |

## 當作檢索語料使用

`corpus/` 的每個檔案是一份官方文件。檔頭的欄位可直接當 metadata：

- `url`：回答時附給使用者的來源連結。
- `org`、`degree_levels`、`lang`：檢索時的篩選條件。
- `effective`：這份文件適用的學年度或修正日期；同一規定有新舊版時要看這欄。
- `fetched_on`、`sha256`：抓取日與原檔雜湊，用來判斷官方內容是否變動。
- `extract`：`html`、`pdf`、`office` 是程式轉出的文字；`manual` 是人工轉錄（原件是圖片）。

注意：

- PDF 表格轉成文字後欄位可能錯位，數字請以 `requirements.json` 或原檔為準。
- 語料只收中央大學官方文件。其他學校的規定不要混進來。
- 英譯本可能比中文版舊，檔頭的 `effective` 有標示。

## 更新流程

1. 在 `backend/` 下執行 `uv run python scripts/build_graduation_language_corpus.py --check`，看哪些來源變了。
2. 不加 `--check` 重跑，更新語料。
3. 對照有變動的語料修改 `requirements.json`，並更新 `verified_on`。
4. 圖片來源（`extract: manual`）要重新人工轉錄，再把檔頭的 `sha256` 改成新的值。
5. 換招生梯次時用 `scripts/build_intl_programs.py --intake <梯次>` 重建 `programs.json`。
