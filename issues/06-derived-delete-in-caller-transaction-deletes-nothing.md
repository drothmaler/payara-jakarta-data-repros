# Derived deleteBy… called inside a caller's transaction reports success but deletes nothing (persistence context cleared before commit)

*Suggested issue title (Payara/Payara, "Bug Report"):* **Derived deleteBy… called inside a caller's transaction reports success but deletes nothing (persistence context cleared before commit)**

## Summary

`void deleteByTitle(String title)` and `void deleteByIdIn(Set<Long> ids)` on a `CrudRepository<Book, Long>` do **not delete** when they are called from a method that runs in a JTA transaction (`@Transactional`, an EJB, ...). No exception, the transaction commits, and the rows are still there.
Called without a surrounding transaction (case 6c) they delete, and an explicit `@Query("delete from Book b where b.id in ?1")` deletes in a transaction (case 6d).

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **6a** and **6b** (controls **6c**, **6d** pass). Each case reseeds six books, deletes one, commits, then counts the rows with plain JPQL in a new transaction.

## Expected outcome

`remaining=5`.

## Current outcome

```
FAIL 6a  deleteByTitle("Alpha") called in a transaction that commits, rows counted afterwards
       expected: remaining=5
       actual:   remaining=6
FAIL 6b  deleteByIdIn(ids of Alpha) called in a transaction that commits
       actual:   remaining=6
PASS 6c  control: deleteByTitle("Alpha") called WITHOUT a surrounding transaction     actual: remaining=5
PASS 6d  control: @Query("delete from Book b where b.id in ?1") in a transaction that commits   actual: remaining=5
```

## Specification check (Jakarta Data 1.0)

Bug. Section 7.3 ("Jakarta Transactions"): when a global transaction is active on the calling thread and the data source can enlist, *"then the repository operation must be performed within the context of the global transaction"*. The delete is reported as done and the caller's transaction commits, but the removal is not part of it.
`void` is a valid return type for a derived delete (addendum, table "Repository Method Return Types"), and `deleteByIdIn(Set<Long>)` uses the `In` condition with the `Set` parameter the addendum specifies (2.7).

## Source analysis (read at tag `payara-server-7.2026.10`)

`QueryByNameOperationUtility.processDeleteByNameOperation` ([L108-L140](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L108-L140)) loads the entities, calls `entityManager.remove(...)` and then `clearCache(...)`, which ends in `entityManager.clear()` ([L161-L184](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L161-L184), the call at L183) **without a flush**.
When the provider started the transaction itself (no caller transaction), `endTransaction` has committed before the `clear()`. When the caller's transaction is still open, the persistence context is shared with it, and the `clear()` discards the pending removals before they are flushed, which is consistent with the observed result. (Not verified with a patch.)

## Workaround

Use an explicit `@Query("delete from ... where ...")` (case 6d), delete with the `EntityManager`, or call the derived delete outside a transaction (case 6c). A flush in the caller cannot help, because the provider clears the persistence context inside the repository call, right after `remove`.
