package com.schedit.library.repository;

import com.schedit.library.model.Book;
import com.schedit.library.model.Member;
import com.schedit.library.model.WaitingListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitingListEntryRepository extends JpaRepository<WaitingListEntry, Long> {
    List<WaitingListEntry> findByBookAndStatusOrderByCreatedAtAsc(Book book, WaitingListEntry.WaitlistStatus status);
    
    @Query("SELECT w FROM WaitingListEntry w WHERE w.book = :book AND (w.status = 'WAITING' OR w.status = 'RESERVED') ORDER BY w.createdAt ASC")
    List<WaitingListEntry> findActiveByBookOrderByCreatedAtAsc(@Param("book") Book book);

    @Query("SELECT w FROM WaitingListEntry w WHERE w.member = :member AND (w.status = 'WAITING' OR w.status = 'RESERVED')")
    List<WaitingListEntry> findActiveByMember(@Param("member") Member member);

    @Query("SELECT COUNT(w) > 0 FROM WaitingListEntry w WHERE w.member = :member AND w.book = :book AND (w.status = 'WAITING' OR w.status = 'RESERVED')")
    boolean existsActiveEntryForMemberAndBook(@Param("member") Member member, @Param("book") Book book);

    List<WaitingListEntry> findByStatus(WaitingListEntry.WaitlistStatus status);
}
