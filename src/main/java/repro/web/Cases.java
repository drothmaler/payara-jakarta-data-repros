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
import java.util.Set;

/**
 * Data: two authors with three books each (First Author: Alpha, Beta, Gamma; Second Author: Delta, Epsilon, Zeta).
 * Every case reseeds, deletes in a committing transaction (or without one), then counts the rows in a new transaction.
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

        r.add(deleteCase("6a", "deleteByTitle(\"Alpha\") called in a transaction that commits, rows counted afterwards", true, () -> books.deleteByTitle("Alpha"), 5));
        r.add(deleteCase("6b", "deleteByIdIn(ids of Alpha) called in a transaction that commits", true, () -> books.deleteByIdIn(Set.of(idOf("Alpha"))), 5));
        r.add(deleteCase("6c", "control: deleteByTitle(\"Alpha\") called WITHOUT a surrounding transaction", false, () -> books.deleteByTitle("Alpha"), 5));
        r.add(deleteCase("6d", "control: @Query(\"delete from Book b where b.id in ?1\") in a transaction that commits", true, () -> books.deleteByIdsQuery(List.of(idOf("Alpha"))), 5));
        reseed();
        return r;
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

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = String.valueOf(root.getMessage()).replace('\n', ' ').replaceAll("\\s+", " ");
        return "EXCEPTION " + root.getClass().getName() + ": " + (message.length() > 330 ? message.substring(0, 330) + "..." : message);
    }
}
