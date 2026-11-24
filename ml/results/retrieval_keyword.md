# Retrieval on the golden set

Keyword search (`knowledge.index.search`, top 3, no source-type filter) on `ml/golden/questions.jsonl`. Produced by `ml/eval_retrieval.py --search keyword`; column meanings are in its docstring.

### old questions

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 21 | 15/21 (71%) | 20/21 (95%) | 18/21 (86%) | 0/21 (0%) | 3/3 (100%) |
| zh-TW | 10 | 7/10 (70%) | 8/10 (80%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| vi | 10 | 7/10 (70%) | 7/10 (70%) | 8/10 (80%) | 0/10 (0%) | 2/2 (100%) |
| id | 10 | 6/10 (60%) | 8/10 (80%) | 9/10 (90%) | 1/10 (10%) | 2/2 (100%) |
| all | 51 | 35/51 (69%) | 43/51 (84%) | 45/51 (88%) | 1/51 (2%) | 9/9 (100%) |

### new questions

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 44 | 32/44 (73%) | 34/44 (77%) | 33/44 (75%) | 0/44 (0%) | 4/4 (100%) |
| zh-TW | 10 | 9/10 (90%) | 10/10 (100%) | 7/10 (70%) | 0/10 (0%) | 2/2 (100%) |
| vi | 6 | 2/6 (33%) | 2/6 (33%) | 1/6 (17%) | 3/6 (50%) | 0/2 (0%) |
| id | 6 | 2/6 (33%) | 3/6 (50%) | 2/6 (33%) | 1/6 (17%) | 1/2 (50%) |
| all | 66 | 45/66 (68%) | 49/66 (74%) | 43/66 (65%) | 4/66 (6%) | 7/10 (70%) |

### all questions

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 65 | 47/65 (72%) | 54/65 (83%) | 51/65 (78%) | 0/65 (0%) | 7/7 (100%) |
| zh-TW | 20 | 16/20 (80%) | 18/20 (90%) | 17/20 (85%) | 0/20 (0%) | 4/4 (100%) |
| vi | 16 | 9/16 (56%) | 9/16 (56%) | 9/16 (56%) | 3/16 (19%) | 2/4 (50%) |
| id | 16 | 8/16 (50%) | 11/16 (69%) | 11/16 (69%) | 2/16 (12%) | 3/4 (75%) |
| all | 117 | 80/117 (68%) | 92/117 (79%) | 88/117 (75%) | 5/117 (4%) | 16/19 (84%) |

### new questions by source type

| source type | answerable | ans@3 |
|---|---|---|
| contact | 4 | 4/4 (100%) |
| credits | 12 | 10/12 (83%) |
| destination | 5 | 4/5 (80%) |
| gov_guide | 16 | 3/16 (19%) |
| graduation_language | 3 | 2/3 (67%) |
| intl_program | 2 | 2/2 (100%) |
| oia_page | 17 | 11/17 (65%) |
| place | 1 | 1/1 (100%) |
| service_learning | 6 | 6/6 (100%) |
