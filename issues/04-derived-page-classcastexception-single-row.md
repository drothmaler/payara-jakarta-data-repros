# Derived query returning Page<T>: ClassCastException (entity cannot be cast to Long) when exactly one row matches

*Suggested issue title (Payara/Payara, "Bug Report"):* **Derived query returning Page<T>: ClassCastException (entity cannot be cast to Long) when exactly one row matches**

## Summary

`Page<Book> findByTitleLike(String pattern, PageRequest pageRequest, Order<Book> order)` works when no row or several rows match, but throws
`java.lang.ClassCastException: class repro.model.Book cannot be cast to class java.lang.Long` when **exactly one** row matches (`"%Beta%"`). The page content is correct, the failure is in `totalElements()`.

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at case **4c** (controls **4a** with five matches and **4b** with none pass).

## Expected outcome

`size=1 total=1`.

## Current outcome

```
FAIL 4c  Page<Book> findByTitleLike("%Beta%") matching exactly one book
       actual:   EXCEPTION java.lang.ClassCastException: class repro.model.Book cannot be cast to class java.lang.Long
```

## Source analysis (read at tag `payara-server-7.2026.10`)

For derived queries the count query is set to the **select** query: `dataForQuery.setCountQueryString(jpql.toString())` ([QueryByNameOperationUtility L394](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L394)) although a `countClause` is built just above it.
`PageImpl.countTotalElements` ([L175-L195](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/cdi/extension/PageImpl.java#L175-L195)) then runs that query and returns `(Long) results.get(0)` when there is one row ([L190](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/cdi/extension/PageImpl.java#L190)) and `results.size()` when there are several.
So `totalElements()` is the number of rows of the whole result loaded into memory (not a `COUNT`), and a single matching row is cast to `Long`. `CursoredPageImpl` has the same cast ([L228](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/cdi/extension/CursoredPageImpl.java#L228)).

## Workaround

None for derived `Page` queries (the total is only computed when `totalElements()` is called, `PageRequest.ofPage(n, size, false)` avoids it), or use a `@Query` with a `Page` return.
