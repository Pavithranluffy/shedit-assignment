package com.schedit.library.service;

import com.schedit.library.model.*;
import com.schedit.library.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class LibraryService {

    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final WaitingListEntryRepository waitlistRepository;
    private final ClockService clockService;

    private static final BigDecimal DAILY_LATE_FEE = new BigDecimal("1.00");

    @Autowired
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

    @Transactional
    public Loan borrowBook(Long memberId, Long bookId) {
        LocalDate today = clockService.getVirtualDate();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));

        // Rule 5: Check outstanding late fees
        if (member.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot borrow new books with outstanding late fees. Please settle fees first.");
        }

        // Rule 1: Check borrowing limits
        List<Loan> activeLoans = loanRepository.findByMemberAndReturnDateIsNull(member);
        if (activeLoans.size() >= member.getTier().getMaxBooks()) {
            throw new IllegalStateException("Borrowing limit reached for membership tier: Max " + member.getTier().getMaxBooks() + " books.");
        }

        // Rule 3 (partial): Can't borrow a book you already have out
        if (loanRepository.existsActiveLoanForMemberAndBook(member, book)) {
            throw new IllegalStateException("You already have an active loan for this book.");
        }

        // Check if there are waitlists
        List<WaitingListEntry> activeWaitlist = waitlistRepository.findActiveByBookOrderByCreatedAtAsc(book);
        boolean hasQueue = !activeWaitlist.isEmpty();

        if (hasQueue) {
            // Check if this member is at the front of the queue and has a RESERVED copy
            Optional<WaitingListEntry> reservationOpt = activeWaitlist.stream()
                    .filter(w -> w.getMember().getId().equals(memberId) && w.getStatus() == WaitingListEntry.WaitlistStatus.RESERVED)
                    .findFirst();

            if (reservationOpt.isPresent()) {
                WaitingListEntry reservation = reservationOpt.get();
                // Check if expired
                if (isReservationExpired(reservation, today)) {
                    expireReservation(reservation);
                    throw new IllegalStateException("Your reservation has expired.");
                }
                return collectReservationInternal(member, reservation, today);
            } else {
                throw new IllegalStateException("This book is currently reserved or has a waiting list. You must join the waiting list.");
            }
        }

        // If no queue, look for an available copy
        List<BookCopy> availableCopies = copyRepository.findByBookAndStatus(book, BookCopy.CopyStatus.AVAILABLE);
        if (availableCopies.isEmpty()) {
            throw new IllegalStateException("No available copies. You must join the waiting list.");
        }

        BookCopy copyToBorrow = availableCopies.get(0);
        copyToBorrow.setStatus(BookCopy.CopyStatus.ON_LOAN);
        copyRepository.save(copyToBorrow);

        LocalDate dueDate = today.plusDays(member.getTier().getLoanPeriodDays());
        Loan loan = new Loan(copyToBorrow, member, today, dueDate);
        return loanRepository.save(loan);
    }

    @Transactional
    public Loan returnBookCopy(Long copyId) {
        LocalDate today = clockService.getVirtualDate();
        BookCopy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Copy not found"));

        Loan loan = loanRepository.findByBookCopyAndReturnDateIsNull(copy)
                .orElseThrow(() -> new IllegalStateException("Copy is not currently checked out"));

        loan.setReturnDate(today);

        // Calculate late fee
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

        // Return the copy back to Available
        copy.setStatus(BookCopy.CopyStatus.AVAILABLE);
        copyRepository.save(copy);

        // Process reservation queue
        processReservationQueue(copy.getBook(), today);

        return loan;
    }

    @Transactional
    public Loan renewLoan(Long loanId) {
        LocalDate today = clockService.getVirtualDate();
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getReturnDate() != null) {
            throw new IllegalStateException("Cannot renew a returned loan.");
        }

        Member member = loan.getMember();

        // Rule 5: Cannot renew with outstanding fees
        if (member.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot renew with outstanding late fees. Please settle fees first.");
        }

        // Rule 7 (Downgrade constraint): If member active loans exceed max tier allowed, block renewals
        List<Loan> activeLoans = loanRepository.findByMemberAndReturnDateIsNull(member);
        if (activeLoans.size() > member.getTier().getMaxBooks()) {
            throw new IllegalStateException("Cannot renew. You currently have " + activeLoans.size() 
                    + " books checked out, which exceeds your tier limit of " + member.getTier().getMaxBooks() + ".");
        }

        // Rule 2: Can renew unless someone else is waiting for that title
        // In this context, someone waiting means there is an entry with status WAITING
        List<WaitingListEntry> activeWaitlist = waitlistRepository.findActiveByBookOrderByCreatedAtAsc(loan.getBookCopy().getBook());
        boolean hasWaitingMember = activeWaitlist.stream()
                .anyMatch(w -> w.getStatus() == WaitingListEntry.WaitlistStatus.WAITING && !w.getMember().getId().equals(member.getId()));

        if (hasWaitingMember) {
            throw new IllegalStateException("Cannot renew. Another member is currently waiting for this book.");
        }

        // Extend due date
        LocalDate newDueDate = loan.getDueDate().plusDays(member.getTier().getLoanPeriodDays());
        loan.setDueDate(newDueDate);
        return loanRepository.save(loan);
    }

    @Transactional
    public WaitingListEntry joinWaitlist(Long memberId, Long bookId) {
        LocalDate today = clockService.getVirtualDate();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));

        // Rule 3: Only be on waiting list once for same book
        if (waitlistRepository.existsActiveEntryForMemberAndBook(member, book)) {
            throw new IllegalStateException("You are already on the waiting list for this book.");
        }

        // Rule 3: Can't be on waitlist for a book you already have out
        if (loanRepository.existsActiveLoanForMemberAndBook(member, book)) {
            throw new IllegalStateException("You already have an active loan for this book.");
        }

        LocalDateTime createdAt = today.atStartOfDay();
        WaitingListEntry entry = new WaitingListEntry(book, member, createdAt, WaitingListEntry.WaitlistStatus.WAITING);
        waitlistRepository.save(entry);

        // Process reservation queue (in case there's an available copy)
        processReservationQueue(book, today);

        return entry;
    }

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

        return entry;
    }

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

        // Rule 5: Outstanding late fees
        if (member.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot collect reservation with outstanding late fees. Please settle fees first.");
        }

        // Rule 1: Check borrowing limits
        List<Loan> activeLoans = loanRepository.findByMemberAndReturnDateIsNull(member);
        if (activeLoans.size() >= member.getTier().getMaxBooks()) {
            throw new IllegalStateException("Borrowing limit reached for membership tier: Max " + member.getTier().getMaxBooks() + " books.");
        }

        // Check expiration
        if (isReservationExpired(entry, today)) {
            expireReservation(entry);
            processReservationQueue(entry.getBook(), today);
            throw new IllegalStateException("This reservation has expired.");
        }

        return collectReservationInternal(member, entry, today);
    }

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

    @Transactional
    public BookCopy markCopyLost(Long copyId) {
        LocalDate today = clockService.getVirtualDate();
        BookCopy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Copy not found"));

        if (copy.getStatus() == BookCopy.CopyStatus.LOST) {
            return copy;
        }

        if (copy.getStatus() == BookCopy.CopyStatus.ON_LOAN) {
            // Find active loan and return with full replacement cost as fee
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
            // Find waitlist entry
            List<WaitingListEntry> reserved = waitlistRepository.findByBookAndStatusOrderByCreatedAtAsc(copy.getBook(), WaitingListEntry.WaitlistStatus.RESERVED);
            Optional<WaitingListEntry> entryOpt = reserved.stream()
                    .filter(w -> w.getReservedCopy() != null && w.getReservedCopy().getId().equals(copyId))
                    .findFirst();

            if (entryOpt.isPresent()) {
                WaitingListEntry entry = entryOpt.get();
                // Put back to waiting queue
                entry.setStatus(WaitingListEntry.WaitlistStatus.WAITING);
                entry.setReservedCopy(null);
                entry.setReservedAt(null);
                waitlistRepository.save(entry);
            }
        }

        copy.setStatus(BookCopy.CopyStatus.LOST);
        BookCopy saved = copyRepository.save(copy);

        // Try to reserve for the displaced user or others if other copies exist
        processReservationQueue(copy.getBook(), today);
        return saved;
    }

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
                    .filter(w -> w.getReservedCopy() != null && w.getReservedCopy().getId().equals(copyId))
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
        return saved;
    }

    // --- Helper Methods ---

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
        // 3 days to collect: if current date is > reservedDate + 3 days, it's expired
        return today.isAfter(reservedDate.plusDays(3));
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
}
