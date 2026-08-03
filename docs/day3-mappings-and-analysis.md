# Search quality improvements — mappings and text analysis

**Status:** delivered · **Scope:** how documents are indexed and matched ·
**Impact:** three search defects fixed, one class of silent data error eliminated

---

## Summary

Until this change, we let the search engine **guess** how each field should be treated.
It guessed badly, and the failures were silent — no errors, no warnings, just missing
results and filters that quietly matched nothing.

We now **declare** the treatment of every field explicitly, and we control how text is
broken into searchable words. Three defects that were live in the previous build are
fixed, all measured against the same data.

| # | Symptom | Before | After |
|---|---|---|---|
| 1 | Filtering by category failed if the case did not match exactly | **0 results** | **3 results** |
| 2 | Searching "search" did not find documents about "OpenSearch" | 2 documents | 3 documents |
| 3 | Singular/plural did not match ("aggregation" vs "Aggregations") | 0 results | 1 result |
| 4 | An unexpected field was accepted and silently given a type | accepted | **rejected with a clear error** |
| 5 | Wasted index storage on a field nobody could use | present | removed |

---

## What was wrong

A search engine does not store text the way a database does. It breaks text into
individual words, and it has to be told which fields are **prose to be searched** and
which are **exact labels to be filtered on**.

Previously nobody told it. It inferred a type from the first document it saw, and
applied one blanket rule to every text field. That produced three concrete problems.

### 1. Filters were case-sensitive, and failed silently

Asking for documents in the `operations` category returned **nothing** unless the case
matched exactly what was stored. Three documents were in that category the whole time.

This is the most expensive kind of bug in a search system, because **nothing reports an
error**. An empty result page looks identical to "there is no such data". On a
dashboard it shows up as a chart that reads zero.

### 2. Product names were unsearchable

The engine treated `OpenSearch` as a single indivisible word. Someone searching for
**search** would not find a document about **OpenSearch** — the two were as unrelated,
to the engine, as "search" and "banana".

### 3. Singular and plural did not match

Searching `aggregation` found nothing, because the document said `Aggregations`. Users
do not think about this; they simply conclude the content is not there.

---

## What we changed

### Every field now has a declared purpose

| Field | Treatment | Reason |
|---|---|---|
| Title | Searchable prose, plus an exact copy | Searched by users; the exact copy is used only for sorting alphabetically |
| Content | Searchable prose | The body text — searched, never filtered or sorted on |
| Author | Exact label, plus a searchable copy | Grouping and counting authors must be exact, but "documents by Nair" should still work |
| Category | Exact label, case-insensitive | Only ever used for filtering and counting |
| Tags | Exact label, case-insensitive | Same |
| Created / updated | Date | Enables date ranges and chronological sorting |

The distinction that matters: **prose fields** are broken into words so they can be
matched loosely; **label fields** are kept whole so they can be counted and filtered
exactly. The previous build applied the first treatment to everything, including fields
that only ever needed the second.

### Text is now processed deliberately

Text passes through a defined sequence before it is stored:

1. **Strip formatting** — HTML tags are removed rather than indexed as words
2. **Split into words**
3. **Split joined words** — `OpenSearch` also becomes `open` and `search`, while
   remaining searchable as `OpenSearch`
4. **Normalise punctuation** — `OpenSearch's` and `OpenSearch` are treated as the same
5. **Ignore case**
6. **Drop filler words** — "is", "a", "the" carry no search value
7. **Reduce words to their stem** — `engine`, `engines` and `engineering` collapse to a
   common root, so any of them finds the others

The measurable effect on one sentence:

```
Input:   "OpenSearch is a Distributed Search Engine"

Before:  opensearch · is · a · distributed · search · engine
After:   opensearch · open · search · distribut · engin
```

Filler words gone, words reduced to stems, and `OpenSearch` findable by either half of
its name.

### Unexpected data is now rejected instead of absorbed

Previously, sending a field nobody had planned for would be accepted and assigned a
type based on whatever value arrived first. If the second document disagreed, indexing
would fail later with an error pointing at the wrong place.

Now an unplanned field is refused immediately, with a message naming it. Adding a field
becomes a deliberate decision rather than an accident.

### Wasted storage removed

The engine had been keeping a second, exact copy of every document's full body text.
Nothing could use it — you cannot meaningfully filter or sort on a paragraph — and it
**silently discarded any value longer than 256 characters**, so it was not even
complete. Removed.

---

## Evidence

All figures below come from running the queries against the real index, before and
after, on identical data.

**Case-insensitive filtering**

```
Filter: category = "OPERATIONS"
Before: 0 documents
After:  3 documents — Choosing a shard count, Bulk indexing throughput,
                      Container memory limits and the JVM
```

**Product name searchability**

```
Search: "search engine"
Before: 2 documents
After:  3 documents
        New: "Relevance scoring with BM25" — a document that never uses the word
        "search" on its own, only "OpenSearch". Previously unfindable.
```

**Singular / plural**

```
Search: "aggregation"
Before: 0 documents
After:  1 document — "Aggregations for analytics"
```

**Unexpected field**

```
Before: accepted, type invented
After:  rejected — "mapping set to strict, dynamic introduction of
        [unexpectedField] is not allowed"
```

---

## New capability: inspecting how text is handled

Two endpoints were added so this behaviour can be examined rather than taken on trust.
They are read-only.

**See how a piece of text will be stored**

```
GET /api/v1/analyze?text=OpenSearch is a Distributed Search Engine&field=content
```

**Compare approaches side by side**

```
GET /api/v1/analyze/compare?text=OpenSearch's Distributed Analytics Engines
```

```
our configuration   6 words   opensearch · open · search · distribut · analyt · engin
built-in default    4 words   opensearch's · distributed · analytics · engines
exact-label mode    1 word    "OpenSearch's Distributed Analytics Engines"
```

That last line is the clearest possible illustration of the difference between a prose
field and a label field: the same sentence, treated as one single value.

This is the first place to look whenever a search returns something unexpected — and it
already earned its place. It exposed a flaw in our own configuration during development:
a punctuation mark was being left attached to one word, which would have made exact
product-name matching fail. That was found by running the comparison, not by reviewing
the configuration.

---

## What to be aware of

**Changing field types later requires a rebuild of the index.** Once text has been
processed and stored, the engine cannot reinterpret it — the stored words were produced
by the old rules. Changing a field's type therefore means building a fresh index and
copying the data across.

The system now **detects and reports** this situation at startup rather than failing
confusingly:

```
WARN  Index does not match the declared definition — 4 field(s) differ
WARN  Field types cannot be changed in place. Recreate the index, then restart.
```

A managed, zero-downtime version of that rebuild is scheduled work, not yet delivered.
Until then, changing a field type is a planned maintenance action.

**Documents currently live in the search index only.** The primary database is
configured but not yet written to — that is the next piece of work. This does not affect
the improvements above, but it is why the database appears empty today.

---

## Assurance

The decisions in this change are covered by **20 automated tests** that assert the
intended treatment of each field and the exact order of the text-processing steps.

That order matters more than it appears. Two of the steps only work if they run in the
right sequence — splitting joined words must happen *before* case is discarded, because
the change in case is the only signal that a word is joined. A well-meaning tidy-up
could reorder them and silently degrade search quality with nothing failing. These tests
exist so that cannot happen quietly.

Total automated test count across the project: **81**, all passing.
