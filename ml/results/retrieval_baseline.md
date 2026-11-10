# Retrieval baseline

Backend keyword search (`student_services.search`, top 3, no topic filter) on `ml/golden/questions.jsonl`. Produced by `ml/eval_retrieval.py`; column meanings are in its docstring.

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 21 | 19/21 (90%) | 21/21 (100%) | 15/21 (71%) | 0/21 (0%) | 3/3 (100%) |
| zh-TW | 10 | 2/10 (20%) | 3/10 (30%) | 2/10 (20%) | 7/10 (70%) | 0/2 (0%) |
| vi | 10 | 5/10 (50%) | 9/10 (90%) | 4/10 (40%) | 0/10 (0%) | 2/2 (100%) |
| id | 10 | 5/10 (50%) | 9/10 (90%) | 3/10 (30%) | 0/10 (0%) | 1/2 (50%) |
| all | 51 | 31/51 (61%) | 42/51 (82%) | 24/51 (47%) | 7/51 (14%) | 6/9 (67%) |
