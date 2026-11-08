# @Query with an alias and a WHERE: runtime Order<T> is appended without the alias, and the Page count query drops the alias

*Suggested issue title (Payara/Payara, "Bug Report"):* **@Query with an alias and a WHERE: runtime Order<T> is appended without the alias, and the Page count query drops the alias**

## Summary

For `@Query("select b from Book b where lower(b.title) like ?1")`:
- with a runtime `Order<Book>` parameter the provider appends `ORDER BY title ASC` **without** the alias (`The identification variable 'title' is not defined in the FROM clause`);
- with a `Page<Book>` return it builds the count query `SELECT COUNT(this) FROM Book WHERE lower(b.title) like ?1`, which drops the alias but keeps `b.title`.
The alias-less JDQL form `@Query("from Book where lower(title) like ?1")` works with both (control 3c). So an explicit JPQL alias, which a `@Query` is allowed to use, cannot be combined with dynamic sorting or paging.

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **3a** and **3b** (control **3c** passes).

## Expected outcome

A `@Query` written in JPQL with an alias works with `Order` and `Page` (Jakarta Data 1.0: special parameters such as `Order` and `PageRequest` can be combined with `@Query`).

## Current outcome

```
FAIL 3a  @Query("select b from Book b where lower(b.title) like ?1") with Order<Book>
       actual:   EXCEPTION ...JPQLException: Problem compiling
                 [SELECT b FROM Book b WHERE lower(b.title) like ?1 ORDER BY title ASC].
                 The identification variable 'title' is not defined in the FROM clause.
FAIL 3b  same query returning Page<Book> (the count query)
       actual:   EXCEPTION ...JPQLException: Problem compiling
                 [SELECT COUNT(this) FROM Book WHERE lower(b.title) like ?1]. The state field path 'b.title' cannot be resolved to a valid type.
```

## Source analysis (read at tag `payara-server-7.2026.10`)

- The `@Query` path calls `createQueriesForPagination(..., rootAlias = null)` ([FindOperationUtility L295](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/FindOperationUtility.java#L295)); `appendSortQuery` ([DataCommonOperationUtility L405-L460](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/DataCommonOperationUtility.java#L405-L460)) prefixes the alias only when `rootAlias` is non-empty, so the property is appended bare.
- `createCountQuery` ([FindOperationUtility L361-L385](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/FindOperationUtility.java#L361-L385)) builds `SELECT COUNT(this) FROM <Entity>` and copies the `WHERE` text of the original query, without the alias declaration.

## Workaround

Write the query alias-less (`from Book where ...`, control 3c). That excludes joins and fetch joins, so a runtime sort over a joined entity is not possible this way.
