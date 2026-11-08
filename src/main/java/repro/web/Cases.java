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

    private static final Order<Book> BY_TITLE = Order.by(Sort.asc("title"));
    private static final PageRequest FIRST_PAGE = PageRequest.ofPage(1, 2, true);

    public List<Result> runAll() throws Exception {
        reseed();
        List<Result> r = new ArrayList<>();

        r.add(read("3a", "@Query(\"select b from Book b where lower(b.title) like ?1\") with Order<Book>",
                "[Alpha, Beta, Delta, Gamma, Zeta]", () -> titles(books.searchSorted("%a%", BY_TITLE))));
        r.add(read("3b", "@Query(\"select b from Book b where lower(b.title) like ?1 order by b.title\") returning Page<Book> (the count query)",
                "size=2 total=5", () -> page(books.searchPage("%a%", FIRST_PAGE))));
        r.add(read("3c", "control: alias-less JDQL \"from Book where lower(title) like ?1\" with Page + Order",
                "size=2 total=5", () -> page(books.searchPageJdql("%a%", FIRST_PAGE, BY_TITLE))));
        r.add(read("3d", "control: alias-less JDQL \"from Book where lower(title) like ?1 order by title\" returning Page",
                "size=2 total=5", () -> page(books.searchPageJdqlOrdered("%a%", FIRST_PAGE))));
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

    private static String page(Page<Book> page) {
        return "size=" + page.numberOfElements() + " total=" + page.totalElements() + " " + titles(page.content());
    }
}
