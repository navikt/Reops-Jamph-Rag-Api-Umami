 RAG v2 SQL Generation Pipeline

Architecture

Two-path system for SQL generation:
- Template-based (linear, rankings) → Fast, consistent, predictable
- Schema-based (default) → Flexible, full LLM generation

Pipeline Steps

Step 1: PickASqlQuestionTypeLlm
Classifies user question into query type:
- `linear` - Trend analysis with regression
- `rankings` - Top/bottom lists
- `default` - Everything else

Step 2: PickVariableJsonLlm (template types only)
Extracts variable values from natural language into JSON.
Skipped for `default` type.

Step 3: ConstructSQL (template types only)
Replaces [PLACEHOLDERS] in SQL templates with extracted values.
Skipped for `default` type.

Step 4: OtherLlm (default type only)
Full schema-based LLM SQL generation for queries that don't fit templates.

Query Types

1. rankings

Top/bottom lists with ORDER BY + LIMIT
Examples: Top pages, top sources, most visited OS/browser, most searched terms
Pattern: GROUP BY + ORDER BY DESC/ASC + LIMIT
How: Standard step 1.2.3.

2. linear - Schema

Regression analysis for trends
Examples: "trend i daglige sidevisninger"
How: Give variables put into formula.
How: Standard step 1.2(simplified, the model does not need to know about rmse etc).3.

3. funnel

Multi-step conversion paths
Examples: "fra start til fullført søknad", "fra forsiden til linkcardkomponentsiden"
Pattern: Multiple CTEs with step filtering, conversion rates
SQL: Window functions or step-by-step JOINs
How: 1.2.3 Code ?? node search?? first find shortest path between the sites. Then count for each step. fint more routes. etc??
Or respond, cannot do that right now, but look in grafbyggeren.

4. Search terms popularity
Finsished logic for finding search term. instert term. BAM. Done.

5. Referrers
Example "Hvor navigerer brukere etter å ha søkt" "hvor kommer brukere fra"
How: Not sure why the models cant handle it.

6. Actions on the page.
Example Hva gjør brukere på siden.
Finsished logic for listing actions. BAM. Done.

7. other/default

Everything else: Simple aggregations, time grouping, custom metrics
Examples: Daily/monthly counts, OS breakdown, 404 errors, search counts, time on page
Entire Schema available for any other type of query that doesn't fit the above categories.

Direct llm generation