# Payara Jakarta Data: reproductions of six defects

Self-contained reproductions of six defects in the Jakarta Data provider of **Payara Server 7** (`fish.payara.data.core`, Jakarta Data 1.0), found on 2026-11-07
while testing a migration. Everything here is generic: two entities (`Author`, `Book`), one repository, one JAX-RS probe, an embedded database.
Nothing depends on the project the defects were found in. One command runs all cases against a clean Payara and prints which ones fail.

| # | Defect | Cases | Issue text |
|---|---|---|---|
| 1 | derived `countBy` / `deleteBy` with an entity-typed parameter is built against the **parameter's** entity | 1b, 1c | [issues/01](issues/01-wrong-entity-for-derived-count-and-delete.md) |
| 2 | `@Query` with a static `ORDER BY` and no `WHERE` becomes `FROM BookBook` | 2a | [issues/02](issues/02-query-static-order-by-without-where.md) |
| 3 | `@Query` with an alias: runtime `Order` appended without the alias, `Page` count query drops the alias | 3a, 3b | [issues/03](issues/03-query-alias-with-runtime-order-and-page.md) |
| 4 | derived `Page<T>`: `ClassCastException` when exactly one row matches | 4c | [issues/04](issues/04-derived-page-classcastexception-single-row.md) |
| 5 | derived `Page<T>` with `StartsWith` / `EndsWith` / `Contains` returns an empty page (the `List` variant works) | 5b, 5c, 5d | [issues/05](issues/05-derived-page-startswith-endswith-contains-empty.md) |
| 6 | derived `deleteBy…` inside a caller's transaction reports success but deletes nothing | 6a, 6b | [issues/06](issues/06-derived-delete-in-caller-transaction-deletes-nothing.md) |

Every defect has **controls** (cases that must pass: the same call in a form that works), so a failing case is not a setup problem. Case ids are printed with the result.

## Run it

Requirements: JDK 21, Maven 3.9+, and either Docker or just `curl` (second option). No database, no Payara installation, no credentials.

```sh
./run.sh            # Docker: builds the WAR, runs payara/server-full:7.2026.10, prints the cases, removes the container
./run-micro.sh      # no Docker: runs the same WAR on Payara Micro 7.2026.10 (downloads ~100 MB from Maven Central once)
```

Other versions: `PAYARA_IMAGE=payara/server-full:7.2026.9 ./run.sh`, `PAYARA_VERSION=7.2026.9 ./run-micro.sh`. `KEEP=1 ./run.sh` keeps the container;
the probe is then at `http://localhost:18080/repro/api/probe` (`/repro/api/probe` on the Micro port 18081).

The expected result **on 7.2026.10 (build 18)** is in [`EXPECTED-OUTPUT-7.2026.10.txt`](EXPECTED-OUTPUT-7.2026.10.txt): `SUMMARY: 9 passed, 11 failed of 20`
(the 11 failures are exactly 1b, 1c, 2a, 3a, 3b, 4c, 5b, 5c, 5d, 6a, 6b). A fixed Payara shows 20 passed.

Results per version (same eleven failures each): Payara Server 7.2026.5, 7.2026.9, 7.2026.10; Payara Micro 7.2026.10.
The cited source is unchanged on `main` at `c54bcd87c8` (2026-11-05).

Not Docker, not Micro: the Payara Server zip (`https://repo1.maven.org/maven2/fish/payara/distributions/payara/7.2026.10/payara-7.2026.10.zip`) works too, but on a machine with other
Hazelcast / LAN services it may fail to start because the data grid tries to cluster; disable it first (`asadmin set-hazelcast-configuration --enabled=false`) or use Micro with `--nocluster` (what `run-micro.sh` does).

## How it works

- `src/main/java/repro/model`: `Author`, `Book` (field access, `Book.author` is `@ManyToOne Author`, deliberately named like its type for defect 1).
- `src/main/java/repro/repo/BookRepository.java`: one Jakarta Data `@Repository` (`CrudRepository<Book, Long>`); every method is commented with the defect it belongs to.
- `src/main/java/repro/web`: `ProbeResource` (`GET /repro/api/probe`) runs the cases in `Cases`. Data: First Author (Alpha, Beta, Gamma), Second Author (Delta, Epsilon, Zeta).
  Read-only cases run in a transaction that is always rolled back; the delete cases (6) commit, then count with plain JPQL in a new transaction, and the data is reseeded before and after.
- `src/main/resources/META-INF/persistence.xml`: Payara's built-in `java:comp/DefaultDataSource`, `drop-and-create` schema generation. `pom.xml` depends only on `jakarta.jakartaee-api:11.0.0` (provided).

## Upstream state (searched 2026-11-08, `payara/Payara` GitHub issues and pull requests)

No report matches the six defects. Related: [#8453](https://github.com/payara/Payara/issues/8453) (generated JPQL uses the class simple name instead of `@Entity(name)`, open),
[#8452](https://github.com/payara/Payara/issues/8452) (`@Id` in a `@MappedSuperclass`, open), [#7947](https://github.com/payara/Payara/issues/7947) (`save(Parent)` duplicates child rows, open),
[PR #8009](https://github.com/payara/Payara/pull/8009) (entity type of a `@Query` resolved from other methods' signatures, merged; same family as defect 1).
Search again before filing; the issue texts in `issues/` are written to be pasted into the "Bug Report" template (summary, environment, steps, expected, current, source analysis, workaround).

## Notes for whoever files the issues

- The source analysis in each issue text names the code at tag `payara-server-7.2026.10` (links with line numbers). Where a statement is a reading of the code that was not confirmed by a patch it says so.
- Defect 6 (silent non-delete) and defect 5 (silent empty page) are the ones that give wrong results without any error; 1 to 4 fail loudly.
- This project is not under a license on purpose: use it as you like for the reports.
