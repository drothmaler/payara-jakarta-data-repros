package repro.repo;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;
import repro.model.Author;
import repro.model.Book;

import java.util.List;

/** The methods of reproduction 1, see README.md. */
@Repository
public interface BookRepository extends CrudRepository<Book, Long> {

    // Issue 1: derived count/delete with a parameter whose type is an entity named like the property
    List<Book> findByAuthor(Author author);          // control: works
    long countByAuthor(Author author);               // wrong entity in the generated query
    void deleteByAuthor(Author author);              // wrong entity in the generated query
    long countByAuthor_id(Long authorId);            // control: works
}
