package repro.repo;

import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import repro.model.Book;

import java.util.List;

/** The methods of reproduction 3, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 3: @Query with an alias, a WHERE and a runtime Order / a Page
    @Query("select b from Book b where lower(b.title) like ?1")
    List<Book> searchSorted(String pattern, Order<Book> order);

    @Query("select b from Book b where lower(b.title) like ?1 order by b.title")
    Page<Book> searchPage(String pattern, PageRequest pageRequest);                       // the count query drops the alias

    @Query("from Book where lower(title) like ?1")
    Page<Book> searchPageJdql(String pattern, PageRequest pageRequest, Order<Book> order); // control: alias-less JDQL works

    @Query("from Book where lower(title) like ?1 order by title")
    Page<Book> searchPageJdqlOrdered(String pattern, PageRequest pageRequest);            // control: alias-less JDQL works
}
