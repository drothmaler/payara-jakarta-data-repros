# Payara Jakarta Data reproduction 8: Repository deletes call EntityManager.clear() on the caller's persistence context: entities the caller already loaded are detached and later changes are silently lost

Isolated reproduction of **one** defect in the Jakarta Data provider of **Payara Server 7** (`fish.payara.data.core`, Jakarta Data 1.0).
This branch contains only the repository methods and cases of defect 8; the branch `main` has all six defects in one application.

Issue text (ready for the Payara "Bug Report" template): [`issues/08-delete-clears-caller-persistence-context.md`](issues/08-delete-clears-caller-persistence-context.md)

| | Cases |
|---|---|
| reproductions (FAIL on 7.2026.10) | 8a, 8b, 8c |
| controls (PASS: the same call in a form that works) | 8d, 8e, 8f |

## Run it

Requirements: JDK 21, Maven 3.9+, and either Docker or just `curl`. No database, no Payara installation, no credentials.

```sh
./run.sh            # Docker: builds the WAR, runs payara/server-full:7.2026.10, prints the cases, removes the container
./run-micro.sh      # no Docker: runs the same WAR on Payara Micro 7.2026.10 (downloads ~100 MB from Maven Central once)
```

Other versions: `PAYARA_IMAGE=payara/server-full:7.2026.9 ./run.sh`, `PAYARA_VERSION=7.2026.9 ./run-micro.sh`. `KEEP=1 ./run.sh` keeps the container;
the probe is then at `http://localhost:18080/repro/api/probe`.

Expected result on 7.2026.10 (build 18), see [`EXPECTED-OUTPUT-7.2026.10.txt`](EXPECTED-OUTPUT-7.2026.10.txt): `SUMMARY: 3 passed, 3 failed of 6`. A fixed Payara shows no failures.

## Contents

- `src/main/java/repro/model`: `Author`, `Book` (`Book.author` is a `@ManyToOne Author`).
- `src/main/java/repro/repo/BookRepository.java`: one Jakarta Data `@Repository` (`CrudRepository<Book, Long>`) with only the methods of defect 8.
- `src/main/java/repro/web`: `ProbeResource` (`GET /repro/api/probe`) runs the cases in `Cases`. Data: First Author (Alpha, Beta, Gamma), Second Author (Delta, Epsilon, Zeta).
- `src/main/resources/META-INF/persistence.xml`: Payara's built-in `java:comp/DefaultDataSource`, `drop-and-create`. `pom.xml` depends only on `jakarta.jakartaee-api:11.0.0` (provided).
