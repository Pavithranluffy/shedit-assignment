package com.schedit.library.service;

import com.schedit.library.dto.Dtos.BookDto;
import com.schedit.library.dto.Dtos.CopyDto;
import com.schedit.library.dto.Dtos.LoanDto;
import com.schedit.library.dto.Dtos.MemberDto;
import com.schedit.library.dto.Dtos.WaitingListEntryDto;
import com.schedit.library.model.Book;
import com.schedit.library.model.BookCopy;
import com.schedit.library.model.Loan;
import com.schedit.library.model.Member;
import com.schedit.library.model.WaitingListEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class LibraryDtoMapper {

    /**
     * Calculates the number of days remaining before a reservation expires.
     */
    public long calculateDaysRemaining(LocalDate today, WaitingListEntry entry) {
        if (entry.getReservedAt() == null) {
            return 0L;
        }
        LocalDate expirationDate = entry.getReservedAt().toLocalDate().plusDays(3);
        long daysRemaining = ChronoUnit.DAYS.between(today, expirationDate);
        return Math.max(daysRemaining, 0L);
    }

    /**
     * Determines whether a loan has passed its due date.
     */
    public boolean isOverdue(LocalDate today, Loan loan) {
        return loan.getReturnDate() == null && today.isAfter(loan.getDueDate());
    }

    /**
     * Maps a book entity into the API DTO used by the frontend.
     */
    public BookDto toBookDto(Book book, List<BookCopy> copies, List<WaitingListEntry> activeWaitlist) {
        BookDto dto = new BookDto();
        dto.id = book.getId();
        dto.title = book.getTitle();
        dto.author = book.getAuthor();
        dto.isbn = book.getIsbn();
        dto.replacementCost = book.getReplacementCost();
        dto.totalCopies = copies.size();
        dto.availableCopies = (int) copies.stream().filter(c -> c.getStatus() == BookCopy.CopyStatus.AVAILABLE).count();
        dto.reservedCopies = (int) copies.stream().filter(c -> c.getStatus() == BookCopy.CopyStatus.RESERVED).count();
        dto.waitingListCount = (int) activeWaitlist.stream().filter(w -> w.getStatus() == WaitingListEntry.WaitlistStatus.WAITING).count();
        dto.copies = copies.stream().map(this::toCopyDto).collect(Collectors.toList());
        return dto;
    }

    /**
     * Maps a copy entity into the API DTO used by the frontend.
     */
    public CopyDto toCopyDto(BookCopy copy) {
        CopyDto dto = new CopyDto();
        dto.id = copy.getId();
        dto.barcode = copy.getBarcode();
        dto.status = copy.getStatus().name();
        return dto;
    }

    /**
     * Maps a member entity into the API DTO used by the frontend.
     */
    public MemberDto toMemberDto(Member member, int activeLoansCount) {
        MemberDto dto = new MemberDto();
        dto.id = member.getId();
        dto.name = member.getName();
        dto.email = member.getEmail();
        dto.tier = member.getTier().name();
        dto.balance = member.getBalance();
        dto.activeLoansCount = activeLoansCount;
        return dto;
    }

    /**
     * Maps a loan entity into the API DTO used by the frontend.
     */
    public LoanDto toLoanDto(Loan loan, LocalDate today) {
        LoanDto dto = new LoanDto();
        dto.id = loan.getId();
        dto.bookTitle = loan.getBookCopy().getBook().getTitle();
        dto.copyBarcode = loan.getBookCopy().getBarcode();
        dto.copyId = loan.getBookCopy().getId();
        dto.memberId = loan.getMember().getId();
        dto.memberName = loan.getMember().getName();
        dto.borrowDate = loan.getBorrowDate();
        dto.dueDate = loan.getDueDate();
        dto.returnDate = loan.getReturnDate();
        dto.feeCharged = loan.getFeeCharged();
        dto.isOverdue = isOverdue(today, loan);
        return dto;
    }

    /**
     * Maps a waitlist entry entity into the API DTO used by the frontend.
     */
    public WaitingListEntryDto toWaitlistDto(WaitingListEntry entry, LocalDate today) {
        WaitingListEntryDto dto = new WaitingListEntryDto();
        dto.id = entry.getId();
        dto.bookId = entry.getBook().getId();
        dto.bookTitle = entry.getBook().getTitle();
        dto.memberId = entry.getMember().getId();
        dto.memberName = entry.getMember().getName();
        dto.status = entry.getStatus().name();
        dto.createdAt = entry.getCreatedAt();
        dto.reservedAt = entry.getReservedAt();
        if (entry.getReservedCopy() != null) {
            dto.reservedCopyBarcode = entry.getReservedCopy().getBarcode();
            dto.reservedCopyId = entry.getReservedCopy().getId();
            dto.daysRemaining = calculateDaysRemaining(today, entry);
        }
        return dto;
    }
}
