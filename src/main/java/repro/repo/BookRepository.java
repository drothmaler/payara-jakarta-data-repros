package repro.repo;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import repro.model.Book;

import java.util.List;

/** The methods of reproduction 2, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 2: @Query with a static ORDER BY and no WHERE
    @Query("select b from Book b order by b.title")
    List<Book> allOrdered();

    @Query("select b from Book b where b.id > 0 order by b.title")
    List<Book> orderedWithWhere();                   // control: with a WHERE it works

    @Query("from Book order by title")
    List<Book> jdqlFromOrdered();                    // the same in pure JDQL: fails too

    @Query("select title from Book order by title")
    List<String> jdqlTitlesOrdered();                // pure JDQL projection: fails too

    @Query("order by title")
    List<Book> jdqlOrderOnly();                      // control: JDQL without FROM works
}
