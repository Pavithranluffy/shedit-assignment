package com.schedit.library.repository;

import com.schedit.library.model.Book;
import com.schedit.library.model.BookCopy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    List<BookCopy> findByBook(Book book);
    List<BookCopy> findByBookAndStatus(Book book, BookCopy.CopyStatus status);
    long countByBookAndStatus(Book book, BookCopy.CopyStatus status);
    Optional<BookCopy> findByBarcode(String barcode);
}
