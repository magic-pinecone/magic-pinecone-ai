# KNOWLEDGE_MIN_SIMILARITY sweep

Hybrid search, top 3 and 5, `ml/golden/questions.jsonl`. Produced by `ml/eval_retrieval.py --sweep`.

| KNOWLEDGE_MIN_SIMILARITY | ans@3 old | ans@3 new | ans@3 all | ans@5 all | unanswerable with passages | unanswerable with a vector hit |
|---|---|---|---|---|---|---|
| 0.3 | 46/51 (90%) | 55/66 (83%) | 101/117 (86%) | 104/117 (89%) | 19/19 (100%) | 18 |
| 0.35 | 46/51 (90%) | 55/66 (83%) | 101/117 (86%) | 104/117 (89%) | 19/19 (100%) | 16 |
| 0.4 | 46/51 (90%) | 54/66 (82%) | 100/117 (85%) | 103/117 (88%) | 18/19 (95%) | 12 |
| 0.45 | 45/51 (88%) | 54/66 (82%) | 99/117 (85%) | 102/117 (87%) | 17/19 (89%) | 10 |
| 0.5 | 45/51 (88%) | 53/66 (80%) | 98/117 (84%) | 99/117 (85%) | 17/19 (89%) | 8 |
