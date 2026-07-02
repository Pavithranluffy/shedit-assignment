package com.schedit.library.repository;

import com.schedit.library.model.Book;
import com.schedit.library.model.BookCopy;
import com.schedit.library.model.Loan;
import com.schedit.library.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByMemberAndReturnDateIsNull(Member member);
    
    Optional<Loan> findByBookCopyAndReturnDateIsNull(BookCopy bookCopy);
    
    @Query("SELECT l FROM Loan l WHERE l.bookCopy.book = :book AND l.returnDate IS NULL")
    List<Loan> findActiveLoansByBook(@Param("book") Book book);

    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.member = :member AND l.bookCopy.book = :book AND l.returnDate IS NULL")
    boolean existsActiveLoanForMemberAndBook(@Param("member") Member member, @Param("book") Book book);
}
