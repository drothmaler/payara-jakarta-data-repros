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

/**
 * Data: two authors with three books each (First Author: Alpha, Beta, Gamma; Second Author: Delta, Epsilon, Zeta).
 * Every case reseeds, runs one committing transaction (load Gamma, call, rename Gamma), then checks the title in a new transaction.
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

        r.add(renameCase("8a", "rename a managed Book after deleteByTitle(\"Alpha\") in the same transaction", () -> books.deleteByTitle("Alpha")));
        r.add(renameCase("8b", "rename a managed Book after @Delete removeByTitle(@By(\"title\") \"Alpha\")", () -> books.removeByTitle("Alpha")));
        r.add(renameCase("8c", "rename a managed Book after @Query(\"delete from Book where title = ?1\")", () -> books.deleteTitled("Alpha")));
        r.add(renameCase("8d", "control: rename a managed Book after books.findById(...)", () -> books.findById(idOf("Alpha"))));
        r.add(renameCase("8e", "control: rename a managed Book after em.remove(Alpha)", () -> em.remove(em.find(Book.class, idOf("Alpha")))));
        r.add(renameBeforeCase("8f", "control: rename a managed Book BEFORE deleteByTitle(\"Alpha\") in the same transaction", () -> books.deleteByTitle("Alpha")));
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

    /** In one committing transaction: load Gamma (managed), run the call, rename Gamma; then check the title in a new transaction. */
    private Result renameCase(String id, String what, Runnable call) {
        String expected = "renamed=true";
        try {
            reseed();
            String managed = runner.committed(() -> {
                Book gamma = em.find(Book.class, idOf("Gamma"));
                call.run();
                boolean stillManaged = em.contains(gamma);
                gamma.setTitle("Gamma2");
                return "managedAfterCall=" + stillManaged;
            });
            String actual = "renamed=" + runner.committed(() -> String.valueOf(
                    em.createQuery("select count(b) from Book b where b.title = 'Gamma2'", Long.class).getSingleResult() == 1)) + " " + managed;
            return new Result(id, what, expected, actual, actual.startsWith(expected));
        } catch (Throwable t) {
            return new Result(id, what, expected, describe(t), false);
        }
    }

    /** Like renameCase, but Gamma is renamed before the call. */
    private Result renameBeforeCase(String id, String what, Runnable call) {
        String expected = "renamed=true";
        try {
            reseed();
            runner.committed(() -> {
                Book gamma = em.find(Book.class, idOf("Gamma"));
                gamma.setTitle("Gamma2");
                call.run();
                return "done";
            });
            String actual = "renamed=" + runner.committed(() -> String.valueOf(
                    em.createQuery("select count(b) from Book b where b.title = 'Gamma2'", Long.class).getSingleResult() == 1));
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
}
