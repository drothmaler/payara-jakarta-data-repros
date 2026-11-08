package repro.web;

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
 * Every case runs in its own transaction that is always rolled back.
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

    public List<Result> runAll() throws Exception {
        reseed();
        List<Result> r = new ArrayList<>();

        r.add(read("2a", "@Query(\"select b from Book b order by b.title\")  (static ORDER BY, no WHERE)",
                "[Alpha, Beta, Delta, Epsilon, Gamma, Zeta]", () -> titles(books.allOrdered())));
        r.add(read("2b", "control: the same query with a WHERE (b.id > 0)",
                "[Alpha, Beta, Delta, Epsilon, Gamma, Zeta]", () -> titles(books.orderedWithWhere())));
        r.add(read("2c", "@Query(\"from Book order by title\")  (the same in pure JDQL)",
                "[Alpha, Beta, Delta, Epsilon, Gamma, Zeta]", () -> titles(books.jdqlFromOrdered())));
        r.add(read("2d", "@Query(\"select title from Book order by title\")  (pure JDQL projection)",
                "[Alpha, Beta, Delta, Epsilon, Gamma, Zeta]", () -> books.jdqlTitlesOrdered().toString()));
        r.add(read("2e", "control: @Query(\"order by title\")  (pure JDQL without FROM)",
                "[Alpha, Beta, Delta, Epsilon, Gamma, Zeta]", () -> titles(books.jdqlOrderOnly())));
        reseed();
        return r;
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
}
