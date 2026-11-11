# 國際生行政事項資料

國際事務處（OIA）英文頁面的語料與聯絡窗口，給 `app/services/student_services.py`、`/intl` 端點與 MCP 工具使用。

| 檔案 | 內容 | 怎麼產生 |
|---|---|---|
| `sources.json` | 要抓的 OIA 頁面，每筆有 `topic`（`insurance`、`immigration`、`work_permit`、`exchange`） | 手動維護 |
| `corpus/<id>.md` | 每個頁面的全文，開頭有來源網址、抓取日與原檔雜湊；`"links": true` 的頁面會保留表單連結 | `scripts/build_graduation_language_corpus.py --dataset student_services` |
| `glossary.json` | 學生可能用繁體中文、越南文、印尼文說的詞，對應到頁面上的英文用詞（居留證 → ARC、học bổng → scholarship）。檢索時把 `en` 的字加進查詢 | 手動維護；依語料涵蓋的主題編寫，**越南文與印尼文詞條由語言模型草擬，尚未經母語者審閱** |
| `contacts.json` | 依主題分的承辦窗口：電話、分機、地點、辦公時間 | 手動從 OIA 頁尾與 Staff 頁抄錄 |

## 原則

- 只收中央大學官方頁面。其他學校的保險、工作證規定不一樣，不能混進來。
- `contacts.json` 只放單位層級的電話與分機，不放承辦人姓名與個人信箱；人員異動時以 `staff_page` 為準。
- 健保加保日的計算規則寫在 `student_services.nhi_enrollment`，依據是 `corpus/oia-insurance.md` 的規則與三個範例。頁面改了規則就要一起改程式與測試。

- `glossary.json` 的 `en` 只能用語料裡真的出現的字（測試會檢查）；一個概念只寫頁面最常用的那個說法，寫太多同義字會讓該概念的權重被重複計算。不要為了讓 `ml/golden/` 的題目過關而照抄題目用字。

## 更新

在 `backend/` 下執行：

```bash
uv run python scripts/build_graduation_language_corpus.py --dataset student_services --check   # 看頁面有沒有變
uv run python scripts/build_graduation_language_corpus.py --dataset student_services           # 重建語料
```

頁面有變動時，對照新的語料檢查 `contacts.json` 與健保計算規則，並更新 `contacts.json` 的 `verified_on`。
