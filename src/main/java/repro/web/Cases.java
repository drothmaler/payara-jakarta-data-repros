package repro.web;

import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import repro.model.Author;
import repro.model.Book;
import repro.repo.BookRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Data: two authors with three books each (First Author: Alpha, Beta, Gamma; Second Author: Delta, Epsilon, Zeta).
 * Every case that only reads runs in its own, rolled back transaction; the delete cases (issue 6) run last and reseed first.
 */
@ApplicationScoped
public class Cases {
    @Inject
    BookRepository books;
    @PersistenceContext
    EntityManager em;
    @Inject
    Runner runner;

    /** One case: what the Jakarta Data 1.0 specification asks for, and what Payara does. */
    public record Result(String id, String what, String expected, String actual, boolean ok) {
    }

    private static final Order<Book> BY_TITLE = Order.by(Sort.asc("title"));
    private static final PageRequest FIRST_PAGE = PageRequest.ofPage(1, 2, true);

    public List<Result> runAll() throws Exception {
        reseed();
        List<Result> r = new ArrayList<>();

        r.add(read("1a", "control: findByAuthor(Author)", "[Alpha, Beta, Gamma]", () -> titles(books.findByAuthor(author()))));
        r.add(read("1b", "countByAuthor(Author)  (property 'author' has the entity type Author)", "3", () -> String.valueOf(books.countByAuthor(author()))));
        r.add(read("1c", "deleteByAuthor(Author)", "no exception", () -> {
            books.deleteByAuthor(author());
            return "no exception";
        }));
        r.add(read("1d", "control: countByAuthor_id(Long)", "3", () -> String.valueOf(books.countByAuthor_id(firstAuthorId()))));

        r.add(read("2a", "@Query(\"select b from Book b order by b.title\")  (static ORDER BY, no WHERE)",
                "[Alpha, Beta, Delta, Epsilon, Gamma, Zeta]", () -> titles(books.allOrdered())));
        r.add(read("2b", "control: the same query with a WHERE (b.id > 0)",
                "[Alpha, Beta, Delta, Epsilon, Gamma, Zeta]", () -> titles(books.orderedWithWhere())));

        r.add(read("3a", "@Query(\"select b from Book b where lower(b.title) like ?1\") with Order<Book>",
                "[Alpha, Beta, Delta, Gamma, Zeta]", () -> titles(books.searchSorted("%a%", BY_TITLE))));
        r.add(read("3b", "same query returning Page<Book> (the count query)", "size=2 total=5", () -> page(books.searchPage("%a%", FIRST_PAGE))));
        r.add(read("3c", "control: alias-less JDQL \"from Book where lower(title) like ?1\" with Page + Order",
                "size=2 total=5", () -> page(books.searchPageJdql("%a%", FIRST_PAGE, BY_TITLE))));

        r.add(read("4a", "control: Page<Book> findByTitleLike(\"%a%\") matching 5 books", "size=2 total=5", () -> page(books.findByTitleLike("%a%", FIRST_PAGE, BY_TITLE))));
        r.add(read("4b", "control: Page<Book> findByTitleLike(\"%x%\") matching no book", "size=0 total=0", () -> page(books.findByTitleLike("%x%", FIRST_PAGE, BY_TITLE))));
        r.add(read("4c", "Page<Book> findByTitleLike(\"%Beta%\") matching exactly one book", "size=1 total=1", () -> page(books.findByTitleLike("%Beta%", FIRST_PAGE, BY_TITLE))));

        r.add(read("5a", "control: List<Book> findByTitleStartsWith(\"B\")", "[Beta]", () -> titles(books.findByTitleStartsWith("B"))));
        r.add(read("5b", "Page<Book> findByTitleStartsWith(\"B\", PageRequest, Order)", "size=1 total=1", () -> page(books.findByTitleStartsWith("B", FIRST_PAGE, BY_TITLE))));
        r.add(read("5c", "Page<Book> findByTitleEndsWith(\"a\", PageRequest, Order)", "size=2 total=5", () -> page(books.findByTitleEndsWith("a", FIRST_PAGE, BY_TITLE))));
        r.add(read("5d", "Page<Book> findByTitleContains(\"et\", PageRequest, Order)", "size=2 total=2", () -> page(books.findByTitleContains("et", FIRST_PAGE, BY_TITLE))));

        r.add(deleteCase("6a", "deleteByTitle(\"Alpha\") called in a transaction that commits, rows counted afterwards", true, () -> books.deleteByTitle("Alpha"), 5));
        r.add(deleteCase("6b", "deleteByIdIn(ids of Alpha) called in a transaction that commits", true, () -> books.deleteByIdIn(List.of(idOf("Alpha"))), 5));
        r.add(deleteCase("6c", "control: deleteByTitle(\"Alpha\") called WITHOUT a surrounding transaction", false, () -> books.deleteByTitle("Alpha"), 5));
        r.add(deleteCase("6d", "control: @Query(\"delete from Book b where b.id in ?1\") in a transaction that commits", true, () -> books.deleteByIdsQuery(List.of(idOf("Alpha"))), 5));
        reseed();
        return r;
    }

    private Author author() {
        return em.find(Author.class, firstAuthorId());
    }

    private Long firstAuthorId() {
        return em.createQuery("select a.id from Author a where a.name = 'First Author'", Long.class).getSingleResult();
    }

    private Long idOf(String title) {
        return em.createQuery("select b.id from Book b where b.title = ?1", Long.class).setParameter(1, title).getSingleResult();
    }

    private void reseed() throws Exception {
        runner.committed(() -> {
            em.createQuery("delete from Book").executeUpdate();
            em.createQuery("delete from Author").executeUpdate();
            Author first = new Author("First Author");
            Author second = new Author("Second Author");
            em.persist(first);
            em.persist(second);
            for (String title : List.of("Alpha", "Beta", "Gamma")) {
                em.persist(new Book(title, first));
            }
            for (String title : List.of("Delta", "Epsilon", "Zeta")) {
                em.persist(new Book(title, second));
            }
            return "seeded";
        });
    }

    /** Runs the delete (in a committing transaction or without one), then counts the rows with plain JPQL in a new transaction. */
    private Result deleteCase(String id, String what, boolean inTransaction, Runnable delete, int expectedRemaining) {
        String expected = "remaining=" + expectedRemaining;
        try {
            reseed();
            if (inTransaction) {
                runner.committed(() -> {
                    delete.run();
                    return "deleted";
                });
            } else {
                runner.withoutTransaction(() -> {
                    delete.run();
                    return "deleted";
                });
            }
            String actual = runner.committed(() -> "remaining=" + em.createQuery("select count(b) from Book b", Long.class).getSingleResult());
            return new Result(id, what, expected, actual, actual.equals(expected));
        } catch (Throwable t) {
            return new Result(id, what, expected, describe(t), false);
        }
    }

    private Result read(String id, String what, String expected, Callable<String> call) {
        try {
            String actual = runner.inRolledBackTransaction(call);
            return new Result(id, what, expected, actual, actual.startsWith(expected));
        } catch (Throwable t) {
            return new Result(id, what, expected, describe(t), false);
        }
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = String.valueOf(root.getMessage()).replace('\n', ' ').replaceAll("\\s+", " ");
        return "EXCEPTION " + root.getClass().getName() + ": " + (message.length() > 330 ? message.substring(0, 330) + "..." : message);
    }

    private static String titles(List<Book> list) {
        return list.stream().map(Book::getTitle).collect(Collectors.toList()).toString();
    }

    private static String page(Page<Book> page) {
        return "size=" + page.numberOfElements() + " total=" + page.totalElements() + " " + titles(page.content());
    }
}
