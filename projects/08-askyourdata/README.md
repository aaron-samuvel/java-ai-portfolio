# AskYourData

AskYourData turns a natural-language question into one of three fixed analytics reports over a 10,000-row synthetic sales fixture. It is a deliberately constrained first step toward safe database question answering: the application chooses among trusted SQL statements, and never executes SQL generated from a prompt.

## Learning goals and current scope

- Map supported user intent to a structured, provider-neutral query plan.
- Validate source tables, group dimensions, metric columns and row limits against a small semantic catalog.
- Execute bounded, reviewed read-only JDBC queries and return chart-ready rows.
- Parse the chosen query and enforce an exact SQL/table allowlist before JDBC.
- Keep synthetic data and report limitations visible to the caller.

The app uses an in-memory H2 `sample_sales` table with five products, four regions, and a 30-day date range. It listens on `127.0.0.1:8088`. It supports product totals, region totals, and an overall total. It does not generate SQL or connect to a production database.

## Flow

```mermaid
flowchart LR
  Q[Question, max 500 chars] --> M[Choose report and structured plan]
  M --> S[Validate semantic table, columns and row limit]
  S --> SQL[Select reviewed constant SQL]
  SQL --> P[Parse, exact SELECT and table policy]
  P --> DB[(Synthetic H2 sales)]
  DB --> R[Rows, SQL and chart hint]
```

## Run and try it

Requires Java 21 and Maven. From this directory:

```powershell
mvn spring-boot:run
```

Ask for product sales:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8088/api/analytics `
  -ContentType 'application/json' -Body '{"question":"Which product has the most revenue?"}'
```

The response includes a report ID, a structured `queryPlan`, rows with `label` and `revenue`, the fixed SQL selected by the application, a chart hint, and an explanation that the source is synthetic. The plan names the `sample_sales` source, selected dimensions, revenue metric inputs (`amount`, `quantity`), empty filters and bounded row limit. A semantic validator checks this plan and its compatibility with the report before the exact SQL allowlist runs. Words containing `product` select product totals, `region` selects regional totals, and other questions select the overall total.

Submit report feedback with `POST http://127.0.0.1:8088/api/analytics/feedback` and JSON such as `{"reportId":"sales_summary","rating":"DOWN","comment":"The result needs a date filter."}`. Feedback is stored in the local H2 database. Do not include personal or confidential information in the comment.

## Tests and exercises

Run `mvn test`. Tests check structured intent plans, semantic source/column/metric/filter/limit restrictions, report compatibility, the fixed SQL policy, and seeded row/category counts.

1. Ask for regional revenue and inspect the report ID and chart rows.
2. Try a prompt asking to delete data. Confirm that it still selects a fixed report and cannot execute the requested command.
3. Add a new fixed report with a test for its intent mapping and a maximum row count.

## Limitations and next steps

The planner is deterministic and supports only product, region and summary intent; it does not generate SQL with an LLM. It has no natural-language filters, one-repair model loop, PostgreSQL least-privilege role, explanation/chart UI, or 50-question benchmark. A strict JSqlParser policy only permits the three reviewed SQL strings and verifies each is one SELECT referencing only `sample_sales`; it is defense in depth, not a database permission boundary. The semantic catalog is a small in-code description, not retrieved from a live database. All seeded sales data is generated and fictional.

**Screenshot placeholder:** add a chart and report result capture when the UI is implemented.

References: [JSqlParser](https://github.com/JSQLParser/JSqlParser), [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc.html).
