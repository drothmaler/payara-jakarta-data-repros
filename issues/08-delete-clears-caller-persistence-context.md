# Repository deletes call EntityManager.clear() on the caller's persistence context: entities the caller already loaded are detached and later changes are silently lost

*Suggested issue title (Payara/Payara, "Bug Report"):* **Repository deletes call EntityManager.clear() on the caller's persistence context: entities the caller already loaded are detached and later changes are silently lost**

## Summary

Inside a JTA transaction the caller loads a `Book` ("Gamma") with its own `@PersistenceContext EntityManager`, calls a repository delete for a *different* book ("Alpha"), then renames Gamma and commits.
The rename is not stored: after the repository call `em.contains(gamma)` is `false`, because the repository called `EntityManager.clear()` on the transaction-scoped persistence context it shares with the caller.
Affected: derived `deleteBy…` (8a), `@Delete` with `@By` (8b) and `@Query` delete (8c); by the code also `@Query` update. Not affected: a repository read (8d), `EntityManager.remove` by the caller (8e).
No exception, the transaction commits, and the change is lost.

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21)
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **8a**, **8b**, **8c** (controls **8d**, **8e**, **8f** pass). Each case reseeds six books and runs one committing transaction: `gamma = em.find(...)`, the repository call, `gamma.setTitle("Gamma2")`; then a new transaction checks whether "Gamma2" was stored.

## Expected outcome

`renamed=true`: a repository call does not detach the caller's managed entities, so a change made to them later in the same transaction is committed.

## Current outcome

```
FAIL 8a  rename a managed Book after deleteByTitle("Alpha") in the same transaction
       expected: renamed=true
       actual:   renamed=false managedAfterCall=false
FAIL 8b  rename a managed Book after @Delete removeByTitle(@By("title") "Alpha")
       actual:   renamed=false managedAfterCall=false
FAIL 8c  rename a managed Book after @Query("delete from Book where title = ?1")
       actual:   renamed=false managedAfterCall=false
PASS 8d  control: rename a managed Book after books.findById(...)            actual: renamed=true managedAfterCall=true
PASS 8e  control: rename a managed Book after em.remove(Alpha)               actual: renamed=true managedAfterCall=true
PASS 8f  control: rename a managed Book BEFORE deleteByTitle("Alpha")        actual: renamed=true
```

(8f passes because EclipseLink flushes pending changes before the repository's select; changes made *after* the call are the ones lost.)

## Specification check

Not a violation of a Jakarta Data 1.0 rule, but a defect: Jakarta Data 1.0 leaves the integration with Jakarta Persistence undefined (section 7.4: *"Integration with Jakarta Persistence is left undefined in this first release of Jakarta Data."*).
What the spec does say is that repository work in a global transaction *"is usually transparent to the code acting as client of the repository"* (section 7.3).
Payara's repository uses the caller's transaction-scoped persistence context (control 8d: entities stay managed after a repository read), and Jakarta Persistence defines `EntityManager.clear()` as detaching *all* managed entities of that context, with unflushed changes not persisted.
Jakarta Persistence does not require clearing after bulk update/delete (it only says the persistence context is not synchronized with the result of a bulk operation), and `EntityManager.remove` (8e) needs no clear at all. So the provider discards state that belongs to the caller, without the caller being able to see or prevent it.

## Source analysis (read at tag `payara-server-7.2026.10`)

Three paths end in `entityManager.clear()` on the shared persistence context after evicting the second-level cache:
- derived delete: `QueryByNameOperationUtility.processDeleteByNameOperation` ([L108-L140](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L108-L140)) → `clearCache` ([L161-L184](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L161-L184), `clear()` at L166 and L183); it uses `remove()`, so the clear is not needed for consistency;
- `@Delete` + `@By`: `DeleteOperationUtility.processDeleteByOperation` ([L70-L117](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/DeleteOperationUtility.java#L70-L117)) → `clearCache` ([L134-L143](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/DeleteOperationUtility.java#L134-L143));
- `@Query` delete and update: `QueryOperationUtility` ([L159-L167](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L159-L167)) → `clearCache` ([L466-L475](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryOperationUtility.java#L466-L475)).

When there is no caller transaction the provider owns the persistence context and clearing it is harmless; the problem is the shared, caller-owned context. The derived-delete path additionally loses its own removals because of the same `clear()` (separate issue: *derived deleteBy… inside a caller's transaction deletes nothing*).

## Workaround

Re-load (`em.find`) the entities after a repository delete or `@Query` update in the same transaction, or delete with the `EntityManager`.
