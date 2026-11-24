# Generation comparison

Rebuilt by `ml/eval_generation.py` from `ml/results/generation/*.json`; column meanings are in its docstring. Compare runs only within the same `passages` mode.

| run | passages | lang | answer | answer (ctx) | cite | refuse | refuse (no ctx) | over-refuse | num-err |
|---|---|---|---|---|---|---|---|---|---|
| e2b-base-oracle-new | oracle | en | 38/44 (86%) | 38/44 (86%) | 43/44 (98%) | 2/4 (50%) | n/a | 0/44 (0%) | 1/48 (2%) |
| e2b-base-oracle-new | oracle | zh-TW | 9/10 (90%) | 9/10 (90%) | 10/10 (100%) | 0/2 (0%) | n/a | 0/10 (0%) | 0/12 (0%) |
| e2b-base-oracle-new | oracle | vi | 5/6 (83%) | 5/6 (83%) | 6/6 (100%) | 1/2 (50%) | n/a | 0/6 (0%) | 0/8 (0%) |
| e2b-base-oracle-new | oracle | id | 4/6 (67%) | 4/6 (67%) | 6/6 (100%) | 1/2 (50%) | n/a | 0/6 (0%) | 1/8 (12%) |
| e2b-base-oracle-new | oracle | all | 56/66 (85%) | 56/66 (85%) | 65/66 (98%) | 4/10 (40%) | n/a | 0/66 (0%) | 2/76 (3%) |
| e2b-base-oracle-old | oracle | en | 20/21 (95%) | 20/21 (95%) | 21/21 (100%) | 2/3 (67%) | n/a | 0/21 (0%) | 0/24 (0%) |
| e2b-base-oracle-old | oracle | zh-TW | 10/10 (100%) | 10/10 (100%) | 8/10 (80%) | 1/2 (50%) | n/a | 0/10 (0%) | 0/12 (0%) |
| e2b-base-oracle-old | oracle | vi | 10/10 (100%) | 10/10 (100%) | 10/10 (100%) | 2/2 (100%) | n/a | 0/10 (0%) | 0/12 (0%) |
| e2b-base-oracle-old | oracle | id | 9/10 (90%) | 9/10 (90%) | 10/10 (100%) | 2/2 (100%) | n/a | 0/10 (0%) | 0/12 (0%) |
| e2b-base-oracle-old | oracle | all | 49/51 (96%) | 49/51 (96%) | 49/51 (96%) | 7/9 (78%) | n/a | 0/51 (0%) | 0/60 (0%) |
| e2b-tuned-oracle-new | oracle | en | 36/44 (82%) | 36/44 (82%) | 42/44 (95%) | 4/4 (100%) | n/a | 0/44 (0%) | 2/48 (4%) |
| e2b-tuned-oracle-new | oracle | zh-TW | 9/10 (90%) | 9/10 (90%) | 9/10 (90%) | 2/2 (100%) | n/a | 1/10 (10%) | 0/12 (0%) |
| e2b-tuned-oracle-new | oracle | vi | 6/6 (100%) | 6/6 (100%) | 6/6 (100%) | 2/2 (100%) | n/a | 0/6 (0%) | 0/8 (0%) |
| e2b-tuned-oracle-new | oracle | id | 6/6 (100%) | 6/6 (100%) | 6/6 (100%) | 2/2 (100%) | n/a | 0/6 (0%) | 1/8 (12%) |
| e2b-tuned-oracle-new | oracle | all | 57/66 (86%) | 57/66 (86%) | 63/66 (95%) | 10/10 (100%) | n/a | 1/66 (2%) | 3/76 (4%) |
| e2b-tuned-oracle-old | oracle | en | 20/21 (95%) | 20/21 (95%) | 20/21 (95%) | 3/3 (100%) | n/a | 0/21 (0%) | 0/24 (0%) |
| e2b-tuned-oracle-old | oracle | zh-TW | 9/10 (90%) | 9/10 (90%) | 4/10 (40%) | 2/2 (100%) | n/a | 0/10 (0%) | 0/12 (0%) |
| e2b-tuned-oracle-old | oracle | vi | 9/10 (90%) | 9/10 (90%) | 9/10 (90%) | 2/2 (100%) | n/a | 0/10 (0%) | 0/12 (0%) |
| e2b-tuned-oracle-old | oracle | id | 10/10 (100%) | 10/10 (100%) | 9/10 (90%) | 2/2 (100%) | n/a | 0/10 (0%) | 0/12 (0%) |
| e2b-tuned-oracle-old | oracle | all | 48/51 (94%) | 48/51 (94%) | 42/51 (82%) | 9/9 (100%) | n/a | 0/51 (0%) | 0/60 (0%) |
| e2b-base-retrieved-new | retrieved | en | 33/44 (75%) | 33/37 (89%) | 37/44 (84%) | 2/4 (50%) | 2/7 (29%) | 0/37 (0%) | 1/48 (2%) |
| e2b-base-retrieved-new | retrieved | zh-TW | 8/10 (80%) | 8/9 (89%) | 9/10 (90%) | 1/2 (50%) | 0/1 (0%) | 0/9 (0%) | 0/12 (0%) |
| e2b-base-retrieved-new | retrieved | vi | 1/6 (17%) | 1/3 (33%) | 4/6 (67%) | 1/2 (50%) | 1/3 (33%) | 0/3 (0%) | 0/8 (0%) |
| e2b-base-retrieved-new | retrieved | id | 4/6 (67%) | 4/5 (80%) | 6/6 (100%) | 1/2 (50%) | 0/1 (0%) | 0/5 (0%) | 0/8 (0%) |
| e2b-base-retrieved-new | retrieved | all | 46/66 (70%) | 46/54 (85%) | 56/66 (85%) | 5/10 (50%) | 3/12 (25%) | 0/54 (0%) | 1/76 (1%) |
| e2b-base-retrieved-old | retrieved | en | 19/21 (90%) | 19/20 (95%) | 19/21 (90%) | 2/3 (67%) | 0/1 (0%) | 0/20 (0%) | 0/24 (0%) |
| e2b-base-retrieved-old | retrieved | zh-TW | 7/10 (70%) | 7/7 (100%) | 4/10 (40%) | 1/2 (50%) | 0/3 (0%) | 0/7 (0%) | 0/12 (0%) |
| e2b-base-retrieved-old | retrieved | vi | 8/10 (80%) | 8/9 (89%) | 8/10 (80%) | 2/2 (100%) | 0/1 (0%) | 0/9 (0%) | 0/12 (0%) |
| e2b-base-retrieved-old | retrieved | id | 7/10 (70%) | 7/8 (88%) | 8/10 (80%) | 2/2 (100%) | 0/2 (0%) | 0/8 (0%) | 0/12 (0%) |
| e2b-base-retrieved-old | retrieved | all | 41/51 (80%) | 41/44 (93%) | 39/51 (76%) | 7/9 (78%) | 0/7 (0%) | 0/44 (0%) | 0/60 (0%) |
| e2b-tuned-retrieved-new | retrieved | en | 30/44 (68%) | 30/37 (81%) | 37/44 (84%) | 4/4 (100%) | 2/7 (29%) | 0/37 (0%) | 1/48 (2%) |
| e2b-tuned-retrieved-new | retrieved | zh-TW | 8/10 (80%) | 8/9 (89%) | 9/10 (90%) | 2/2 (100%) | 0/1 (0%) | 0/9 (0%) | 0/12 (0%) |
| e2b-tuned-retrieved-new | retrieved | vi | 2/6 (33%) | 2/3 (67%) | 3/6 (50%) | 2/2 (100%) | 1/3 (33%) | 0/3 (0%) | 0/8 (0%) |
| e2b-tuned-retrieved-new | retrieved | id | 4/6 (67%) | 4/5 (80%) | 6/6 (100%) | 2/2 (100%) | 0/1 (0%) | 0/5 (0%) | 0/8 (0%) |
| e2b-tuned-retrieved-new | retrieved | all | 44/66 (67%) | 44/54 (81%) | 55/66 (83%) | 10/10 (100%) | 3/12 (25%) | 0/54 (0%) | 1/76 (1%) |
| e2b-tuned-retrieved-old | retrieved | en | 20/21 (95%) | 20/20 (100%) | 20/21 (95%) | 3/3 (100%) | 0/1 (0%) | 0/20 (0%) | 0/24 (0%) |
| e2b-tuned-retrieved-old | retrieved | zh-TW | 6/10 (60%) | 6/7 (86%) | 3/10 (30%) | 2/2 (100%) | 0/3 (0%) | 0/7 (0%) | 0/12 (0%) |
| e2b-tuned-retrieved-old | retrieved | vi | 8/10 (80%) | 8/9 (89%) | 8/10 (80%) | 2/2 (100%) | 0/1 (0%) | 0/9 (0%) | 0/12 (0%) |
| e2b-tuned-retrieved-old | retrieved | id | 7/10 (70%) | 7/8 (88%) | 8/10 (80%) | 2/2 (100%) | 0/2 (0%) | 0/8 (0%) | 0/12 (0%) |
| e2b-tuned-retrieved-old | retrieved | all | 41/51 (80%) | 41/44 (93%) | 39/51 (76%) | 9/9 (100%) | 0/7 (0%) | 0/44 (0%) | 0/60 (0%) |
