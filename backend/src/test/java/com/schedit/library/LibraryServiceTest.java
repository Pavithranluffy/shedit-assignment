package com.schedit.library;

import com.schedit.library.model.*;
import com.schedit.library.repository.*;
import com.schedit.library.service.ClockService;
import com.schedit.library.service.LibraryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LibraryServiceTest {

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private ClockService clockService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCopyRepository copyRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private WaitingListEntryRepository waitlistRepository;

    @Autowired
    private SystemClockStateRepository clockStateRepository;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        waitlistRepository.deleteAll();
        copyRepository.deleteAll();
        bookRepository.deleteAll();
        memberRepository.deleteAll();
        clockStateRepository.deleteAll();
    }

    @Test
    void waitlistEntryShouldUseTheLibraryClockForItsCreationDate() {
        Member member = memberRepository.save(new Member("Ava", "ava@example.com", Member.MembershipTier.REGULAR));
        Book book = bookRepository.save(new Book("The Hobbit", "J.R.R. Tolkien", "1234", new BigDecimal("15.00")));

        clockStateRepository.save(new com.schedit.library.model.SystemClockState(LocalDate.of(2025, 1, 10)));

        WaitingListEntry entry = libraryService.joinWaitlist(member.getId(), book.getId());

        assertEquals(LocalDate.of(2025, 1, 10), entry.getCreatedAt().toLocalDate());
    }

    @Test
    void memberWithOutstandingFeesCannotBorrowNewBooks() {
        Member member = memberRepository.save(new Member("Ben", "ben@example.com", Member.MembershipTier.REGULAR));
        member.setBalance(new BigDecimal("2.50"));
        memberRepository.save(member);

        Book book = bookRepository.save(new Book("Dune", "Frank Herbert", "5678", new BigDecimal("20.00")));
        BookCopy copy = copyRepository.save(new BookCopy(book, BookCopy.CopyStatus.AVAILABLE, "ABC-1"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> libraryService.borrowBook(member.getId(), book.getId()));
        assertTrue(ex.getMessage().contains("outstanding late fees"));
        assertEquals(BookCopy.CopyStatus.AVAILABLE, copyRepository.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void reservationExpiresAfterThreeDaysAndNextWaitlistedMemberCanBeReserved() {
        Member first = memberRepository.save(new Member("Cara", "cara@example.com", Member.MembershipTier.REGULAR));
        Member second = memberRepository.save(new Member("Drew", "drew@example.com", Member.MembershipTier.REGULAR));
        Book book = bookRepository.save(new Book("1984", "George Orwell", "9999", new BigDecimal("18.00")));
        BookCopy copy = copyRepository.save(new BookCopy(book, BookCopy.CopyStatus.AVAILABLE, "COPY-1"));

        clockStateRepository.save(new com.schedit.library.model.SystemClockState(LocalDate.of(2025, 2, 1)));
        WaitingListEntry firstEntry = libraryService.joinWaitlist(first.getId(), book.getId());
        WaitingListEntry secondEntry = libraryService.joinWaitlist(second.getId(), book.getId());

        assertEquals(WaitingListEntry.WaitlistStatus.RESERVED, firstEntry.getStatus());
        assertEquals(WaitingListEntry.WaitlistStatus.WAITING, secondEntry.getStatus());

        clockService.advanceClock(4);
        libraryService.triggerExpirations();

        WaitingListEntry updatedFirst = waitlistRepository.findById(firstEntry.getId()).orElseThrow();
        WaitingListEntry updatedSecond = waitlistRepository.findById(secondEntry.getId()).orElseThrow();

        assertEquals(WaitingListEntry.WaitlistStatus.EXPIRED, updatedFirst.getStatus());
        assertEquals(WaitingListEntry.WaitlistStatus.RESERVED, updatedSecond.getStatus());
    }
}
