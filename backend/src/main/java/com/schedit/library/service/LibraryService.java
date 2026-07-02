package com.schedit.library.service;

import com.schedit.library.model.Book;
import com.schedit.library.model.BookCopy;
import com.schedit.library.model.Loan;
import com.schedit.library.model.Member;
import com.schedit.library.model.WaitingListEntry;
import com.schedit.library.repository.BookCopyRepository;
import com.schedit.library.repository.BookRepository;
import com.schedit.library.repository.LoanRepository;
import com.schedit.library.repository.MemberRepository;
import com.schedit.library.repository.WaitingListEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Central service containing the business rules for borrowing, returning, waitlisting, and inventory actions.
 */
@Service
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);
    private static final BigDecimal DAILY_LATE_FEE = new BigDecimal("1.00");
    private static final int RESERVATION_EXPIRY_DAYS = 3;

    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final WaitingListEntryRepository waitlistRepository;
    private final ClockService clockService;

    /**
     * Creates the service with the repositories it needs to enforce library rules.
     */
    public LibraryService(BookRepository bookRepository,
                          BookCopyRepository copyRepository,
                          MemberRepository memberRepository,
                          LoanRepository loanRepository,
                          WaitingListEntryRepository waitlistRepository,
                          ClockService clockService) {
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.waitlistRepository = waitlistRepository;
        this.clockService = clockService;
    }

    /**
     * Borrows a book for a member if the member is eligible and the inventory permits it.
     */
    @Transactional
    public Loan borrowBook(Long memberId, Long bookId) {
        LocalDate today = clockService.getVirtualDate();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));

        validateNoOutstandingFees(member, "borrow new books");
        validateBorrowingCapacity(member);

        if (loanRepository.existsActiveLoanForMemberAndBook(member, book)) {
            throw new IllegalStateException("You already have an active loan for this book.");
        }

        List<WaitingListEntry> activeWaitlist = waitlistRepository.findActiveByBookOrderByCreatedAtAsc(book);
        if (!activeWaitlist.isEmpty()) {
            Optional<WaitingListEntry> reservationOpt = activeWaitlist.stream()
                    .filter(waitingEntry -> waitingEntry.getMember().getId().equals(memberId)
                            && waitingEntry.getStatus() == WaitingListEntry.WaitlistStatus.RESERVED)
                    .findFirst();

            if (reservationOpt.isPresent()) {
                WaitingListEntry reservation = reservationOpt.get();
                if (isReservationExpired(reservation, today)) {
                    expireReservation(reservation);
                    throw new IllegalStateException("Your reservation has expired.");
                }
                log.info("Collecting reservation {} for member {}", reservation.getId(), memberId);
                return collectReservationInternal(member, reservation, today);
            }
            throw new IllegalStateException("This book is currently reserved or has a waiting list. You must join the waiting list.");
        }

        List<BookCopy> availableCopies = copyRepository.findByBookAndStatus(book, BookCopy.CopyStatus.AVAILABLE);
        if (availableCopies.isEmpty()) {
            throw new IllegalStateException("No available copies. You must join the waiting list.");
        }

        BookCopy copyToBorrow = availableCopies.get(0);
        copyToBorrow.setStatus(BookCopy.CopyStatus.ON_LOAN);
        copyRepository.save(copyToBorrow);

        LocalDate dueDate = today.plusDays(member.getTier().getLoanPeriodDays());
        Loan loan = new Loan(copyToBorrow, member, today, dueDate);
        Loan savedLoan = loanRepository.save(loan);
        log.info("Created loan {} for member {}", savedLoan.getId(), memberId);
        return savedLoan;
    }

    /**
     * Returns a checked-out copy and applies fees if the loan is overdue.
     */
    @Transactional
    public Loan returnBookCopy(Long copyId) {
        LocalDate today = clockService.getVirtualDate();
        BookCopy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Copy not found"));

        Loan loan = loanRepository.findByBookCopyAndReturnDateIsNull(copy)
                .orElseThrow(() -> new IllegalStateException("Copy is not currently checked out"));

        loan.setReturnDate(today);

        long daysLate = ChronoUnit.DAYS.between(loan.getDueDate(), today);
        if (daysLate > 0) {
            BigDecimal charge = DAILY_LATE_FEE.multiply(new BigDecimal(daysLate));
            BigDecimal cap = loan.getBookCopy().getBook().getReplacementCost();
            if (charge.compareTo(cap) > 0) {
                charge = cap;
            }
            loan.setFeeCharged(charge);
            Member member = loan.getMember();
            member.setBalance(member.getBalance().add(charge));
            memberRepository.save(member);
        }

        loanRepository.save(loan);
        copy.setStatus(BookCopy.CopyStatus.AVAILABLE);
        copyRepository.save(copy);
        processReservationQueue(copy.getBook(), today);

        log.info("Returned copy {} and processed queue for book {}", copyId, copy.getBook().getId());
        return loan;
    }

    /**
     * Renews an active loan if the member remains eligible under the current policy.
     */
    @Transactional
    public Loan renewLoan(Long loanId) {
        LocalDate today = clockService.getVirtualDate();
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getReturnDate() != null) {
            throw new IllegalStateException("Cannot renew a returned loan.");
        }

        Member member = loan.getMember();
        validateNoOutstandingFees(member, "renew loans");
        validateRenewalCapacity(member);

        List<WaitingListEntry> activeWaitlist = waitlistRepository.findActiveByBookOrderByCreatedAtAsc(loan.getBookCopy().getBook());
        boolean hasWaitingMember = activeWaitlist.stream()
                .anyMatch(waitingEntry -> waitingEntry.getStatus() == WaitingListEntry.WaitlistStatus.WAITING
                        && !waitingEntry.getMember().getId().equals(member.getId()));

        if (hasWaitingMember) {
            throw new IllegalStateException("Cannot renew. Another member is currently waiting for this book.");
        }

        LocalDate newDueDate = loan.getDueDate().plusDays(member.getTier().getLoanPeriodDays());
        loan.setDueDate(newDueDate);
        Loan savedLoan = loanRepository.save(loan);
        log.info("Renewed loan {} for member {} on {}", loanId, member.getId(), today);
        return savedLoan;
    }

    /**
     * Adds a member to the waiting list for a book.
     */
    @Transactional
    public WaitingListEntry joinWaitlist(Long memberId, Long bookId) {
        LocalDate today = clockService.getVirtualDate();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));

        if (waitlistRepository.existsActiveEntryForMemberAndBook(member, book)) {
            throw new IllegalStateException("You are already on the waiting list for this book.");
        }

        if (loanRepository.existsActiveLoanForMemberAndBook(member, book)) {
            throw new IllegalStateException("You already have an active loan for this book.");
        }

        LocalDateTime createdAt = today.atStartOfDay();
        WaitingListEntry entry = new WaitingListEntry(book, member, createdAt, WaitingListEntry.WaitlistStatus.WAITING);
        WaitingListEntry savedEntry = waitlistRepository.save(entry);
        processReservationQueue(book, today);
        log.info("Member {} joined waitlist {}", memberId, savedEntry.getId());
        return savedEntry;
    }

    /**
     * Cancels an active waitlist entry and returns any reserved copy to the pool when appropriate.
     */
    @Transactional
    public WaitingListEntry cancelWaitlist(Long entryId) {
        LocalDate today = clockService.getVirtualDate();
        WaitingListEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found"));

        if (entry.getStatus() != WaitingListEntry.WaitlistStatus.WAITING && entry.getStatus() != WaitingListEntry.WaitlistStatus.RESERVED) {
            throw new IllegalStateException("Cannot cancel an inactive waitlist entry.");
        }

        boolean wasReserved = entry.getStatus() == WaitingListEntry.WaitlistStatus.RESERVED;
        BookCopy copy = entry.getReservedCopy();

        entry.setStatus(WaitingListEntry.WaitlistStatus.CANCELLED);
        entry.setReservedCopy(null);
        waitlistRepository.save(entry);

        if (wasReserved && copy != null) {
            copy.setStatus(BookCopy.CopyStatus.AVAILABLE);
            copyRepository.save(copy);
            processReservationQueue(copy.getBook(), today);
        }

        log.info("Cancelled waitlist entry {}", entryId);
        return entry;
    }

    /**
     * Collects a reservation for a member and creates an active loan.
     */
    @Transactional
    public Loan collectReservation(Long memberId, Long entryId) {
        LocalDate today = clockService.getVirtualDate();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        WaitingListEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found"));

        if (!entry.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("This reservation does not belong to the selected member");
        }

        if (entry.getStatus() != WaitingListEntry.WaitlistStatus.RESERVED) {
            throw new IllegalStateException("This entry is not in reserved status");
        }

        validateNoOutstandingFees(member, "collect reservations");
        validateBorrowingCapacity(member);

        if (isReservationExpired(entry, today)) {
            expireReservation(entry);
            processReservationQueue(entry.getBook(), today);
            throw new IllegalStateException("This reservation has expired.");
        }

        return collectReservationInternal(member, entry, today);
    }

    /**
     * Expires any reservation that has exceeded its collection window.
     */
    @Transactional
    public void triggerExpirations() {
        LocalDate today = clockService.getVirtualDate();
        List<WaitingListEntry> reservedEntries = waitlistRepository.findByStatus(WaitingListEntry.WaitlistStatus.RESERVED);
        for (WaitingListEntry entry : reservedEntries) {
            if (isReservationExpired(entry, today)) {
                expireReservation(entry);
                processReservationQueue(entry.getBook(), today);
            }
        }
    }

    /**
     * Marks a copy as lost and applies the replacement cost as a fee if the item was on loan.
     */
    @Transactional
    public BookCopy markCopyLost(Long copyId) {
        LocalDate today = clockService.getVirtualDate();
        BookCopy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Copy not found"));

        if (copy.getStatus() == BookCopy.CopyStatus.LOST) {
            return copy;
        }

        if (copy.getStatus() == BookCopy.CopyStatus.ON_LOAN) {
            Loan loan = loanRepository.findByBookCopyAndReturnDateIsNull(copy)
                    .orElseThrow(() -> new IllegalStateException("Copy state is ON_LOAN but no active loan found"));
            loan.setReturnDate(today);
            BigDecimal cost = copy.getBook().getReplacementCost();
            loan.setFeeCharged(cost);
            loanRepository.save(loan);

            Member member = loan.getMember();
            member.setBalance(member.getBalance().add(cost));
            memberRepository.save(member);
        } else if (copy.getStatus() == BookCopy.CopyStatus.RESERVED) {
            List<WaitingListEntry> reserved = waitlistRepository.findByBookAndStatusOrderByCreatedAtAsc(copy.getBook(), WaitingListEntry.WaitlistStatus.RESERVED);
            Optional<WaitingListEntry> entryOpt = reserved.stream()
                    .filter(waitingEntry -> waitingEntry.getReservedCopy() != null && waitingEntry.getReservedCopy().getId().equals(copyId))
                    .findFirst();

            if (entryOpt.isPresent()) {
                WaitingListEntry entry = entryOpt.get();
                entry.setStatus(WaitingListEntry.WaitlistStatus.WAITING);
                entry.setReservedCopy(null);
                entry.setReservedAt(null);
                waitlistRepository.save(entry);
            }
        }

        copy.setStatus(BookCopy.CopyStatus.LOST);
        BookCopy saved = copyRepository.save(copy);
        processReservationQueue(copy.getBook(), today);
        log.info("Marked copy {} as lost", copyId);
        return saved;
    }

    /**
     * Marks a copy as damaged and re-queues any affected reservations.
     */
    @Transactional
    public BookCopy markCopyDamaged(Long copyId) {
        LocalDate today = clockService.getVirtualDate();
        BookCopy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Copy not found"));

        if (copy.getStatus() == BookCopy.CopyStatus.DAMAGED) {
            return copy;
        }

        if (copy.getStatus() == BookCopy.CopyStatus.RESERVED) {
            List<WaitingListEntry> reserved = waitlistRepository.findByBookAndStatusOrderByCreatedAtAsc(copy.getBook(), WaitingListEntry.WaitlistStatus.RESERVED);
            Optional<WaitingListEntry> entryOpt = reserved.stream()
                    .filter(waitingEntry -> waitingEntry.getReservedCopy() != null && waitingEntry.getReservedCopy().getId().equals(copyId))
                    .findFirst();

            if (entryOpt.isPresent()) {
                WaitingListEntry entry = entryOpt.get();
                entry.setStatus(WaitingListEntry.WaitlistStatus.WAITING);
                entry.setReservedCopy(null);
                entry.setReservedAt(null);
                waitlistRepository.save(entry);
            }
        }

        copy.setStatus(BookCopy.CopyStatus.DAMAGED);
        BookCopy saved = copyRepository.save(copy);
        processReservationQueue(copy.getBook(), today);
        log.info("Marked copy {} as damaged", copyId);
        return saved;
    }

    private Loan collectReservationInternal(Member member, WaitingListEntry reservation, LocalDate today) {
        BookCopy copy = reservation.getReservedCopy();
        copy.setStatus(BookCopy.CopyStatus.ON_LOAN);
        copyRepository.save(copy);

        LocalDate dueDate = today.plusDays(member.getTier().getLoanPeriodDays());
        Loan loan = new Loan(copy, member, today, dueDate);

        reservation.setStatus(WaitingListEntry.WaitlistStatus.FULFILLED);
        waitlistRepository.save(reservation);

        return loanRepository.save(loan);
    }

    private void processReservationQueue(Book book, LocalDate today) {
        List<BookCopy> availableCopies = copyRepository.findByBookAndStatus(book, BookCopy.CopyStatus.AVAILABLE);
        List<WaitingListEntry> waitingEntries = waitlistRepository.findByBookAndStatusOrderByCreatedAtAsc(book, WaitingListEntry.WaitlistStatus.WAITING);

        int copiesToReserve = Math.min(availableCopies.size(), waitingEntries.size());
        for (int i = 0; i < copiesToReserve; i++) {
            BookCopy copy = availableCopies.get(i);
            WaitingListEntry entry = waitingEntries.get(i);

            copy.setStatus(BookCopy.CopyStatus.RESERVED);
            copyRepository.save(copy);

            entry.setStatus(WaitingListEntry.WaitlistStatus.RESERVED);
            entry.setReservedCopy(copy);
            entry.setReservedAt(today.atStartOfDay());
            waitlistRepository.save(entry);
        }
    }

    private boolean isReservationExpired(WaitingListEntry entry, LocalDate today) {
        if (entry.getReservedAt() == null) {
            return false;
        }
        LocalDate reservedDate = entry.getReservedAt().toLocalDate();
        return today.isAfter(reservedDate.plusDays(RESERVATION_EXPIRY_DAYS));
    }

    private void expireReservation(WaitingListEntry entry) {
        entry.setStatus(WaitingListEntry.WaitlistStatus.EXPIRED);
        BookCopy copy = entry.getReservedCopy();
        entry.setReservedCopy(null);
        waitlistRepository.save(entry);

        if (copy != null) {
            copy.setStatus(BookCopy.CopyStatus.AVAILABLE);
            copyRepository.save(copy);
        }
    }

    private void validateNoOutstandingFees(Member member, String action) {
        if (member.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot " + action + " with outstanding late fees. Please settle fees first.");
        }
    }

    private void validateBorrowingCapacity(Member member) {
        List<Loan> activeLoans = loanRepository.findByMemberAndReturnDateIsNull(member);
        if (activeLoans.size() >= member.getTier().getMaxBooks()) {
            throw new IllegalStateException("Borrowing limit reached for membership tier: Max " + member.getTier().getMaxBooks() + " books.");
        }
    }

    private void validateRenewalCapacity(Member member) {
        List<Loan> activeLoans = loanRepository.findByMemberAndReturnDateIsNull(member);
        if (activeLoans.size() > member.getTier().getMaxBooks()) {
            throw new IllegalStateException("Cannot renew. You currently have " + activeLoans.size()
                    + " books checked out, which exceeds your tier limit of " + member.getTier().getMaxBooks() + ".");
        }
    }
}

