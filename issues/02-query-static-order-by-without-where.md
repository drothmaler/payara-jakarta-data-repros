# @Query with a static ORDER BY and no WHERE clause produces "FROM BookBook" (entity name duplicated), also for plain JDQL

*Suggested issue title (Payara/Payara, "Bug Report"):* **@Query with a static ORDER BY and no WHERE clause produces "FROM BookBook" (entity name duplicated), also for plain JDQL**

## Summary

`@Query("from Book order by title") List<Book> jdqlFromOrdered();` fails at the first call with `Problem compiling [FROM BookBook ORDER BY title]. The abstract schema type 'BookBook' is unknown.`
The same happens for a JDQL projection (`select title from Book order by title`) and for JPQL with an alias (`select b from Book b order by b.title` becomes `SELECT b FROM BookBook b ORDER BY b.title`).
With a `WHERE` clause (control 2b) or without a `FROM` clause (`order by title`, control 2e) the query works. Affects every `@Query` that has a `FROM` and an `ORDER BY` and no `WHERE`, whatever the entity or select list.

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), case 2a also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **2a**, **2c**, **2d** (controls **2b**, **2e** pass).

## Expected outcome

The queries run as written and return the six books (or titles) sorted by title.

## Current outcome

```
FAIL 2a  @Query("select b from Book b order by b.title")  (static ORDER BY, no WHERE)
       actual:   EXCEPTION ...JPQLException: Problem compiling [SELECT b FROM BookBook b ORDER BY b.title]. [14, 22] The abstract schema type 'BookBook' is unknown.
PASS 2b  control: the same query with a WHERE (b.id > 0)
FAIL 2c  @Query("from Book order by title")  (the same in pure JDQL)
       actual:   EXCEPTION ...JPQLException: Problem compiling [FROM BookBook ORDER BY title]. [17, 25] The abstract schema type 'BookBook' is unknown.
FAIL 2d  @Query("select title from Book order by title")  (pure JDQL projection)
       actual:   EXCEPTION ...JPQLException: Problem compiling [SELECT title FROM BookBook ORDER BY title]. [18, 26] The abstract schema type 'BookBook' is unknown.
PASS 2e  control: @Query("order by title")  (pure JDQL without FROM)
```

## Specification check (Jakarta Data 1.0)

**Spec reference:** [Jakarta Data 1.0, 5.7 Syntax](https://jakarta.ee/specifications/data/1.0/jakarta-data-1.0.html#_syntax); [Jakarta Data 1.0, 5.5.1 From clause](https://jakarta.ee/specifications/data/1.0/jakarta-data-1.0.html#_from_clause); [Jakarta Data 1.0, 5.5.2 Where clause](https://jakarta.ee/specifications/data/1.0/jakarta-data-1.0.html#_where_clause); [Jakarta Data 1.0, 5.5.5 Order clause](https://jakarta.ee/specifications/data/1.0/jakarta-data-1.0.html#_order_clause); [Jakarta Data 1.0, 5 Jakarta Data Query Language](https://jakarta.ee/specifications/data/1.0/jakarta-data-1.0.html#_jakarta_data_query_language).

Bug, and a violation of the required JDQL support for 2c and 2d. The JDQL grammar (spec section 5.7) is `select_statement : select_clause? from_clause? where_clause? orderby_clause?` with `from_clause : 'FROM' entity_name`
and `orderby_clause : 'ORDER' 'BY' orderby_item (',' orderby_item)*`, so `from Book order by title` and `select title from Book order by title` are valid JDQL; the where clause *"is always optional"* (5.5.2).
Case 2a uses an identification variable (`Book b`), which is JPQL, not JDQL: a provider *"might choose to allow the use of a much larger subset of JPQL"* (chapter 5), and Payara does (control 2b), so 2a is the same defect in that extension.

## Source analysis (read at tag `payara-server-7.2026.10`)

`QueryOperationUtility.addQueryPart` rebuilds the query clause by clause. Without a `WHERE` the `FROM` case appends `"FROM " + <entity name>` ([L290-L303](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L290-L303), the `else` branch uses `getSingleEntityName(entityClass.getName())`),
and the `ORDER` case then appends `query.substring(fromIndex + 5, orderIndex)` ([L325](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L325)), which is the original `Book b ` / `Book ` again: `FROM Book` + `Book b ` = `FROM BookBook b`.
The clauses are located with a plain `indexOf` on the lower-cased query (`preprocessQueryString`, [L249](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L249)), so the rewrite also depends on keyword positions, not on a parse.

## Workaround

Leave out the `FROM` (`@Query("order by title")`, control 2e; the entity comes from the return type), add a `WHERE` (even `where id > 0`), or sort with a runtime `Order`.
