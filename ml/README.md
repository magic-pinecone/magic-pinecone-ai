# ml/ — evaluation and fine-tuning kit for the on-device student

Goal: a small on-device model that turns "question + retrieved OIA passages"
into a short bulleted answer with source numbers, or refuses and points to the
OIA when the passages do not hold the answer. A large open-weight teacher
(gpt-oss behind an OpenAI-compatible endpoint) writes the training data;
students are LoRA fine-tuned on it. Languages: English, Traditional Chinese
(zh-TW), Vietnamese (vi), Indonesian (id).

The scripts import the backend's knowledge index
(`backend/app/services/knowledge/`) and therefore run with the backend's
environment. The index covers the crawled OIA site, government guides, credits,
service learning, graduation language, international programs, contacts,
places and destinations (courses and clubs only with the production database).
No model of PRC origin is used anywhere in this kit (teacher, students,
embeddings: EmbeddingGemma); keep it that way.

## Files

| File | What it does |
|---|---|
| `prompt_format.py` | The one definition of the prompt: system prompt, passage numbering, refusal sentence per language, citation and refusal detection. Used by data generation, training data and evaluation. The text lives in `backend/app/services/answer_text.py`, shared with the assistant endpoint, and is loaded from there by file path (no backend dependencies needed). |
| `checks.py` | Pure text checks: strip a reasoning channel, numbers not supported by the passages (both from `answer_text.py` too), `must_contain` matching, similarity to golden questions. |
| `demo/` | A one-page demo of the backend's assistant chain; see "Demo page" below. |
| `common.py` | Paths, import of the backend service, JSONL helpers, the OpenAI-compatible chat client (`httpx`). |
| `golden/questions.jsonl` | 136 test questions: the first 60 hand-written against 8 documents (24 en, 12 each zh-TW / vi / id; 9 unanswerable), then 76 against the larger corpus (48 en, 12 zh-TW, 8 vi, 8 id; 10 unanswerable). |
| `eval_retrieval.py` | hit@1 / hit@3 / ans@3 of the knowledge search on the golden set, `--search keyword\|hybrid`, and a sweep of `KNOWLEDGE_MIN_SIMILARITY`. |
| `make_teacher_data.py` | Asks the teacher(s) for training examples and cleans them. |
| `train_lora.py` | LoRA fine-tuning of a student (Unsloth, or PEFT + TRL). |
| `eval_generation.py` | Runs the golden set through any OpenAI-compatible endpoint and scores it; keeps a comparison table across runs. |
| `requirements-train.txt` | Heavy dependencies of `train_lora.py` only. |
| `results/` | Committed result files. `retrieval_baseline.*` is real. |
| `tests/` | Unit tests, no network. |

`ml/data/` (teacher data) and `ml/runs/` (adapters, merged models) are
git-ignored.

## Golden set

Fields: `id`, `lang`, `question`, `answerable`, `source_id` (a document id of
`knowledge.index.load_documents()`, `null` when unanswerable), `must_contain`
(strings copied verbatim from that document that a correct answer must
include), `needs_native_review`, and optionally:

- `source_type` — only on the 76 newer questions (the first 60 lack it, which is
  how the scripts tell "old" from "new"): the source type of the document, or
  `none` when unanswerable. 44 of the 66 answerable newer questions are in
  English, the rest are mostly translations of those (`parallel_of`).
- `parallel_of` — id of the English question this one translates. 33 of the 36
  non-English questions are translations, so languages can be compared on the
  same facts. Three zh-TW questions are written directly against the Chinese
  scholarship guidelines.
- `alt_source_ids` — other documents that state the same fact (for example the
  Cathay limits are on the insurance page and in the handbook). They count as
  hits and as correct citations. This field is an addition to the brief.

- `must_contain_any` — spellings of one fact, of which an answer needs only
  one; it is asked for on top of `must_contain`, and one of the spellings is
  the document's own. Two items have it, and **both were changed after reading
  model output, so they are no longer blind**: `zh-003` (`30` or `三十`; an
  answer of 「三十天」 had been marked wrong) and `en-005` (`excluded`,
  `not covered`, `does not cover`, `only hospitalization`; the earlier
  `hospitalization` failed "outpatient visits are completely excluded" and
  passed an answer that said outpatient visits are covered). `en-005` is a
  yes/no question and these strings are still only a proxy: a wrong "yes"
  that happens to use one of them passes. Read its answers by hand.

`must_contain` is mostly numbers and URLs so that it also works for answers in
other languages. A bare number must appear as a whole number (`20` is not found
in `2026`; `1,000`, `1000` and `1.000` are the same); other strings are matched
ignoring case, spaces and dash style. English-only questions also use English
phrases.

**The vi and id questions, and the vi and id refusal sentences in
`prompt_format.py`, are machine-drafted. A native speaker must review them
before any number computed on them is quoted.** They are marked
`needs_native_review: true`. The zh-TW questions were also written by the
model and deserve a read by a native speaker, although they are not flagged.

## Order of steps

All commands that touch the backend service are run from `backend/`
(`uv sync --frozen` once, if the environment does not exist yet).

### 1. Tests (laptop, no network)

```bash
cd backend && uv run --frozen pytest ../ml/tests -q
```

### 2. Retrieval on the golden set (keyword: laptop, no network)

```bash
cd backend && uv run --frozen python ../ml/eval_retrieval.py
```

Writes `ml/results/retrieval_baseline.md` and `.json`. Current result:

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 21 | 17/21 (81%) | 21/21 (100%) | 19/21 (90%) | 0/21 (0%) | 2/3 (67%) |
| zh-TW | 10 | 9/10 (90%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| vi | 10 | 8/10 (80%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| id | 10 | 8/10 (80%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| all | 51 | 42/51 (82%) | 51/51 (100%) | 49/51 (96%) | 0/51 (0%) | 8/9 (89%) |

`ans@3` (the three returned passages hold the answer, by the same test as for
a model's answer: all `must_contain` strings and one of `must_contain_any`) is the
number that matters for the generator. hit@k is about documents, and there are
only 8, so it says little.

How the search got here, `ans@3` after each change (each row includes the rows
above it):

| search | en | zh-TW | vi | id | all | answerable, no result | unanswerable with passages |
|---|---|---|---|---|---|---|---|
| first version: substring match, one passage per document | 15/21 | 2/10 | 4/10 | 3/10 | 24/51 | 7/51 | 6/9 |
| several passages per document | 15/21 | 1/10 | 2/10 | 4/10 | 22/51 | 7/51 | 6/9 |
| Latin-script words matched whole | 16/21 | 2/10 | 2/10 | 4/10 | 24/51 | 11/51 | 4/9 |
| title matched with each passage, rare words weigh more, plural "s" ignored | 19/21 | 2/10 | 2/10 | 5/10 | 28/51 | 11/51 | 4/9 |
| relevance threshold | 19/21 | 0/10 | 0/10 | 1/10 | 20/51 | 28/51 | 2/9 |
| Chinese questions cut into bigrams | 19/21 | 4/10 | 0/10 | 1/10 | 24/51 | 20/51 | 3/9 |
| glossary of zh-TW / vi / id terms | 19/21 | 10/10 | 10/10 | 10/10 | 49/51 | 0/51 | 8/9 |

How to read it:

- **The golden set is the only test set, and the search was built while
  looking at it.** The scoring changes (third and fourth row) were chosen
  because they fixed English questions of this set. The threshold was fixed by
  a rule (below) but checked on the same 60 questions. So these are not
  held-out numbers; expect less on new questions.
- **The 30/30 for zh-TW, vi and id is optimistic.** The glossary was written
  from the topics of the 8 documents, not from the questions, but by the same
  kind of language model that drafted the vi and id questions (and 33 of the 36
  non-English questions translate English ones about the documents' main
  facts). A student who words a question differently from the glossary gets
  nothing. The vi and id glossary entries have not been reviewed by a native
  speaker.
- Whole-word matching and the threshold take away the vi and id results of the
  first version (4/10 and 3/10 down to 0/10 and 1/10 before the glossary).
  Those were matches on shared names and numbers and on substrings ("nhi" in
  "nhiêu" matching NHI); without the glossary the search now returns nothing
  for such a question instead.
- Threshold: a passage is returned when it has every word of the query, or
  when the weights of the words it has add up to the weight of a word found in
  a single passage (ln(1 + number of passages), 5.25 for the 190 passages
  now). On this set that drops the passages of 1 of the 9 unanswerable
  questions and of no answerable one; the lowest answerable question scores
  5.82, so the margin is thin, and a cut-off of 6 would already lose 4
  answerable questions. The unanswerable questions here are about neighbouring
  subjects (dependent visa, income tax) and score like answerable ones: the
  search still gives no usable "not covered" signal, and refusing remains the
  generator's job.
- With 51 answerable questions, differences of one or two questions per
  language mean nothing.
- Two English questions still miss: the list of documents they ask for is in a
  passage that overlaps a better-scoring one, and overlapping passages are not
  returned together.

#### Keyword and hybrid search on the larger corpus

```bash
cd backend
uv run --frozen python ../ml/eval_retrieval.py --search keyword
EMBEDDING_BASE_URL=http://127.0.0.1:8002/v1 EMBEDDING_MODEL=embeddinggemma \
    uv run --frozen python ../ml/eval_retrieval.py --search hybrid
uv run --frozen python ../ml/eval_retrieval.py --search hybrid --sweep 0.3,0.4,0.5
```

`hybrid` embeds all passages through an OpenAI-compatible `/v1/embeddings`
endpoint (run here: `ggml-org/embeddinggemma-300M-GGUF`, Q8_0, in
llama-server `--embeddings`), keeps the vectors in a SQLite file and fuses the
keyword and vector rankings. Top 3 and top 5, no source-type filter,
`KNOWLEDGE_MIN_SIMILARITY` 0.40 (the default):

| questions | answerable | keyword ans@3 | hybrid ans@3 | keyword ans@5 | hybrid ans@5 |
|---|---|---|---|---|---|
| old (first 60) | 51 | 44/51 (86%) | 46/51 (90%) | 47/51 (92%) | 47/51 (92%) |
| new (76) | 66 | 45/66 (68%) | 54/66 (82%) | 48/66 (73%) | 56/66 (85%) |
| all | 117 | 89/117 (76%) | 100/117 (85%) | 95/117 (81%) | 103/117 (88%) |

Per language and per source type: `results/retrieval_keyword.md` and
`results/retrieval_hybrid.md`. Hybrid helps most the questions in English about
the Chinese government guides (`gov_guide` ans@3 5/16 keyword, 10/16 hybrid) and
the vi and id questions (ans@3 20/32 keyword, 25/32 hybrid, all questions).
The assistant passes five passages to the model (`TOP_K` 5), so ans@5 is the
cap on `answer` for retrieved prompts.

Sweep of `KNOWLEDGE_MIN_SIMILARITY` (`results/retrieval_hybrid_sweep.md`):
ans@3 on all questions is 101/117 at 0.30 and 0.35, 100/117 at 0.40, 99/117 at
0.45 and 98/117 at 0.50; ans@5 is 104, 104, 103, 102 and 99. The unanswerable
questions that still get passages fall from 19 of 19 (0.30, 0.35) to 18 (0.40)
and 17 (0.45, 0.50), and the number of unanswerable questions with a vector hit
from 18 (0.30) to 12 (0.40) and 8 (0.50). They always keep their keyword
passages, so the threshold does not make the search say "not found". 0.40 is
the highest value that keeps ans@3 within one question of the best; the choice
rests on 117 questions and one embedding model.

### 3. Teacher data (teacher on Colab, script on the laptop)

Serve gpt-oss on Colab behind an OpenAI-compatible endpoint, then on the
laptop:

```bash
cd backend
uv run --frozen python ../ml/make_teacher_data.py --dry-run -n 40   # plumbing check, fake teacher

export TEACHER_BASE_URL=...   # ends in /v1
export TEACHER_API_KEY=...    # only if the endpoint needs one
export TEACHER_MODEL=...      # model name the endpoint expects
uv run --frozen python ../ml/make_teacher_data.py -n 2000 --out ../ml/data/train.jsonl
```

The scripts read the endpoint only from these environment variables and never
print the URL or the key.

- Passages come from `knowledge.index` and are cut with `PASSAGE_LINES` /
  `PASSAGE_STEP`. The answering passage is drawn by source type
  (`--type-mix`, default OIA pages 40% and government guides 14%, so at least
  half; the rest over the other types; a type absent from the corpus is
  dropped and the weights renormalised).
- About 70% of the answer examples (`--hard-rate`) have their distractors
  replaced, after the question exists, by the top results of the search for
  that question (`--search hybrid`, the default, or `keyword`), never a
  passage sharing lines with the answering one; the rest keep random
  distractors. Rows record `distractors: retrieved|random`.
- About 10% (`--multi-rate`) are `multi` examples: two answering passages
  (the nearest non-overlapping passage by search, else another window of the
  page) and a question that needs both; both must be cited.
- `--workers N` asks the teacher with N parallel requests.
- An answer example shows the passage with the answer plus 1-2 distractor
  passages from other documents, in random order. (The brief says one
  distractor; a second is added at random because evaluation prompts hold up to
  three passages. `--max-distractors 1` restores exactly one.)
- About 18% are refusal examples (`--refusal-rate`): the teacher sees a hidden
  passage and writes a question it answers, the student only gets passages of
  other documents, and the target is the fixed refusal sentence of
  `prompt_format.py`, not teacher text.
- Language mix: `--lang-mix en=0.55,zh-TW=0.15,vi=0.15,id=0.15` (default).
- Cleaning (`clean_example`): reasoning channel removed (`<think>`, Gemma
  thought channel, gpt-oss harmony `analysis` channel; the API's separate
  reasoning field is never read); reply must be JSON; every bullet must cite;
  every `[n]` must be a shown passage and the answering passage must be cited;
  every number and English month name in the answer must occur in the shown
  passages; questions equal or similar (ratio >= 0.85 after normalization) to a
  golden question are dropped. Rejections go to `<out>.rejects.jsonl` with the
  reason.
- Resume: rerun the same command. Tasks already written or rejected are
  skipped; a larger `-n` continues the same plan. A request that fails is not
  recorded, so it is retried on the next run.
- More teachers: `--teachers TEACHER,TEACHER2` with `TEACHER2_BASE_URL`,
  `TEACHER2_API_KEY`, `TEACHER2_MODEL`. Tasks alternate between teachers and
  each row records `teacher`. To add a teacher to an existing file later, run
  it alone with a different `--seed` and the same `--out`.

Run for the tuned student below (gpt-oss-20b behind vLLM, `-n 4200 --seed 5
--refusal-rate 0.22 --workers 16 --max-tokens 3072`; five-passage prompts, 2 to
4 distractors, `--hard-refusal-rate` 0.6): 3596 kept, 604 rejected (232
`visible_passages_answer`, 210 `answer_not_in_language`, 77
`number_not_in_passages`, 39 `not_json`, 22 `bullet_without_citation`, 5
`matches_golden_question`, 5 `teacher_skipped`, 4
`number_not_in_passages_after_swap`, 3 `wrong_language`, 2 `answer_is_refusal`,
2 `question_too_long`, 2 `not_bulleted`, 1 `second_passage_not_cited`). By kind:
2553 answer, 358 multi, 685 refusal (298 of them with passages retrieved for the
question minus the answering document, 387 with random passages); by language:
2014 en, 541 id, 527 zh-TW, 514 vi; by source type: 1537 oia_page, 530
gov_guide, 327 credits, 275 intl_program, 239 graduation_language, 237 place,
193 service_learning, 153 destination, 105 contact; 2102 rows with retrieved and
1494 with random distractors. With the default of 1024 reply tokens a test of 40
tasks lost 8 to empty or cut-off JSON (the model's reasoning used the budget);
3072 brought `not_json` to 39 of 4200. Some vi and id answers carry English
fragments copied from the passages.

Read a sample of the output before training. The cleaning catches invented
numbers and broken citations; it cannot tell whether an answer is otherwise
faithful, whether a "refusal" question is really unanswerable from the shown
passages, or whether a vi/id question is natural.

### 4. Training (laptop with RTX 4070 8 GB, or Colab)

```bash
python -m venv .venv-train && . .venv-train/bin/activate   # not the backend environment
pip install -r ml/requirements-train.txt
python ml/train_lora.py --check-data --data ml/data/train.jsonl
python ml/train_lora.py --data ml/data/train.jsonl \
    --model aisingapore/Gemma-SEA-LION-v4.5-E2B-IT --output ml/runs/sealion-e2b
python ml/train_lora.py --data ml/data/train.jsonl \
    --model google/gemma-4-E2B-it --output ml/runs/gemma4-e2b
python ml/train_lora.py --data ml/data/train.jsonl \
    --model google/gemma-3-1b-it --output ml/runs/gemma3-1b    # gated: set HF_TOKEN
```

Defaults: 4-bit loading, batch size 1, gradient accumulation 8, sequence length
2048, LoRA rank 16, 3 epochs, loss on the assistant turn only, vision layers
not trained. `--backend peft` uses transformers + PEFT + TRL instead of Unsloth.
`--merge` also writes merged 16-bit weights; `--gguf q4_k_m` exports a GGUF
(Unsloth backend).

`--model` also takes Unsloth's mirrors `unsloth/gemma-4-E2B-it`,
`unsloth/gemma-3-1b-it` and `unsloth/gemma-3-270m-it`, which are not gated.

`--full-finetune` (Unsloth backend) trains all weights instead of a LoRA
adapter, loads the model in 16 bit, and saves it to `<output>/model`; the
learning rate default drops from 2e-4 to 5e-5. It is meant for the 270m
student, which is small enough to train in full.

Notes from the runs on Colab. They were reported by the session that ran
them and have not been repeated on another machine:

- **Batch size.** The defaults (batch 1, accumulation 8) are for 8 GB of GPU
  memory. On Colab use `--batch-size 8 --grad-accum 1`: the same effective
  batch, about 3.9 s per step with the defaults and about 6.6 times faster
  this way.
- **Gemma 3 through Unsloth for inference.** Load it with
  `attn_implementation="eager"`. Nothing in this repository loads a model for
  inference (`eval_generation.py` talks to a server), so this concerns the
  notebook that scores a model directly with transformers.
- **Repeatable scores.** For the same reason the settings are not in a script
  here: when a model is scored directly on a GPU, set
  `torch.use_deterministic_algorithms(True, warn_only=True)` and the
  environment variable `CUBLAS_WORKSPACE_CONFIG=:4096:8` before the first
  CUDA call, so that two runs of the same model give the same answers.
- **270m and 1B were scored in bf16**, not from a quantised GGUF, so their
  numbers are not the numbers of the file that would be served. Numbers from
  different GPU types are not comparable either: compare runs made on the same
  machine with the same settings.

Manual GGUF export from `<output>/merged` with llama.cpp:

```bash
git clone https://github.com/ggml-org/llama.cpp
python llama.cpp/convert_hf_to_gguf.py ml/runs/sealion-e2b/merged \
    --outfile sealion-e2b-tuned-f16.gguf --outtype f16
# then quantize with llama.cpp's llama-quantize, e.g. to Q4_K_M
```

`train_lora.py` was written on a machine without a GPU, where only
`--check-data` and `--help` were executed. It was then run on Colab on
2026-11-12 (G4 and A100) for Gemma 3 270m and 1B and for Gemma 4 E2B,
according to the session that did that work; this has not been repeated on
another machine. See "Not tested" below.

### 5. Serve a student

Any OpenAI-compatible server works, for example llama.cpp:

```bash
llama-server -m sealion-e2b-tuned-q4_k_m.gguf --port 8080 --jinja
```

Serve the base model the same way to get the "before" numbers. The server must
apply the model's own chat template; a different template than in training
makes a tuned model look worse than it is.

#### Behind the backend's `POST /api/v1/assistant/ask`

The backend does not load a model. It calls an OpenAI-compatible server over
HTTP (`backend/app/services/assistant.py`, `ChatModel`), with the prompt of
`prompt_format.py` and temperature 0, so the tuned student is put behind the
endpoint by starting a model server next to the backend and naming it in the
backend's environment.

1. Start llama.cpp's server with the tuned GGUF. The file is kept in a
   private Hugging Face repository whose name is not written here: put it in
   place of `HF_USER/HF_REPO` below. Because it is private, llama-server
   needs a token to download from it: `HF_TOKEN` in its environment (never in
   a file of this repository). Whether it also picks up a login made with
   `hf auth login` has not been tried.

   ```bash
   llama-server -hf HF_USER/HF_REPO:Q4_K_M \
       --alias ncu-e2b-tuned \
       --host 127.0.0.1 --port 8080 --jinja --reasoning off -c 4096
   # or, with the file already on disk:  -m e2b-v3-Q4_K_M.gguf
   ```

   **`--reasoning off` is required.** For a Gemma 4, llama-server switches
   thinking on by default; the chat template then adds a thinking marker to
   the prompt, which is not the prompt the student was trained on. A first
   round of evaluation was thrown away for this reason.

2. Point the backend at it (`backend/.env`, see `backend/.env.example`):

   ```bash
   ASSISTANT_BASE_URL=http://127.0.0.1:8080/v1
   ASSISTANT_MODEL=ncu-e2b-tuned
   ASSISTANT_DISABLE_THINKING=true
   ASSISTANT_TIMEOUT_SECONDS=60
   ```

   `ASSISTANT_DISABLE_THINKING=true` sends
   `chat_template_kwargs: {"enable_thinking": false}` with every request, so
   thinking stays off even if the server was started without the flag. For
   Ollama the switch is `ASSISTANT_REASONING_EFFORT=none` instead.

3. Check it:

   ```bash
   curl -s http://127.0.0.1:8000/api/v1/assistant/ask \
       -H 'Content-Type: application/json' \
       -d '{"question": "With a work permit, how many hours per week can I work?"}'
   ```

   `model` is the name from step 2 and `answer` is the model's text. When the
   two variables are empty, or the server is down, slow or returns an error,
   the endpoint still answers 200 with what it looked up (`passages`,
   `places`, `bus`), `answer` is null, and `error` says what happened to the
   model. A question takes one request to the model, or two when it is not in
   English (it is first translated for the search).

What is and is not known about this path:

- `backend/tests/test_assistant.py` tests it with a stand-in for the model
  server: the request the backend sends, and what it returns when the server
  fails.
- It was run once with the real model, on 2026-11-13: the 60 golden questions
  sent one after another to the endpoint, with `e2b-v3-Q4_K_M.gguf` served by
  llama.cpp b12340 (prebuilt CPU build; b12342, used for the conversion, has
  no published build) started as in step 1 but with `-m`, on a 36-thread CPU
  without a GPU. Only the assistant router was started, since the whole
  backend needs a database.

  | | en | zh-TW | vi | id | all |
  |---|---|---|---|---|---|
  | answer correct | 19/21 | 10/10 | 9/10 | 10/10 | 48/51 |
  | answer in the passages | 19/21 | 10/10 | 9/10 | 10/10 | 48/51 |
  | refused, unanswerable | 3/3 | 2/2 | 2/2 | 2/2 | 9/9 |
  | seconds, median | 4.7 | 8.3 | 7.1 | 7.0 | 6.9 |

  No request failed or timed out; the slowest took 17.4 s. All 51 answers
  cite a passage that was given, none refuses an answerable question, and
  none has a number that is not in the passages. The three misses (en-004,
  en-008, vi-006) are questions whose passages lack the full answer.
  Generation ran at 10 to 12 tokens per second.
- Translation, which the student was not tuned for: all 36 non-English
  questions came back as one English question. Some names are wrong (國泰 as
  "National Life Insurance", 中央大學 as "Central Normal University", 舊生 as
  "new students", làm thêm as "overtime"). The search uses the question and
  the translation together, and the right document was still found for each
  of these. Nothing was changed because of it.
- Several questions at once: b12340 starts with 4 slots by default. 4
  questions sent together took 13 to 17 s each, 8 together up to 23.5 s, all
  answered. The endpoint has no rate limit and no queue of its own.
- Not tried: a GPU, the Q8_0 file, Ollama, downloading with `-hf` (the file
  was on disk), more than 8 questions at once, and questions outside the
  golden set. The timeout was never reached; if it is, the endpoint returns
  the passages with `error: "model endpoint: ReadTimeout"`, and
  `ASSISTANT_TIMEOUT_SECONDS` is the setting to raise, not `max_tokens`.

### 6. Generation evaluation (laptop)

```bash
cd backend
uv run --frozen python ../ml/eval_generation.py --dry-run          # plumbing check, fake model

export EVAL_BASE_URL=http://127.0.0.1:8080/v1
export EVAL_MODEL=sealion-e2b-tuned
uv run --frozen python ../ml/eval_generation.py --run-name sealion-e2b-tuned
uv run --frozen python ../ml/eval_generation.py --run-name sealion-e2b-tuned-oracle --passages oracle
```

Run it once per model (teacher, each base student, each tuned student); use
`--endpoint TEACHER` to evaluate the teacher with the `TEACHER_*` variables.
Every run is saved in `ml/results/generation/<run-name>.json` and
`ml/results/generation_comparison.md` is rebuilt from all of them.

Columns, per language:

| Column | Meaning |
|---|---|
| answer | answerable questions answered with every `must_contain` string and, where listed, one of `must_contain_any` |
| answer (ctx) | the same, only over questions whose prompt passages hold the answer by that same test (a number has to be a whole number there too: `20` is not in `std20230311`) |
| cite | answerable questions answered with valid `[n]`, at least one pointing to a passage of the expected document |
| refuse | unanswerable questions that were refused |
| refuse (no ctx) | answerable questions whose passages lack the answer and that were refused (correct behaviour) |
| over-refuse | passages hold the answer but the model refused |
| num-err | replies containing a number that is neither in the passages nor in the question |

A refusal is a reply that contains the OIA e-mail address and no `[n]`.
With `--passages retrieved`, `answer` is capped by retrieval (`ans@3` above);
`answer (ctx)` and the oracle run show what the model itself can do.

#### Run on the larger corpus (Gemma 4 E2B, Colab A100)

`unsloth/gemma-4-E2B-it`, the 3596 rows above (3417 train, 179 held out), 3
epochs, `--batch-size 8 --grad-accum 1 --max-seq-length 3072`, rank 16,
`--merge --gguf q4_k_m`, 1284 steps in 21 minutes. Before the run
`train_lora.py` decodes the label sequence of the first example (`--labels-only`):
176 of 1116 tokens carry loss and they decode to the answer plus the
end-of-turn token, no system, passage or question text. Mean training loss
0.294 (0.16 to 0.23 in the last epoch); held-out loss 0.395 after the last
epoch.

Generation scores (Q4_K_M GGUF, llama-server `--jinja --reasoning off -c 4096`,
temperature 0, hybrid search and five passages for the retrieved prompts; base
is `unsloth/gemma-4-E2B-it-GGUF` Q4_K_M, v4 is the tuned model of the previous
data round, v5 the model trained on the data above). Columns as in section 6,
all languages; `refuse` is over the unanswerable questions, `num-err` over all
replies:

| questions | passages | model | answer | answer (ctx) | cite | refuse | refuse (no ctx) | over-refuse | num-err |
|---|---|---|---|---|---|---|---|---|---|
| old | retrieved | base | 42/51 | 42/47 | 43/51 | 6/9 | 0/4 | 0/47 | 0/60 |
| old | retrieved | v4 | 43/51 | 43/47 | 43/51 | 9/9 | 0/4 | 0/47 | 0/60 |
| old | retrieved | v5 | 35/51 | 35/47 | 33/51 | 9/9 | 1/4 | 6/47 | 0/60 |
| old | oracle | base | 48/51 | 48/51 | 47/51 | 6/9 | n/a | 0/51 | 0/60 |
| old | oracle | v4 | 44/51 | 44/51 | 44/51 | 9/9 | n/a | 0/51 | 1/60 |
| old | oracle | v5 | 41/51 | 41/51 | 41/51 | 9/9 | n/a | 4/51 | 0/60 |
| new | retrieved | base | 51/66 | 51/58 | 59/66 | 2/10 | 1/8 | 0/58 | 2/76 |
| new | retrieved | v4 | 52/66 | 52/58 | 59/66 | 8/10 | 2/8 | 0/58 | 1/76 |
| new | retrieved | v5 | 53/66 | 53/58 | 56/66 | 10/10 | 6/8 | 1/58 | 1/76 |
| new | oracle | base | 61/66 | 61/66 | 66/66 | 2/10 | n/a | 0/66 | 2/76 |
| new | oracle | v4 | 63/66 | 63/66 | 64/66 | 8/10 | n/a | 0/66 | 2/76 |
| new | oracle | v5 | 58/66 | 58/66 | 63/66 | 10/10 | n/a | 2/66 | 3/76 |

Split of the 117 answerable questions, retrieved prompts (old and new together):

| outcome | base | v4 | v5 |
|---|---|---|---|
| correct | 93 | 95 | 88 |
| passages lack the answer, model refused | 1 | 2 | 7 |
| passages lack the answer, model answered | 11 | 10 | 5 |
| passages have the answer, model refused | 0 | 0 | 7 |
| passages have the answer, model answered wrongly | 12 | 10 | 10 |
| of the last two rows, correct with oracle passages | 9 | 5 | 9 of 17 |

Reading: v5 refuses every unanswerable question (19/19 against 17/19 for v4 and
8/19 for the base) and refuses more often when the passages lack the answer (7
of 12, against 2 and 1), which is what the new hard-refusal data was built
for. It pays for it with over-refusal: 7 questions whose passages hold the
answer were refused (v4 and the base: 0), and its `answer` on the old
questions fell from 43/51 to 35/51 (retrieved) and from 44/51 to 41/51
(oracle). On the new questions retrieved `answer` is 53/66 against 52/66 for
v4, within noise. Of the 17 questions where v5 had the answer in its passages
and failed, 2 are scoring artifacts (a paraphrase of "ARC" and "Certificate of
Enrollment", and "128" written as "一百二十八"), 1 is borderline (the
website is named, its URL is not given) and 14 are errors: 7 refusals, a wrong
figure (15 days for 30), the wrong semester row, an off-topic answer, and
three answers that point to the Immigration Bureau site instead of giving the
OIA download link. This reading is of the replies against `must_contain`, not
of the passages. Per-language rows: `results/generation_comparison.md`. One run per model, 51 and 66 answerable
questions: differences of one to three questions mean nothing; the drop on the
old set (8 questions) is larger than that.

#### Mixed data run (v6)

`mix_data.py` merges teacher-data files into one training file:

```bash
cd backend
uv run --frozen python ../ml/mix_data.py --data /content/data/train.jsonl \
    /content/data/train_v5.jsonl --out /content/data/train_v6.jsonl \
    --recheck-votes 2 --workers 16
```

It puts the current system prompt on every row, drops duplicate ids and
questions that look like a golden question, and asks the teacher again
(`--recheck-votes`, with the stricter verify prompt) about every hard refusal,
a refusal row whose distractors were retrieved: it stays only if every vote
says the passages do not answer the question. Rejects, with the reason, go to
`<out>.rejects.jsonl`; `--dry-run` uses the fake teacher.

The input was the v4 data (3479 rows) and the v5 data (3596 rows). 7028 rows
were kept and 47 rejected, all of them v5 hard refusals for which a vote said
the passages do answer (298 hard refusals went in, 251 stayed: 3.6% of the
rows; none of the v4 rows is a hard refusal). No duplicate id and no golden
look-alike was found. Kept by kind: 5092 answer, 675 multi, 1261 refusal; by
language: 3923 en, 1054 id, 1024 vi, 1027 zh-TW; by distractors: 3200 random,
3828 retrieved.

`unsloth/gemma-4-E2B-it` on these rows (6677 train, 351 held out), 2 epochs,
`--batch-size 8 --grad-accum 1 --max-seq-length 3072`, rank 16, `--merge --gguf
q4_k_m`, 1670 steps in 21 minutes. `--labels-only` on the first example: 127
of 625 tokens carry loss and they decode to the answer plus the end-of-turn
token. Mean training loss 0.298, held-out loss 0.308.

Generation scores as in the table above, with v6 added (same server flags, one
run per cell):

| questions | passages | model | answer | answer (ctx) | cite | refuse | refuse (no ctx) | over-refuse | num-err |
|---|---|---|---|---|---|---|---|---|---|
| old | retrieved | base | 42/51 | 42/47 | 43/51 | 6/9 | 0/4 | 0/47 | 0/60 |
| old | retrieved | v4 | 43/51 | 43/47 | 43/51 | 9/9 | 0/4 | 0/47 | 0/60 |
| old | retrieved | v5 | 35/51 | 35/47 | 33/51 | 9/9 | 1/4 | 6/47 | 0/60 |
| old | retrieved | v6 | 41/51 | 41/47 | 41/51 | 9/9 | 0/4 | 0/47 | 0/60 |
| old | oracle | base | 48/51 | 48/51 | 47/51 | 6/9 | n/a | 0/51 | 0/60 |
| old | oracle | v4 | 44/51 | 44/51 | 44/51 | 9/9 | n/a | 0/51 | 1/60 |
| old | oracle | v5 | 41/51 | 41/51 | 41/51 | 9/9 | n/a | 4/51 | 0/60 |
| old | oracle | v6 | 44/51 | 44/51 | 45/51 | 9/9 | n/a | 0/51 | 0/60 |
| new | retrieved | base | 51/66 | 51/58 | 59/66 | 2/10 | 1/8 | 0/58 | 2/76 |
| new | retrieved | v4 | 52/66 | 52/58 | 59/66 | 8/10 | 2/8 | 0/58 | 1/76 |
| new | retrieved | v5 | 53/66 | 53/58 | 56/66 | 10/10 | 6/8 | 1/58 | 1/76 |
| new | retrieved | v6 | 53/66 | 53/58 | 57/66 | 9/10 | 3/8 | 1/58 | 1/76 |
| new | oracle | base | 61/66 | 61/66 | 66/66 | 2/10 | n/a | 0/66 | 2/76 |
| new | oracle | v4 | 63/66 | 63/66 | 64/66 | 8/10 | n/a | 0/66 | 2/76 |
| new | oracle | v5 | 58/66 | 58/66 | 63/66 | 10/10 | n/a | 2/66 | 3/76 |
| new | oracle | v6 | 64/66 | 64/66 | 64/66 | 9/10 | n/a | 0/66 | 2/76 |

Split of the 117 answerable questions, retrieved prompts:

| outcome | base | v4 | v5 | v6 |
|---|---|---|---|---|
| correct | 93 | 95 | 88 | 94 |
| passages lack the answer, model refused | 1 | 2 | 7 | 3 |
| passages lack the answer, model answered | 11 | 10 | 5 | 9 |
| passages have the answer, model refused | 0 | 0 | 7 | 1 |
| passages have the answer, model answered wrongly | 12 | 10 | 10 | 10 |
| of the last two rows, correct with oracle passages | 9 | 5 | 9 of 17 | 6 of 11 |

Reading: v6 gets back most of the answer accuracy (old retrieved 41/51 against
43/51 for v4 and 35/51 for v5; oracle 44/51 and 64/66, the same as or above
v4) and over-refuses once (en-141, the Cathay Life insurance price, refused
with the answer in the passages) against 7 for v5. It keeps part of the
refusal gain: 18/19 unanswerable questions refused (v5 19/19, v4 17/19; the
miss is a Vietnamese question in the new set, in both passage modes), and
when the passages lack the answer it refuses 3 of 12 times (v5 7, v4 2).
Against v4 the differences on answerable questions are one or two questions
(94 against 95 correct), which one run per model cannot separate.

## Gemma 3 270m student

A second, smaller student for the offline mode: `unsloth/gemma-3-270m-it`
against the budget of 300 MB for the model file and phones with 4 GB of RAM.
The comparison asked is full fine-tune against LoRA at this size.

Where this was run, so the numbers are read correctly:

- Base, v4, v5 and v6 of Gemma 4 E2B (tables above) ran on an A100. The 270m
  runs below ran on a Colab T4 (15 GB).
- All 270m numbers use the knowledge index at commit `bc3304d`, before the
  `food_place` source, the academic calendar and the course translations were
  added. They are comparable with each other, and with the E2B rows only for
  the same 117 answerable and 19 unanswerable golden questions.
- Each cell is one run at temperature 0.

### Setup

- Data: `train_v6.jsonl`, the 7,028 rows of the mixed data run (6,677 train,
  351 held out).
- Full fine-tune: `--full-finetune`, `--batch-size 8 --grad-accum 1
  --epochs 2`, 1670 steps in 9672 s. Mean training loss 0.568, held-out loss
  0.547. Exported with `--gguf q4_k_m`; a Q8_0 file of the same model
  also exists.
- float32: the T4 has no bf16, and Unsloth refuses float16 for Gemma 3 (its
  log says so and switches to float32), so the whole fine-tune ran in float32.
- LoRA: not run. The Colab runtime was replaced at step 525/1670, and no
  checkpoint had been saved. The run (rank 16, `--no-load-in-4bit --merge
  --gguf q4_k_m`) needed `UNSLOTH_DEFAULT_PACKING=0`, because Unsloth otherwise
  packs rows and the label check fails; `train_lora.py` now defaults that
  variable to 0.
- Serving for the evals: `llama-server --jinja --reasoning off -c 4096` on the
  GPU, the Q4_K_M file, hybrid search with EmbeddingGemma 300M.

### Size against the budget

| file | bytes | MB (10^6) |
|---|---|---|
| full fine-tune, Q4_K_M | 253,114,752 | 253.1 |
| full fine-tune, Q8_0 | 291,545,312 | 291.5 |
| LoRA, Q4_K_M | not run | not run |

Both files are under 300 MB; Q8_0 only just. Speed on a phone, or on a CPU:
not measured.

### Results

Counts over the 117 answerable and 19 unanswerable questions (old and new
sets together), from `error_split.txt` and `ml/results/generation/`; the
per-language rows are in `generation_comparison.md`.

| model | passages | correct /117 | has the answer, refused | refused, passages lack it | unanswerable refused /19 | num-err /136 | language drift |
|---|---|---|---|---|---|---|---|
| 270m base | hybrid top 5 | 35 | 0 | 0/12 | 1 | 4 | 34 |
| 270m base | oracle | 44 | 1 | n/a | 0 | 7 | 32 |
| 270m full | hybrid top 5 | 74 (old 30/51, new 44/66) | 2 | 3/12 | 9 | 1 | 4 |
| 270m full | hybrid top 3 | 80 | 2 | 4/15 | 11 | 3 | 5 |
| 270m full | keyword top 3 | 68 | 1 | 10/27 | 14 | 0 | 2 |
| 270m full | oracle | 84 | 2 | n/a | 9 | 2 | 6 |
| 270m LoRA | all | not run | not run | not run | not run | not run | not run |
| E2B v6 (A100) | hybrid top 5 | 94 | 1 | 3/12 | 18 | 1 | n/a |

"Refused, passages lack it" counts only the answerable questions whose
retrieved passages do not hold the answer; the denominator changes with the
search because top 3 and keyword search retrieve different passages. The
language drift column counts non-refusals written in another language than
the question; it was not computed for the E2B runs. The E2B v6 row is copied
from the v6 section above.

### Reading

- Fine-tuning does most of the work at this size: 74 correct against 35 for
  the untuned model, and the base model answers in the wrong language in 34
  responses against 4.
- Full fine-tune against LoRA: not answered here, LoRA was not run.
- Top 3 beats top 5 for this model: 80 against 74 correct, and 11 against 9
  unanswerable questions refused. Retrieval puts the answer in the top 3 for
  100 of 117 questions and in the top 5 for 103, so the three extra passages
  cost little recall and appear to give a 270m model more to get lost in. It
  is one run per cell and a difference of 6 questions, so treat it as a
  direction, not a result.
- Keyword-only search with top 3 loses 12 correct answers against hybrid top
  3 (68 against 80), but refuses more: 14/19 unanswerable and 10 refusals
  where the passages lack the answer. The model refuses when the passages
  look unrelated, which is what weaker retrieval gives it.
- The main weakness against E2B v6 is refusing unanswerable questions: 9/19
  at top 5 (11/19 at top 3) against 18/19. Even with the right passages
  (oracle) it gets 84/117 correct against 108/117 for E2B v6, and only 9/19
  unanswerable refused.
- Against the 300 MB budget the model fits (253 MB at Q4_K_M), but at about 63%
  correct with retrieved passages it is a fallback for offline use, not a
  replacement for E2B v6.

## What has been tested

Run on the development machine (no GPU, no network access to a model):

- `pytest ml/tests`: 397 passed.
- `eval_retrieval.py`: real run, numbers above.
- `make_teacher_data.py --dry-run`, including resume and a larger `-n`.
- `eval_generation.py --dry-run`, both `--passages` modes.
- `train_lora.py --check-data` and `--help`.

## Not tested

- **The teacher run** (section 3) used gpt-oss-20b through vLLM with
  `--max-tokens 3000`; the reasoning is returned in a separate field, which
  `ChatClient` does not read. Whether a cut-off or differently served model
  needs a different `--max-tokens` is untested.
- **`train_lora.py` was not executed where it was written**, in either
  backend; the runs on Colab (see "Training") came later and are known here
  only from that session's report. Points that were uncertain when it was
  written:
  - whether Unsloth's `FastModel` loads `aisingapore/Gemma-SEA-LION-v4.5-E2B-IT`
    (Unsloth documents its own `unsloth/gemma-4-E2B-it` mirror) and whether
    replacing its chat template with Unsloth's `gemma-4` template is harmless;
  - whether `train_on_responses_only` with `<|turn>user\n` / `<|turn>model\n`
    masks correctly when the conversation starts with a system turn (check that
    a decoded label sequence contains only the answer before a long run);
  - Gemma 3's template is believed to fold the system prompt into the first
    user turn; not checked here;
  - in the PEFT backend: the `dtype=` argument of `from_pretrained`, PEFT's
    default target modules for Gemma 4, `optim="adamw_torch_fused"` with 4-bit
    weights, and whether an 8 GB card fits it at all. Google's guide also saves
    `lm_head` and `embed_tokens` (`modules_to_save`); this script does not,
    because no tokens are added and it would not fit in 8 GB;
  - `requirements-train.txt` was never installed; Unsloth may conflict with the
    minimum versions listed there;
  - memory: Unsloth states 8-10 GB for E2B LoRA, so 8 GB is at the edge even in
    4 bit. If it runs out, lower `--max-seq-length` first, or train on Colab;
  - merging and GGUF export, including whether llama.cpp's converter handles a
    merged Gemma 4 E2B checkpoint that still contains the vision and audio
    towers.
- The golden set has not been reviewed by anyone but its author; vi and id
  need a native speaker.
- The dry-run numbers of both scripts come from fake models and mean nothing.

## Documentation used for `train_lora.py` (read 2026-11-10)

- Unsloth, Gemma 4 fine-tuning: <https://unsloth.ai/docs/models/gemma-4/train>
  — `FastModel.from_pretrained` / `get_peft_model` arguments
  (`finetune_vision_layers=False` for text only), chat template `gemma-4`
  (non-thinking, recommended for E2B/E4B), `train_on_responses_only` with
  `<|turn>user\n` and `<|turn>model\n`, removing the leading `<bos>` (wrong for
  Gemma 4 in practice: its processor does not add `<bos>` again, so the script
  keeps it; see `strip_bos` in `train_lora.py`),
  batch size 1 + gradient accumulation, `adamw_8bit`, E2B LoRA at 8-10 GB,
  a loss of 13-15 being normal for the multimodal E2B/E4B.
- Unsloth notebooks (Gemma 4 E2B/E4B text, Gemma 3) via Context7
  `/unslothai/notebooks`: <https://github.com/unslothai/notebooks> — the same
  calls, and the `gemma-3` template name.
- Unsloth, saving to GGUF:
  <https://unsloth.ai/docs/basics/inference-and-deployment/saving-to-gguf> —
  `save_pretrained_merged(..., save_method="merged_16bit")`,
  `save_pretrained_gguf(..., quantization_method="q4_k_m")`,
  `convert_hf_to_gguf.py`.
- Google, Gemma QLoRA with Hugging Face (now targets Gemma 4):
  <https://ai.google.dev/gemma/docs/core/huggingface_text_finetune_qlora> —
  `transformers>=5.10.1`, `peft>=0.19.0`, `AutoModelForMultimodalLM`,
  `BitsAndBytesConfig` nf4 + double quantization, prompt/completion dataset
  with loss on the completion, `merge_and_unload`, end-of-turn token `<turn|>`.
- TRL `SFTTrainer` docs via Context7 `/huggingface/trl`:
  <https://github.com/huggingface/trl/blob/main/docs/source/sft_trainer.md> —
  conversational prompt/completion format, `completion_only_loss` (default
  true), `max_length`, `peft_config`, `processing_class`.
- Model cards: <https://huggingface.co/google/gemma-4-E2B-it> (system role is
  native, thinking is off unless enabled, `AutoModelForMultimodalLM`),
  <https://huggingface.co/aisingapore/Gemma-SEA-LION-v4.5-E2B-IT> (MIT,
  fine-tune of `google/gemma-4-E2B-it`), `google/gemma-3-1b-it` (gated,
  text-only `AutoModelForCausalLM`).

## Demo page

`demo/app.py` serves one page that shows the whole chain for a question typed
in any of the four languages. The chain itself is the backend's
(`backend/app/services/assistant.py`, also served as `POST /api/v1/assistant/ask`);
the page only shows each step: where the question was sent (OIA documents, a
campus building, or live bus arrivals), the passages the model was given, the
model's answer, and two automatic checks on it (the cited passage numbers
exist; every number in the answer appears in the passages).

```bash
cd backend
# look-up only
uv run --frozen python ../ml/demo/app.py --port 8765
# with a model behind an OpenAI-compatible endpoint, e.g. Ollama
DEMO_BASE_URL=http://localhost:11434/v1 DEMO_MODEL=<model name> \
  DEMO_REASONING_EFFORT=none \
  uv run --frozen python ../ml/demo/app.py --port 8765
```

With a model, a question that is not English is first translated by the same
model, because the pages are in English; the documents are then searched for
the words of the translation and of the question. Without a model the page
shows what was looked up only, and a question in another language finds the
English pages through the search's glossary alone. Bus
questions need the backend's `TDX_CLIENT_ID` and `TDX_CLIENT_SECRET`. Live
arrivals are shown as data and never go through the model: tried on 2026-11-11,
the untuned model gave the time the data was read as the bus time and answered
Vietnamese and Indonesian bus questions in Thai script. It listens on 127.0.0.1; it
has no login and is not meant to be exposed.

Run on 2026-11-11 with an untuned `unsloth/gemma-4-E2B-it-GGUF` (Q4_K_M) behind
Ollama on a CPU-only server, all 60 golden questions through the page's API,
look-up and answer together (one run, temperature 0):

| lang | right document in top 3 | answer passage in top 3 | answer correct | refused when unanswerable |
|---|---|---|---|---|
| en | 21/21 | 15/21 | 14/21 | 2/3 |
| zh-TW | 7/10 | 5/10 | 4/10 | 2/2 |
| vi | 10/10 | 8/10 | 7/10 | 2/2 |
| id | 10/10 | 8/10 | 7/10 | 2/2 |

No answer contained a number missing from its passages or cited a passage that
was not there. Read these with care: "answer correct" only checks that the
expected figures appear; the translation prompt was chosen after three tries
on these same 60 questions, so the non-English rows are optimistic; and there
are ten questions per non-English language. All 60 are document questions and
none of them was sent to the building or bus look-up. Building and bus
questions have no golden set yet; they were only tried by hand.
