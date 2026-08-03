# OpenSearch query walkthrough

Eighteen queries against the `documents` index, in a deliberate teaching order.
Every result shown was captured from a real run — including the three that return
nothing on purpose.

Run them in **OpenSearch Dashboards → Management → Dev Tools**
(`docker compose --profile tools up -d`, then <http://localhost:5601>), or against
`localhost:9200` with curl.

> Captured against OpenSearch 3.7.0, index `documents`, 11 documents, 1 primary
> shard / 0 replicas, cluster green.

---

## I — Finding things

A database answers *does this row equal that value*. A search engine answers *which
documents are most about this subject*. The number beside each hit is a relevance
score, and it is the whole difference.

### 1. `match` — ranked full-text search

```json
GET documents/_search
{ "query": { "match": { "content": "search engine" } } }
```

```text
total matched: 2
 2.081  Introduction to OpenSearch
 1.405  Introduction to OpenSearch
```

Two copies of the same article scored differently. Identical words, different
scores — because scoring depends on the length of the document the words sit in, not
just their presence.

### 2. `match_phrase` — words together, in order

```json
{ "query": { "match_phrase": { "content": "search and analytics engine" } } }
```

```text
total matched: 2
 3.211  Introduction to OpenSearch
 2.167  Introduction to OpenSearch
```

Same documents, but scores rose from 2.08 to 3.21. Matching an exact phrase is
stronger evidence of relevance than matching scattered words.

### 3. The same words, reordered

```json
{ "query": { "match_phrase": { "content": "analytics and search engine" } } }
```

```text
total matched: 0
```

Every word still exists in that document. Swapping two of them drops the match to
zero. This is the control you reach for when `"senior data engineer"` must not match
`"engineer of senior data"`.

### 4. `multi_match` with a boost

```json
{
  "query": {
    "multi_match": {
      "query":  "indexing performance",
      "fields": [ "title^3", "content" ]
    }
  }
}
```

```text
total matched: 1
 3.108  Bulk indexing throughput
```

`title^3` means a match in the title counts three times as much as one in the body.
The single most effective lever on result quality, and it is one character of config.

### 5. The identical query, unboosted

```json
{ "query": { "multi_match": { "query": "indexing performance",
                              "fields": [ "title", "content" ] } } }
```

```text
total matched: 1
 1.036  Bulk indexing throughput
```

3.108 against 1.036 — the boost tripled the score. With one result the order cannot
change, but across thousands of documents this is what decides page one.

---

## II — Filtering exactly

Not every question is fuzzy. "Category is operations" has one right answer, and
asking it the wrong way is the most common and most expensive mistake in a search
project.

### 6. `term` on an exact field

```json
{ "query": { "term": { "category.keyword": "operations" } } }
```

```text
total matched: 3
 1.000  Choosing a shard count
 1.000  Bulk indexing throughput
 1.000  Container memory limits and the JVM
```

Every score is exactly `1.000`. A filter is a yes-or-no question, so there is no such
thing as being "more in" a category. That flatness is the signal it is behaving as a
filter.

### 7. The same filter, one word different

```json
{ "query": { "term": { "category": "Operations" } } }
```

```text
total matched: 0
```

Three documents *are* in that category. This query finds none, and reports no error.
Two causes at once: the stored field was lowercased when indexed, and `term` does not
process your search text — so capital-O `"Operations"` is compared against lowercase
`operations` and never matches.

> **The one point worth pausing on.** Text fields are broken into lowercase words for
> flexible matching. Keyword fields are stored exactly as given, for filtering,
> sorting and counting. The same data usually needs both, which is why `category` and
> `category.keyword` are different fields with different behaviour.
>
> Getting this wrong does not throw an error — it returns zero results, or quietly
> wrong counts on a dashboard.

### 8. `bool` — combining four kinds of intent

```json
{
  "query": {
    "bool": {
      "must":     [ { "match": { "content": "documents" } } ],
      "filter":   [ { "terms": { "category.keyword": [ "search", "operations" ] } } ],
      "should":   [ { "match": { "title": "aggregations" } } ],
      "must_not": [ { "term":  { "author.keyword": "Marcus Webb" } } ]
    }
  }
}
```

```text
total matched: 3
 1.486  Aggregations for analytics   [search]
 0.439  Introduction to OpenSearch   [search]
 0.429  Bulk indexing throughput     [operations]
```

Four clauses, four jobs. `must` is the search box. `filter` is the checkboxes down
the side. `must_not` is the exclusion. `should` is "nice to have" — and it is why the
aggregations article scored 1.486 while the others sit near 0.43 rather than being
excluded. Only `filter` is cacheable and score-free, which is what keeps it fast.

---

## III — Presenting results

### 9. `highlight` — show why each hit matched

```json
{
  "query": { "match": { "content": "shard heap" } },
  "highlight": { "fields": { "content": { "fragment_size": 90 } } }
}
```

```text
total matched: 2
 1.597  Choosing a shard count
   Oversharding wastes [heap] on cluster state; undersharding caps throughput.
   A few tens of gigabytes per [shard] is a reasonable starting point.
 0.963  Container memory limits and the JVM
   ...will size its [heap] far too large...
```

The engine hands back the exact sentences that matched, already marked up. Users
trust results they can see the reason for.

### 10. `fuzziness` — tolerate typos

```json
{ "query": { "match": { "title": { "query": "agregations", "fuzziness": "AUTO" } } } }
```

```text
with fuzziness:     1 hit   0.942  Aggregations for analytics
without fuzziness:  0 hits
```

`agregations` is missing a letter. With one setting the right article is found;
without it the user gets nothing and assumes the content does not exist.

### 11. Sort and paginate

```json
{
  "from": 3,
  "size": 3,
  "sort": [ { "createdAt": "desc" }, { "title.keyword": "asc" } ],
  "query": { "match_all": {} }
}
```

```text
total matched: 11
 0.000  Choosing a shard count
 0.000  Container memory limits and the JVM
 0.000  Introduction to OpenSearch
```

`total` stays 11 while only 3 come back — that is how the UI knows to draw four
pages. And every score is `0.000`: an explicit sort switches relevance off entirely.
Sorting by date and expecting the best match first is a contradiction. Note the
tie-breaker on `title.keyword` — without a second sort key, paging can repeat or skip
rows.

### 12. `range` — date and numeric windows

```json
{ "size": 0, "query": { "range": { "createdAt": { "gte": "now-1h", "lte": "now" } } } }
```

```text
total matched: 11
```

`now-1h` is resolved by the engine, so no date arithmetic is needed in application
code. `"size": 0` asks for the count without shipping any documents back.

---

## IV — Answering questions without returning documents

### 13. `terms` — count per group

```json
{ "size": 0, "aggs": { "by_category": { "terms": { "field": "category.keyword" } } } }
```

```text
search          5
operations      3
architecture    1
database        1
java            1
```

This is the "Search (5) / Operations (3)" list beside every filter you have ever
used. One request, no documents transferred.

### 14. Nested aggregation

```json
{
  "size": 0,
  "aggs": {
    "by_category": {
      "terms": { "field": "category.keyword" },
      "aggs": { "top_authors": { "terms": { "field": "author.keyword" } } }
    }
  }
}
```

```text
search          5
   Sourabh Jagtap   3
   Priya Nair       2
operations      3
   Marcus Webb      2
   Aisha Rahman     1
architecture    1
   Marcus Webb      1
```

Aggregations nest to any depth. In SQL this is a grouped query per category; here it
is one round trip.

### 15. Metrics inside buckets

```json
{
  "size": 0,
  "aggs": {
    "by_category": {
      "terms": { "field": "category.keyword" },
      "aggs": { "avg_len": { "avg": { "script": "params._source.content.length()" } } }
    }
  }
}
```

```text
search          5   avg_len=201.0
operations      3   avg_len=231.7
architecture    1   avg_len=235.0
```

Swap `avg` for `sum`, `min`, `max` or `percentiles` and this becomes revenue per
region or p95 latency per endpoint. The shape of the query does not change.

### 16. Search and analytics in one round trip

```json
{
  "size": 0,
  "query": { "match": { "content": "index" } },
  "aggs": {
    "matching_categories": { "terms": { "field": "category.keyword" } },
    "unique_authors":      { "cardinality": { "field": "author.keyword" } }
  }
}
```

```text
matching_categories:
  architecture    1
  operations      1
unique_authors = 1
```

Compare with query 13: five categories there, two here. The aggregation ran over only
the documents matching "index". That is what makes facet counts update as a user
narrows their search — same request, no second call.

---

## V — Showing your work

### 17. `_explain` — the arithmetic behind one score

```json
GET documents/_explain/<document-id>
{ "query": { "match": { "content": "shard heap" } } }
```

```text
1.5967 sum of:
  0.9101 weight(content:shard)
    2.0794 idf,  log(1 + (N - n + 0.5) / (n + 0.5))
       1.0000 n, docs containing term
      11.0000 N, total docs with field
    0.4377 tf,  freq / (freq + k1 * (1 - b + b * dl / avgdl))
       1.2000 k1, term saturation
       0.7500 b,  length normalisation
      38.0000 dl, field length
  0.6865 weight(content:heap)
    1.5686 idf ...
```

The two term scores sum to the total. `shard` scores higher than `heap` because it
appears in only 1 of 11 documents — rarer words are stronger evidence. That is the
whole intuition behind relevance, and the engine will show its working for any result.

### 18. `_analyze` — see the searchable tokens

```json
GET documents/_analyze
{ "field": "content", "text": "OpenSearch is a Distributed Search Engine" }
```

```text
[ 'opensearch', 'is', 'a', 'distributed', 'search', 'engine' ]
```

Everything is lowercased, which is why case-sensitive filtering fails. And
`'opensearch'` stays **one token** — so searching for `search` will never match the
word "OpenSearch". That explains why query 1 found only 2 documents when the word
"search" seems to be everywhere. This one command answers most "why doesn't my search
work" questions.

---

## Where this index stood when these were captured

Field types were **inferred automatically**, which is how `content` ended up with an
unused exact-value twin, and how `category` and `tags` became flexible text when they
only ever need exact matching. Defining the field types explicitly is Day 3's work,
and queries 7 and 18 are the argument for it.
