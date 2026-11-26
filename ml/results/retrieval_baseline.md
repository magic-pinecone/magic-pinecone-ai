# Retrieval on the golden set

Backend keyword search (`student_services.search`, top 3, no topic filter) on `ml/golden/questions.jsonl`. Produced by `ml/eval_retrieval.py`; column meanings are in its docstring.

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 21 | 17/21 (81%) | 21/21 (100%) | 19/21 (90%) | 0/21 (0%) | 2/3 (67%) |
| zh-TW | 10 | 9/10 (90%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| vi | 10 | 8/10 (80%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| id | 10 | 8/10 (80%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| all | 51 | 42/51 (82%) | 51/51 (100%) | 49/51 (96%) | 0/51 (0%) | 8/9 (89%) |
