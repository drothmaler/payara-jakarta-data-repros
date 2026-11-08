# @Query with a static ORDER BY and no WHERE clause produces "FROM BookBook" (entity name duplicated)

*Suggested issue title (Payara/Payara, "Bug Report"):* **@Query with a static ORDER BY and no WHERE clause produces "FROM BookBook" (entity name duplicated)**

## Summary

`@Query("select b from Book b order by b.title") List<Book> allOrdered();` fails at the first call with `Problem compiling [SELECT b FROM BookBook b ORDER BY b.title]. The abstract schema type 'BookBook' is unknown.`
The same query with a `WHERE` clause (case 2b) works. Affects every `@Query` that has an `ORDER BY` and no `WHERE`, whatever the entity, alias or select list (also `select b.title from Book b order by b.title`).

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at case **2a** (control **2b** passes).

## Expected outcome

The query runs as written and returns the six books sorted by title.

## Current outcome

```
FAIL 2a  @Query("select b from Book b order by b.title")  (static ORDER BY, no WHERE)
       actual:   EXCEPTION org.eclipse.persistence.exceptions.JPQLException: Problem compiling
                 [SELECT b FROM BookBook b ORDER BY b.title]. [14, 22] The abstract schema type 'BookBook' is unknown.
```

## Source analysis (read at tag `payara-server-7.2026.10`)

`QueryOperationUtility.addQueryPart` rebuilds the query clause by clause. Without a `WHERE` the `FROM` case appends `"FROM " + <entity name>` ([L290-L303](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L290-L303), the `else` branch uses `getSingleEntityName(entityClass.getName())`),
and the `ORDER` case then appends `query.substring(fromIndex + 5, orderIndex)` ([L325](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L325)), which is the original `Book b ` again: `FROM Book` + `Book b ` = `FROM BookBook b`.
The clauses are located with a plain `indexOf` on the lower-cased query (`preprocessQueryString`, [L249](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L249)), so the rewrite also depends on keyword positions, not on a parse.

## Workaround

Add a `WHERE` (even `where b.id > 0`), or leave the sorting to a runtime `Order` (see the separate issue about aliases and `Order`).
