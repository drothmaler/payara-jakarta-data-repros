package repro.repo;

import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;
import repro.model.Book;

import java.util.List;

/** The methods of reproduction 5, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 5: StartsWith / EndsWith / Contains with a Page return
    List<Book> findByTitleStartsWith(String prefix);                                    // control: List works
    Page<Book> findByTitleStartsWith(String prefix, PageRequest pageRequest, Order<Book> order);
    Page<Book> findByTitleEndsWith(String suffix, PageRequest pageRequest, Order<Book> order);
    Page<Book> findByTitleContains(String part, PageRequest pageRequest, Order<Book> order);
}
