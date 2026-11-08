# Payara Jakarta Data reproduction 2: @Query with a static ORDER BY and no WHERE clause produces "FROM BookBook" (entity name duplicated), also for plain JDQL

Isolated reproduction of **one** defect in the Jakarta Data provider of **Payara Server 7** (`fish.payara.data.core`, Jakarta Data 1.0).
This branch contains only the repository methods and cases of defect 2; the branch `main` has all six defects in one application.

Issue text (ready for the Payara "Bug Report" template): [`issues/02-query-static-order-by-without-where.md`](issues/02-query-static-order-by-without-where.md)

| | Cases |
|---|---|
| reproductions (FAIL on 7.2026.10) | 2a, 2c, 2d |
| controls (PASS: the same call in a form that works) | 2b, 2e |

## Run it

Requirements: JDK 21, Maven 3.9+, and either Docker or just `curl`. No database, no Payara installation, no credentials.

```sh
./run.sh            # Docker: builds the WAR, runs payara/server-full:7.2026.10, prints the cases, removes the container
./run-micro.sh      # no Docker: runs the same WAR on Payara Micro 7.2026.10 (downloads ~100 MB from Maven Central once)
```

Other versions: `PAYARA_IMAGE=payara/server-full:7.2026.9 ./run.sh`, `PAYARA_VERSION=7.2026.9 ./run-micro.sh`. `KEEP=1 ./run.sh` keeps the container;
the probe is then at `http://localhost:18080/repro/api/probe`.

Expected result on 7.2026.10 (build 18), see [`EXPECTED-OUTPUT-7.2026.10.txt`](EXPECTED-OUTPUT-7.2026.10.txt): `SUMMARY: 2 passed, 3 failed of 5`. A fixed Payara shows no failures.

## Contents

- `src/main/java/repro/model`: `Author`, `Book` (`Book.author` is a `@ManyToOne Author`).
- `src/main/java/repro/repo/BookRepository.java`: one Jakarta Data `@Repository` (`CrudRepository<Book, Long>`) with only the methods of defect 2.
- `src/main/java/repro/web`: `ProbeResource` (`GET /repro/api/probe`) runs the cases in `Cases`. Data: First Author (Alpha, Beta, Gamma), Second Author (Delta, Epsilon, Zeta).
- `src/main/resources/META-INF/persistence.xml`: Payara's built-in `java:comp/DefaultDataSource`, `drop-and-create`. `pom.xml` depends only on `jakarta.jakartaee-api:11.0.0` (provided).
