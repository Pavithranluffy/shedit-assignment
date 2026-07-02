package com.schedit.library.controller;

import com.schedit.library.dto.Dtos.AddBookRequest;
import com.schedit.library.dto.Dtos.AddCopyRequest;
import com.schedit.library.dto.Dtos.AdvanceClockRequest;
import com.schedit.library.dto.Dtos.BookDto;
import com.schedit.library.dto.Dtos.BorrowRequest;
import com.schedit.library.dto.Dtos.DowngradeRequest;
import com.schedit.library.dto.Dtos.LoanDto;
import com.schedit.library.dto.Dtos.MemberDto;
import com.schedit.library.dto.Dtos.SettleRequest;
import com.schedit.library.dto.Dtos.WaitingListEntryDto;
import com.schedit.library.dto.Dtos.WaitlistRequest;
import com.schedit.library.model.Book;
import com.schedit.library.model.BookCopy;
import com.schedit.library.model.Loan;
import com.schedit.library.model.Member;
import com.schedit.library.model.WaitingListEntry;
import com.schedit.library.repository.BookCopyRepository;
import com.schedit.library.repository.BookRepository;
import com.schedit.library.repository.MemberRepository;
import com.schedit.library.service.ClockService;
import com.schedit.library.service.LibraryDtoMapper;
import com.schedit.library.service.LibraryQueryService;
import com.schedit.library.service.LibraryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * REST controller exposing the library operations to the frontend.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LibraryController {

    private final LibraryService libraryService;
    private final ClockService clockService;
    private final LibraryQueryService queryService;
    private final LibraryDtoMapper mapper;
    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final MemberRepository memberRepository;

    /**
     * Creates a controller with the application services and repositories it orchestrates.
     */
    public LibraryController(LibraryService libraryService,
                             ClockService clockService,
                             LibraryQueryService queryService,
                             LibraryDtoMapper mapper,
                             BookRepository bookRepository,
                             BookCopyRepository copyRepository,
                             MemberRepository memberRepository) {
        this.libraryService = libraryService;
        this.clockService = clockService;
        this.queryService = queryService;
        this.mapper = mapper;
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Returns the current virtual date used by the library rules.
     */
    @GetMapping("/clock")
    public ResponseEntity<LocalDate> getClock() {
        return ResponseEntity.ok(clockService.getVirtualDate());
    }

    /**
     * Advances the virtual clock and expires any reservations that have passed their window.
     */
    @PostMapping("/clock/advance")
    public ResponseEntity<LocalDate> advanceClock(@RequestBody AdvanceClockRequest request) {
        LocalDate newDate = clockService.advanceClock(request.days);
        libraryService.triggerExpirations();
        return ResponseEntity.ok(newDate);
    }

    /**
     * Returns all books and their availability summary.
     */
    @GetMapping("/books")
    public ResponseEntity<List<BookDto>> getBooks() {
        return ResponseEntity.ok(queryService.getBooks());
    }

    /**
     * Creates a new book in the catalog.
     */
    @PostMapping("/books")
    public ResponseEntity<Book> addBook(@RequestBody AddBookRequest request) {
        Book book = new Book(request.title, request.author, request.isbn, request.replacementCost);
        return ResponseEntity.ok(bookRepository.save(book));
    }

    /**
     * Adds a physical copy to an existing book.
     */
    @PostMapping("/books/{id}/copies")
    public ResponseEntity<BookCopy> addCopy(@PathVariable Long id, @RequestBody AddCopyRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        BookCopy copy = new BookCopy(book, BookCopy.CopyStatus.AVAILABLE, request.barcode);
        BookCopy saved = copyRepository.save(copy);
        libraryService.triggerExpirations();
        return ResponseEntity.ok(saved);
    }

    /**
     * Marks a copy as lost and applies the associated fee policy.
     */
    @PostMapping("/copies/{id}/lost")
    public ResponseEntity<BookCopy> markLost(@PathVariable Long id) {
        return ResponseEntity.ok(libraryService.markCopyLost(id));
    }

    /**
     * Marks a copy as damaged and re-queues any affected reservations.
     */
    @PostMapping("/copies/{id}/damaged")
    public ResponseEntity<BookCopy> markDamaged(@PathVariable Long id) {
        return ResponseEntity.ok(libraryService.markCopyDamaged(id));
    }

    /**
     * Returns all registered members.
     */
    @GetMapping("/members")
    public ResponseEntity<List<MemberDto>> getMembers() {
        return ResponseEntity.ok(queryService.getMembers());
    }

    /**
     * Creates a new library member.
     */
    @PostMapping("/members")
    public ResponseEntity<Member> addMember(@RequestBody Member member) {
        member.setBalance(BigDecimal.ZERO);
        return ResponseEntity.ok(memberRepository.save(member));
    }

    /**
     * Updates the membership tier for a member.
     */
    @PostMapping("/members/{id}/tier")
    public ResponseEntity<MemberDto> changeTier(@PathVariable Long id, @RequestBody DowngradeRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        member.setTier(Member.MembershipTier.valueOf(request.tier.toUpperCase()));
        memberRepository.save(member);
        return ResponseEntity.ok(queryService.getMembers().stream()
                .filter(dto -> dto.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member not found")));
    }

    /**
     * Settles a portion of a member's outstanding balance.
     */
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
        return ResponseEntity.ok(queryService.getMembers().stream()
                .filter(dto -> dto.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member not found")));
    }

    /**
     * Returns all currently active loans.
     */
    @GetMapping("/loans/active")
    public ResponseEntity<List<LoanDto>> getActiveLoans() {
        LocalDate today = clockService.getVirtualDate();
        return ResponseEntity.ok(queryService.getActiveLoans(today));
    }

    /**
     * Returns the active loans for a specific member.
     */
    @GetMapping("/members/{memberId}/loans")
    public ResponseEntity<List<LoanDto>> getMemberLoans(@PathVariable Long memberId) {
        LocalDate today = clockService.getVirtualDate();
        return ResponseEntity.ok(queryService.getMemberLoans(memberId, today));
    }

    /**
     * Borrows a book for the selected member if the business rules allow it.
     */
    @PostMapping("/borrow")
    public ResponseEntity<?> borrowBook(@RequestBody BorrowRequest request) {
        try {
            Loan loan = libraryService.borrowBook(request.memberId, request.bookId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(mapper.toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Returns a checked-out copy to the catalog.
     */
    @PostMapping("/return/{copyId}")
    public ResponseEntity<?> returnBook(@PathVariable Long copyId) {
        try {
            Loan loan = libraryService.returnBookCopy(copyId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(mapper.toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Renews an active loan if the member is still eligible.
     */
    @PostMapping("/renew/{loanId}")
    public ResponseEntity<?> renewLoan(@PathVariable Long loanId) {
        try {
            Loan loan = libraryService.renewLoan(loanId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(mapper.toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Returns the current waitlist across the library.
     */
    @GetMapping("/waitlist")
    public ResponseEntity<List<WaitingListEntryDto>> getWaitlist() {
        LocalDate today = clockService.getVirtualDate();
        return ResponseEntity.ok(queryService.getWaitlist(today));
    }

    /**
     * Returns the waitlist entries created by a specific member.
     */
    @GetMapping("/members/{memberId}/waitlist")
    public ResponseEntity<List<WaitingListEntryDto>> getMemberWaitlist(@PathVariable Long memberId) {
        LocalDate today = clockService.getVirtualDate();
        return ResponseEntity.ok(queryService.getMemberWaitlist(memberId, today));
    }

    /**
     * Adds a member to the waiting list for a book.
     */
    @PostMapping("/waitlist/join")
    public ResponseEntity<?> joinWaitlist(@RequestBody WaitlistRequest request) {
        try {
            WaitingListEntry entry = libraryService.joinWaitlist(request.memberId, request.bookId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(mapper.toWaitlistDto(entry, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Cancels a waitlist entry if it is still active.
     */
    @PostMapping("/waitlist/cancel/{entryId}")
    public ResponseEntity<?> cancelWaitlist(@PathVariable Long entryId) {
        try {
            WaitingListEntry entry = libraryService.cancelWaitlist(entryId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(mapper.toWaitlistDto(entry, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Collects a reservation and creates the borrowing record for the member.
     */
    @PostMapping("/waitlist/collect/{entryId}/member/{memberId}")
    public ResponseEntity<?> collectReservation(@PathVariable Long entryId, @PathVariable Long memberId) {
        try {
            Loan loan = libraryService.collectReservation(memberId, entryId);
            LocalDate today = clockService.getVirtualDate();
            return ResponseEntity.ok(mapper.toLoanDto(loan, today));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
