# Derived countBy/deleteBy with an entity-typed parameter is built against the parameter's entity instead of the repository's entity

*Suggested issue title (Payara/Payara, "Bug Report"):* **Derived countBy/deleteBy with an entity-typed parameter is built against the parameter's entity instead of the repository's entity**

## Summary

`BookRepository extends CrudRepository<Book, Long>` declares `long countByAuthor(Author author)` and `void deleteByAuthor(Author author)`, where `Author` is an entity and `Book.author` is a `@ManyToOne Author`.
The generated JPQL selects from **`Author`** instead of `Book`, so the query does not compile: `SELECT COUNT(DISTINCT e) FROM Author e WHERE e.author = ?1`.
`findByAuthor(Author)` (returns `List<Book>`) and `countByAuthor_id(Long)` are fine, so only methods whose return type carries no entity (`long`, `void`) and whose parameter is an entity are affected.

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **1b** and **1c** (controls **1a**, **1d** pass).

## Expected outcome

`countByAuthor(author)` returns 3 and `deleteByAuthor(author)` deletes the three books of that author (Jakarta Data 1.0, "Query by Method Name": the entity is the repository's primary entity).

## Current outcome

```
FAIL 1b  countByAuthor(Author)  (property 'author' has the entity type Author)
       actual:   EXCEPTION org.eclipse.persistence.exceptions.JPQLException: Problem compiling
                 [SELECT COUNT(DISTINCT e) FROM Author e WHERE e.author = ?1]. [45, 53] The state field path 'e.author' cannot be resolved to a valid type.
FAIL 1c  deleteByAuthor(Author)
       actual:   EXCEPTION org.eclipse.persistence.exceptions.JPQLException: Problem compiling
                 [SELECT DISTINCT e FROM Author e WHERE e.author = ?1]. ...
```

## Source analysis (read at tag `payara-server-7.2026.10`)

`DynamicInterfaceDataProducer` sets the method's entity from `findEntityTypeInMethod(method)` ([L352](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/cdi/extension/DynamicInterfaceDataProducer.java#L352)).
For a `long` or `void` return type that method falls through to the parameter loop ([DataCommonOperationUtility L286-L309](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/DataCommonOperationUtility.java#L286-L309), the loop at L306) and returns the first parameter whose type is an entity, here `Author`.
That entity then replaces the repository's `Book` in the generated query. Same family as #8009 (fixed for `@Query`), but the derived `countBy` / `deleteBy` path still does it.

## Workaround

`countByAuthor_id(Long authorId)` (case 1d) works; so does an explicit `@Query`.
