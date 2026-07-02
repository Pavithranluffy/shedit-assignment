package com.schedit.library.controller;

import com.schedit.library.dto.Dtos.*;
import com.schedit.library.model.*;
import com.schedit.library.repository.*;
import com.schedit.library.service.ClockService;
import com.schedit.library.service.LibraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LibraryController {

    private final LibraryService libraryService;
    private final ClockService clockService;
    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final WaitingListEntryRepository waitlistRepository;

    @Autowired
    public LibraryController(LibraryService libraryService,
                             ClockService clockService,
                             BookRepository bookRepository,
                             BookCopyRepository copyRepository,
                             MemberRepository memberRepository,
                             LoanRepository loanRepository,
                             WaitingListEntryRepository waitlistRepository) {
        this.libraryService = libraryService;
        this.clockService = clockService;
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.waitlistRepository = waitlistRepository;
    }

    // --- Clock Endpoints ---

    @GetMapping("/clock")
    public ResponseEntity<LocalDate> getClock() {
        return ResponseEntity.ok(clockService.getVirtualDate());
    }

    @PostMapping("/clock/advance")
    public ResponseEntity<LocalDate> advanceClock(@RequestBody AdvanceClockRequest request) {
        LocalDate newDate = clockService.advanceClock(request.days);
        // Automatically check and expire older reservations on clock advancement
        libraryService.triggerExpirations();
        return ResponseEntity.ok(newDate);
    }

    // --- Book Endpoints ---

    @GetMapping("/books")
    public ResponseEntity<List<BookDto>> getBooks() {
        List<Book> books = bookRepository.findAll();
        List<BookDto> dtos = books.stream().map(this::toBookDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/books")
    public ResponseEntity<Book> addBook(@RequestBody AddBookRequest request) {
        Book book = new Book(request.title, request.author, request.isbn, request.replacementCost);
        return ResponseEntity.ok(bookRepository.save(book));
    }

    @PostMapping("/books/{id}/copies")
    public ResponseEntity<BookCopy> addCopy(@PathVariable Long id, @RequestBody AddCopyRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        BookCopy copy = new BookCopy(book, BookCopy.CopyStatus.AVAILABLE, request.barcode);
        BookCopy saved = copyRepository.save(copy);
        // Process reservation queue in case a member is waiting
        libraryService.triggerExpirations();
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/copies/{id}/lost")
    public ResponseEntity<BookCopy> markLost(@PathVariable Long id) {
        return ResponseEntity.ok(libraryService.markCopyLost(id));
    }

    @PostMapping("/copies/{id}/damaged")
    public ResponseEntity<BookCopy> markDamaged(@PathVariable Long id) {
        return ResponseEntity.ok(libraryService.markCopyDamaged(id));
    }

    // --- Member Endpoints ---

    @GetMapping("/members")
    public ResponseEntity<List<MemberDto>> getMembers() {
        List<Member> members = memberRepository.findAll();
        List<MemberDto> dtos = members.stream().map(this::toMemberDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/members")
    public ResponseEntity<Member> addMember(@RequestBody Member member) {
        member.setBalance(BigDecimal.ZERO);
        return ResponseEntity.ok(memberRepository.save(member));
    }

    @PostMapping("/members/{id}/tier")
    public ResponseEntity<MemberDto> changeTier(@PathVariable Long id, @RequestBody DowngradeRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        member.setTier(Member.MembershipTier.valueOf(request.tier.toUpperCase()));
        memberRepository.save(member);
        return ResponseEntity.ok(toMemberDto(member));
    }

    @PostMapping("/members/{id}/settle")
    public ResponseEntity<MemberDto> settleFees(@PathVariable Long id, @RequestBody SettleRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        BigDecimal amount = request.amount;
        if (amount.compareTo(member.getBalance()) > 0) {
            amount = member.getBalance();
        }
        member.setBalance(member.getBalance().subtract(amount));
        memberRepository.save(member);
        return ResponseEntity.ok(toMemberDto(member));
    }

    // --- Lending Endpoints ---

    @GetMapping("/loans/active")
    public ResponseEntity<List<LoanDto>> getActiveLoans() {
        LocalDate today = clockService.getVirtualDate();
        List<Loan> loans = loanRepository.findAll().stream()
                .filter(l -> l.getReturnDate() == null)
                .collect(Collectors.toList());
        List<LoanDto> dtos = loans.stream().map(l -> toLoanDto(l, today)).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/members/{memberId}/loans")
    public ResponseEntity<List<LoanDto>> getMemberLoans(@PathVariable Long memberId) {
        LocalDate today = clockService.getVirtualDate();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<Loan> loans = loanRepository.findByMemberAndReturnDateIsNull(member);
        List<LoanDto> dtos = loans.stream().map(l -> toLoanDto(l, today)).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/borrow")
    public ResponseEntity<?> borrowBook(@RequestBody BorrowRequest request) {
        try {
            Loan loan = libraryService.borrowBook(request.memberId, request.bookId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/return/{copyId}")
    public ResponseEntity<?> returnBook(@PathVariable Long copyId) {
        try {
            Loan loan = libraryService.returnBookCopy(copyId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/renew/{loanId}")
    public ResponseEntity<?> renewLoan(@PathVariable Long loanId) {
        try {
            Loan loan = libraryService.renewLoan(loanId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- Waitlist Endpoints ---

    @GetMapping("/waitlist")
    public ResponseEntity<List<WaitingListEntryDto>> getWaitlist() {
        LocalDate today = clockService.getVirtualDate();
        List<WaitingListEntry> entries = waitlistRepository.findAll();
        List<WaitingListEntryDto> dtos = entries.stream().map(w -> toWaitlistDto(w, today)).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/members/{memberId}/waitlist")
    public ResponseEntity<List<WaitingListEntryDto>> getMemberWaitlist(@PathVariable Long memberId) {
        LocalDate today = clockService.getVirtualDate();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<WaitingListEntry> entries = waitlistRepository.findActiveByMember(member);
        List<WaitingListEntryDto> dtos = entries.stream().map(w -> toWaitlistDto(w, today)).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/waitlist/join")
    public ResponseEntity<?> joinWaitlist(@RequestBody WaitlistRequest request) {
        try {
            WaitingListEntry entry = libraryService.joinWaitlist(request.memberId, request.bookId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(toWaitlistDto(entry, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/waitlist/cancel/{entryId}")
    public ResponseEntity<?> cancelWaitlist(@PathVariable Long entryId) {
        try {
            WaitingListEntry entry = libraryService.cancelWaitlist(entryId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(toWaitlistDto(entry, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/waitlist/collect/{entryId}/member/{memberId}")
    public ResponseEntity<?> collectReservation(@PathVariable Long entryId, @PathVariable Long memberId) {
        try {
            Loan loan = libraryService.collectReservation(memberId, entryId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- DTO Converters ---

    private BookDto toBookDto(Book book) {
        BookDto dto = new BookDto();
        dto.id = book.getId();
        dto.title = book.getTitle();
        dto.author = book.getAuthor();
        dto.isbn = book.getIsbn();
        dto.replacementCost = book.getReplacementCost();

        List<BookCopy> copies = copyRepository.findByBook(book);
        dto.totalCopies = copies.size();
        dto.availableCopies = (int) copies.stream().filter(c -> c.getStatus() == BookCopy.CopyStatus.AVAILABLE).count();
        dto.reservedCopies = (int) copies.stream().filter(c -> c.getStatus() == BookCopy.CopyStatus.RESERVED).count();
        
        List<WaitingListEntry> activeWait = waitlistRepository.findActiveByBookOrderByCreatedAtAsc(book);
        dto.waitingListCount = (int) activeWait.stream().filter(w -> w.getStatus() == WaitingListEntry.WaitlistStatus.WAITING).count();

        dto.copies = copies.stream().map(c -> {
            CopyDto cd = new CopyDto();
            cd.id = c.getId();
            cd.barcode = c.getBarcode();
            cd.status = c.getStatus().name();
            return cd;
        }).collect(Collectors.toList());

        return dto;
    }

    private MemberDto toMemberDto(Member member) {
        MemberDto dto = new MemberDto();
        dto.id = member.getId();
        dto.name = member.getName();
        dto.email = member.getEmail();
        dto.tier = member.getTier().name();
        dto.balance = member.getBalance();
        dto.activeLoansCount = loanRepository.findByMemberAndReturnDateIsNull(member).size();
        return dto;
    }

    private LoanDto toLoanDto(Loan loan, LocalDate today) {
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
        dto.isOverdue = loan.getReturnDate() == null && today.isAfter(loan.getDueDate());
        return dto;
    }

    private WaitingListEntryDto toWaitlistDto(WaitingListEntry w, LocalDate today) {
        WaitingListEntryDto dto = new WaitingListEntryDto();
        dto.id = w.getId();
        dto.bookId = w.getBook().getId();
        dto.bookTitle = w.getBook().getTitle();
        dto.memberId = w.getMember().getId();
        dto.memberName = w.getMember().getName();
        dto.status = w.getStatus().name();
        dto.createdAt = w.getCreatedAt();
        dto.reservedAt = w.getReservedAt();
        if (w.getReservedCopy() != null) {
            dto.reservedCopyBarcode = w.getReservedCopy().getBarcode();
            dto.reservedCopyId = w.getReservedCopy().getId();
            if (w.getReservedAt() != null) {
                LocalDate expirationDate = w.getReservedAt().toLocalDate().plusDays(3);
                dto.daysRemaining = ChronoUnit.DAYS.between(today, expirationDate);
                if (dto.daysRemaining < 0) {
                    dto.daysRemaining = 0L;
                }
            }
        }
        return dto;
    }
}
