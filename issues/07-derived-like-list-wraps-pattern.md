# Derived findBy…Like returning a List wraps the caller's pattern in %…%, so Like behaves like Contains

*Suggested issue title (Payara/Payara, "Bug Report"):* **Derived findBy…Like returning a List wraps the caller's pattern in %…%, so Like behaves like Contains**

## Summary

`List<Book> findByTitleLike(String pattern)` does not use the pattern as given: the provider binds `"%" + pattern + "%"`. `findByTitleLike("%e")` ("ends with e", no title matches) returns `[Beta, Delta, Zeta]` (contains e),
`findByTitleLike("Bet")` (no wildcard, so only the exact title "Bet") returns `[Beta]`, and `findByTitleIgnoreCaseLike("a%")` ("starts with a") returns every title that contains an a.
No exception, so the wrong result is silent. The same pattern works as given in a JDQL `@Query` (control 7e) and in the derived `Page` variant (control 7f).

## Environment

- Payara Server 7.2026.10 (build 18), image `payara/server-full:7.2026.10` (JDK 21)
- Jakarta Data 1.0 API as shipped by Payara, EclipseLink 5.0.1.payara-p1, embedded default data source (no external database)
- Source analysis against tag `payara-server-7.2026.10`; the cited code is unchanged on `main` (c54bcd87c8, 2026-11-05)
- Reproduction project: this repository, `./run.sh` (Docker) or `./run-micro.sh` (no Docker); the case ids below are printed by it

## Steps to reproduce

1. `./run.sh` (or `./run-micro.sh`)
2. Look at cases **7a**, **7b**, **7c** (controls **7d**, **7e**, **7f** pass).

## Expected outcome

The argument is the pattern: `findByTitleLike("%e")` returns `[]`, `findByTitleLike("Bet")` returns `[]`, `findByTitleIgnoreCaseLike("a%")` returns `[Alpha]`.

## Current outcome

```
FAIL 7a  List<Book> findByTitleLike("%e")  (no title ends with e)
       expected: []
       actual:   [Beta, Delta, Zeta]
FAIL 7b  List<Book> findByTitleLike("Bet")  (pattern without wildcard = equality)
       expected: []
       actual:   [Beta]
FAIL 7c  List<Book> findByTitleIgnoreCaseLike("a%")  (starts with a, any case)
       expected: [Alpha]
       actual:   [Alpha, Beta, Delta, Gamma, Zeta]
PASS 7d  control: List<Book> findByTitleLike("B%")                          actual: [Beta]
PASS 7e  control: @Query("where title like ?1") with "%e"                  actual: []
PASS 7f  control: Page<Book> findByTitleLike("%e", PageRequest, Order)      actual: size=0 total=0 []
```

## Specification check (Jakarta Data 1.0)

**Spec reference:** [Query by Method Name addendum 1.0, 2.7 Query by Method Name Conditions](https://jakarta.ee/specifications/data/1.0/jakarta-data-addendum-1.0.html#_query_by_method_name_conditions); [Jakarta Data 1.0, 5.4.4 Like expressions](https://jakarta.ee/specifications/data/1.0/jakarta-data-1.0.html#_like_expressions).

Bug. The Query by Method Name extension (Jakarta Data 1.0 addendum, section 2.7, table "Query by Method Name Conditions") defines `Like` as *"Matches string values against the given pattern"*, and
*"Wildcard characters for patterns are determined by the data store. For relational databases, `_` matches any one character and `%` matches zero or more characters."* The caller writes the pattern; the provider must not add wildcards.
By contrast `Contains`, `StartsWith` and `EndsWith` are defined as *"…with the given substring / beginning / ending, which can be a pattern"*, so adding `%` is correct for those three, and they are not affected.
JDQL `like` (spec section 5.4.4) has the same semantics, which is what control 7e shows.

## Source analysis (read at tag `payara-server-7.2026.10`)

`QueryByNameOperationUtility.setQueryParameters` ([L521-L545](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L521-L545)) treats `Like` like `Contains`:
`else if ("Contains".equals(condition.operator()) || "Like".equals(condition.operator())) { processedArg = "%" + processedArg + "%"; }` ([L537](https://github.com/payara/Payara/blob/payara-server-7.2026.10/appserver/data/data-core/src/main/java/fish/payara/data/core/util/QueryByNameOperationUtility.java#L537)).
That method is used for the non-paginated derived queries only; the `Page` path binds the raw arguments (see the separate issue about `StartsWith` / `EndsWith` / `Contains` returning empty pages), which is why the `Page` variant of `Like` is correct today.

## Workaround

Use a JDQL `@Query("where title like ?1")` (control 7e) or the `Page` variant. Patterns that start and end with `%` are not affected.
