# Derived query returning Page<T>: StartsWith / EndsWith / Contains return an empty page (the List variants work)

*Suggested issue title (Payara/Payara, "Bug Report"):* **Derived query returning Page<T>: StartsWith / EndsWith / Contains return an empty page (the List variants work)**

## Summary

`List<Book> findByTitleStartsWith(String)` returns `[Beta]` for `"B"`, but `Page<Book> findByTitleStartsWith(String, PageRequest, Order<Book>)` with the same argument returns an empty page (`size=0 total=0`), and the same for `EndsWith` and `Contains`.
No exception, so the wrong result is silent.

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21), also reproduced on 7.2026.9 and 7.2026.5 and on Payara Micro 7.2026.10
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **5b**, **5c**, **5d** (control **5a**, the `List` variant, passes).

## Expected outcome

`size=1 total=1` (`StartsWith "B"`), `size=2 total=5` (`EndsWith "a"`), `size=2 total=2` (`Contains "et"`).

## Current outcome

```
PASS 5a  control: List<Book> findByTitleStartsWith("B")           actual: [Beta]
FAIL 5b  Page<Book> findByTitleStartsWith("B", PageRequest, Order)  actual: size=0 total=0 []
FAIL 5c  Page<Book> findByTitleEndsWith("a", PageRequest, Order)    actual: size=0 total=0 []
FAIL 5d  Page<Book> findByTitleContains("et", PageRequest, Order)   actual: size=0 total=0 []
```

## Source analysis (read at tag `payara-server-7.2026.10`)

The `List` path adds the wildcards when it binds the parameters ([QueryByNameOperationUtility.setQueryParameters L521-L545](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L521-L545): `StartsWith` appends `%`, `EndsWith` prepends it, `Contains` / `Like` wrap the value).
`PageImpl` binds the raw arguments for both the page query and the count query ([L175-L187](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/cdi/extension/PageImpl.java#L175-L187)), which is consistent with the observed empty pages. (Not verified with a patch.)
Side observation (from the code, not tested separately): on the `List` path `Like` is also wrapped in `%…%` (`findByTitleLike("B%")` binds `%B%%`), although Jakarta Data defines `Like` as a pattern the caller writes; whether the `Page` path does the same was not determined.

## Workaround

Use `Like` with an explicit pattern for the `Page` variant (`findByTitleLike("B%", ...)`), or return a `List` with `Limit`.
