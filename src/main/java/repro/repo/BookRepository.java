package repro.repo;

import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import repro.model.Book;

import java.util.List;

/** The methods of reproduction 7, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 7: derived Like returning a List wraps the caller's pattern in %...%
    List<Book> findByTitleLike(String pattern);
    List<Book> findByTitleIgnoreCaseLike(String pattern);

    @Query("where title like ?1")
    List<Book> jdqlTitleLike(String pattern);        // control: JDQL like uses the pattern as given

    // from issue 4
    Page<Book> findByTitleLike(String pattern, PageRequest pageRequest, Order<Book> order);
}
