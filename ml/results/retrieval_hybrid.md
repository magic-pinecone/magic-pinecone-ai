# Retrieval on the golden set

Hybrid search (keyword and embeddings, fused) (`knowledge.index.search`, top 3, no source-type filter) on `ml/golden/questions.jsonl`. Produced by `ml/eval_retrieval.py --search hybrid`; column meanings are in its docstring.

### old questions

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 21 | 20/21 (95%) | 21/21 (100%) | 20/21 (95%) | 0/21 (0%) | 3/3 (100%) |
| zh-TW | 10 | 5/10 (50%) | 6/10 (60%) | 7/10 (70%) | 0/10 (0%) | 2/2 (100%) |
| vi | 10 | 8/10 (80%) | 9/10 (90%) | 9/10 (90%) | 0/10 (0%) | 2/2 (100%) |
| id | 10 | 7/10 (70%) | 9/10 (90%) | 8/10 (80%) | 0/10 (0%) | 2/2 (100%) |
| all | 51 | 40/51 (78%) | 45/51 (88%) | 44/51 (86%) | 0/51 (0%) | 9/9 (100%) |

### new questions

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 44 | 35/44 (80%) | 38/44 (86%) | 37/44 (84%) | 0/44 (0%) | 4/4 (100%) |
| zh-TW | 10 | 8/10 (80%) | 9/10 (90%) | 8/10 (80%) | 0/10 (0%) | 2/2 (100%) |
| vi | 6 | 4/6 (67%) | 4/6 (67%) | 2/6 (33%) | 1/6 (17%) | 1/2 (50%) |
| id | 6 | 5/6 (83%) | 6/6 (100%) | 4/6 (67%) | 0/6 (0%) | 1/2 (50%) |
| all | 66 | 52/66 (79%) | 57/66 (86%) | 51/66 (77%) | 1/66 (2%) | 8/10 (80%) |

### all questions

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 65 | 55/65 (85%) | 59/65 (91%) | 57/65 (88%) | 0/65 (0%) | 7/7 (100%) |
| zh-TW | 20 | 13/20 (65%) | 15/20 (75%) | 15/20 (75%) | 0/20 (0%) | 4/4 (100%) |
| vi | 16 | 12/16 (75%) | 13/16 (81%) | 11/16 (69%) | 1/16 (6%) | 3/4 (75%) |
| id | 16 | 12/16 (75%) | 15/16 (94%) | 12/16 (75%) | 0/16 (0%) | 3/4 (75%) |
| all | 117 | 92/117 (79%) | 102/117 (87%) | 95/117 (81%) | 1/117 (1%) | 17/19 (89%) |

### new questions by source type

| source type | answerable | ans@3 |
|---|---|---|
| contact | 4 | 4/4 (100%) |
| credits | 12 | 11/12 (92%) |
| destination | 5 | 4/5 (80%) |
| gov_guide | 16 | 8/16 (50%) |
| graduation_language | 3 | 3/3 (100%) |
| intl_program | 2 | 2/2 (100%) |
| oia_page | 17 | 12/17 (71%) |
| place | 1 | 1/1 (100%) |
| service_learning | 6 | 6/6 (100%) |

