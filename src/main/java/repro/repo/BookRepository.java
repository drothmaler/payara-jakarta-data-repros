package repro.repo;

import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import repro.model.Author;
import repro.model.Book;

import java.util.Collection;
import java.util.List;

/** Every method belongs to one of the reproductions, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 1: derived count/delete with a parameter whose type is an entity named like the property
    List<Book> findByAuthor(Author author);          // control: works
    long countByAuthor(Author author);               // wrong entity in the generated query
    void deleteByAuthor(Author author);              // wrong entity in the generated query
    long countByAuthor_id(Long authorId);            // control: works

    // Issue 2: @Query with a static ORDER BY and no WHERE
    @Query("select b from Book b order by b.title")
    List<Book> allOrdered();

    @Query("select b from Book b where b.id > 0 order by b.title")
    List<Book> orderedWithWhere();                   // control: with a WHERE it works

    // Issue 3: @Query with an alias, a WHERE and a runtime Order / a Page
    @Query("select b from Book b where lower(b.title) like ?1")
    List<Book> searchSorted(String pattern, Order<Book> order);

    @Query("select b from Book b where lower(b.title) like ?1")
    Page<Book> searchPage(String pattern, PageRequest pageRequest);

    @Query("from Book where lower(title) like ?1")
    Page<Book> searchPageJdql(String pattern, PageRequest pageRequest, Order<Book> order); // control: alias-less JDQL works

    // Issue 4: derived query returning a Page when exactly one row matches
    Page<Book> findByTitleLike(String pattern, PageRequest pageRequest, Order<Book> order);

    // Issue 5: StartsWith / EndsWith / Contains with a Page return
    List<Book> findByTitleStartsWith(String prefix);                                    // control: List works
    Page<Book> findByTitleStartsWith(String prefix, PageRequest pageRequest, Order<Book> order);
    Page<Book> findByTitleEndsWith(String suffix, PageRequest pageRequest, Order<Book> order);
    Page<Book> findByTitleContains(String part, PageRequest pageRequest, Order<Book> order);

    // Issue 6: derived delete inside a caller's transaction
    void deleteByTitle(String title);
    void deleteByIdIn(Collection<Long> ids);

    @Query("delete from Book b where b.id in ?1")
    int deleteByIdsQuery(Collection<Long> ids);      // control: @Query delete works
}
