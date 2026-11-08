# JPQL @Query with an alias: runtime Order<T> is appended without the alias, and the Page count query drops the alias

*Suggested issue title (Payara/Payara, "Bug Report"):* **JPQL @Query with an alias: runtime Order<T> is appended without the alias, and the Page count query drops the alias**

## Summary

For a `@Query` written in JPQL with an identification variable (`select b from Book b where lower(b.title) like ?1`):
- with a runtime `Order<Book>` parameter the provider appends `ORDER BY title ASC` **without** the alias (`The identification variable 'title' is not defined in the FROM clause`);
- with a `Page<Book>` return it builds the count query `SELECT COUNT(this) FROM Book WHERE lower(b.title) like ?1`, which drops the alias but keeps `b.title` (case 3b, sorted by a static `order by b.title` in the query).
The alias-less JDQL forms (`from Book where lower(title) like ?1`, with a runtime `Order` or a static `order by title`) work (controls 3c, 3d). So an explicit JPQL alias, which Payara otherwise accepts in `@Query`, cannot be combined with dynamic sorting or paging.

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), case 3a also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **3a** and **3b** (controls **3c**, **3d** pass).

## Expected outcome

A JPQL `@Query` with an alias works with `Order` and `Page`, the same as its JDQL equivalent.

## Current outcome

```
FAIL 3a  @Query("select b from Book b where lower(b.title) like ?1") with Order<Book>
       actual:   EXCEPTION ...JPQLException: Problem compiling
                 [SELECT b FROM Book b WHERE lower(b.title) like ?1 ORDER BY title ASC].
                 The identification variable 'title' is not defined in the FROM clause.
FAIL 3b  @Query("select b from Book b where lower(b.title) like ?1 order by b.title") returning Page<Book> (the count query)
       actual:   EXCEPTION ...JPQLException: Problem compiling
                 [SELECT COUNT(this) FROM Book WHERE lower(b.title) like ?1 ]. The state field path 'b.title' cannot be resolved to a valid type.
PASS 3c  control: alias-less JDQL "from Book where lower(title) like ?1" with Page + Order
PASS 3d  control: alias-less JDQL "from Book where lower(title) like ?1 order by title" returning Page
```

## Specification check (Jakarta Data 1.0)

Not a violation of required behaviour, but a defect in Payara's JPQL support. The queries use an identification variable, which JDQL does not have (`from_clause : 'FROM' entity_name`, spec section 5.7), so they are JPQL;
JPQL in `@Query` is an optional provider extension (*"A Jakarta Data provider backed by access to a relational database might choose to allow the use of a much larger subset of JPQL—or even the whole language—via the @Query annotation. Such extensions are not required by this specification."*, chapter 5).
Payara accepts these queries (they run without `Order` / `Page`), and the way they are combined with `Order` and `Page` is spec-conformant: a `@Query` without `ORDER BY` that ends with a `WHERE` *"can use other mechanisms … for providing sort criteria"* (4.7.1, case 3a),
and a `Page` needs sort criteria (4.8.1.1), here given by the query's own `ORDER BY` (case 3b). The JDQL equivalents (3c, 3d) are the portable form and work.

## Source analysis (read at tag `payara-server-7.2026.10`)

- The `@Query` path calls `createQueriesForPagination(..., rootAlias = null)` ([FindOperationUtility L295](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/FindOperationUtility.java#L295)); `appendSortQuery` ([DataCommonOperationUtility L405-L460](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/DataCommonOperationUtility.java#L405-L460)) prefixes the alias only when `rootAlias` is non-empty, so the property is appended bare. The non-paginated `List` + `Order` call (case 3a) goes through `QueryOperationUtility.processQueryOperation`, which also passes no alias.
- `createCountQuery` ([FindOperationUtility L361-L385](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/FindOperationUtility.java#L361-L385)) builds `SELECT COUNT(this) FROM <Entity>` and copies the `WHERE` text of the original query, without the alias declaration.

## Workaround

Write the query alias-less in JDQL (`from Book where ...`, controls 3c and 3d). That excludes joins and fetch joins, so a runtime sort over a joined entity is not possible this way.
