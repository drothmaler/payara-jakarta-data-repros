package repro.repo;

import jakarta.data.repository.By;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import repro.model.Book;

/** The methods of reproduction 8, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 8: deletes clear the caller's persistence context (deleteByTitle from issue 6, and these)
    @Delete
    void removeByTitle(@By("title") String title);

    @Query("delete from Book where title = ?1")
    int deleteTitled(String title);                  // JDQL delete

    // from issue 6
    void deleteByTitle(String title);
}
