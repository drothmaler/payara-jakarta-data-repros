package repro.repo;

import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;
import repro.model.Book;

/** The methods of reproduction 4, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 4: derived query returning a Page when exactly one row matches
    Page<Book> findByTitleLike(String pattern, PageRequest pageRequest, Order<Book> order);
}
