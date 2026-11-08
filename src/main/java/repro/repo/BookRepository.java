package repro.repo;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import repro.model.Book;

import java.util.Collection;
import java.util.Set;

/** The methods of reproduction 6, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 6: derived delete inside a caller's transaction
    void deleteByTitle(String title);
    void deleteByIdIn(Set<Long> ids);                // In takes a Set (addendum 2.7)

    @Query("delete from Book b where b.id in ?1")
    int deleteByIdsQuery(Collection<Long> ids);      // control: @Query delete works
}
