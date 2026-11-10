# 學分規定

`requirements.json` 是 `GET /api/v1/intl/credits` 的資料，人工整理，2026-11-11 對照原文。內容是全校層級的學分架構：最低畢業學分、共同必修（國文、外文、體育、服務學習、通識）、外籍生以華語抵國文的規定，以及研究生最低學分。各系的畢業學分在 `GET /intl/programs`，這裡不重複。

## 收錄原則

- 規則只收完整讀過的官方文件，每條附網址與原文引述。
- 只在搜尋摘要看到、或網頁打不開的內容，不寫成規則，放進 `open_questions`。
- `title_en`、`summary_en`、`summary_zh` 是我們自己的整理，原文以 `quote` 為準。
- `credits` 是該條規則講的學分數；規則沒有單一數字時為 `null`。
- 引述來自 `graduation_language/corpus/` 收錄的文件時，`tests/test_credits.py` 會比對原文（忽略空白，並做 NFKC 正規化，因為 PDF 轉出的文字含 CJK 相容字元）。新增來源時，優先先把文件加進該語料。

## 來源

| 文件 | 用途 |
|---|---|
| [學則](https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/418/439020843.pdf)（115 學年度教務章則彙編）與[英文版](https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/329/1NationalCentralUniversityStudyRegulations.pdf)（備查至 2024-01-05） | 第 11 條每學期最低學分、第 13 條 128 學分與體育、第 48 條同等學力加修 12 學分、第 58 條研究生最低學分 |
| [共同必修科目選修辦法](https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/418/364607259.pdf)（111-01-12）與[英文版](https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/329/11GuidelinesfortheSelectionofCommonRequiredCoursesatNationalCentralUniversity.pdf) | 共同必修 25 學分、國文、外文、體育、服務學習、通識與免修 |
| [大一國文修課實施辦法](https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/418/521084208.pdf)（107-03-31） | 外籍生以華語 6 學分抵國文、中文系外籍生分級 |
| [大一外文修課及教學實施細則](https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/418/786143607.pdf)（113-06-19） | 英語系國家外籍生免修大一英文 |
| [各學士班應修科目表使用注意事項](https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/418/459504876.pdf)（115 學年度） | 依入學年度的應修科目表修課 |
| [松濤全人學院：課程資訊](https://tc.ncu.edu.tw/?page_id=814) | 107 學年度起核心通識改為四選一 |

六份 PDF 於 2026-11-11 重新下載，SHA-256 與語料檔頭相同。

## 還沒讀到的

- 國文課程抵修的申請表、期限與承辦單位。
- 111 學年度以前入學者的共同必修規定。
- 通識選課說明（`pdc.adm.ncu.edu.tw/Course/course/COUR_09.pdf`）：404。
- 英文授課的通識課程清單。

補上這些文件後，把 `open_questions` 裡對應的項目改寫成規則。
