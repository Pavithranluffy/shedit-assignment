package com.schedit.library.service;

import com.schedit.library.dto.Dtos.BookDto;
import com.schedit.library.dto.Dtos.LoanDto;
import com.schedit.library.dto.Dtos.MemberDto;
import com.schedit.library.dto.Dtos.WaitingListEntryDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides read-oriented query operations for the library domain and keeps the controller thin.
 */
@Service
public class LibraryQueryService {

    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final WaitingListEntryRepository waitlistRepository;
    private final LibraryDtoMapper mapper;

    /**
     * Creates a new query service with all required repositories.
     */
    public LibraryQueryService(BookRepository bookRepository,
                               BookCopyRepository copyRepository,
                               MemberRepository memberRepository,
                               LoanRepository loanRepository,
                               WaitingListEntryRepository waitlistRepository,
                               LibraryDtoMapper mapper) {
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.waitlistRepository = waitlistRepository;
        this.mapper = mapper;
    }

    /**
     * Returns all books with their copy availability and queue statistics.
     */
    @Transactional(readOnly = true)
    public List<BookDto> getBooks() {
        return bookRepository.findAll().stream()
                .map(this::toBookDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns all members with their current borrowing state.
     */
    @Transactional(readOnly = true)
    public List<MemberDto> getMembers() {
        return memberRepository.findAll().stream()
                .map(this::toMemberDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns every currently active loan for the library.
     */
    @Transactional(readOnly = true)
    public List<LoanDto> getActiveLoans(LocalDate today) {
        return loanRepository.findAll().stream()
                .filter(loan -> loan.getReturnDate() == null)
                .map(loan -> mapper.toLoanDto(loan, today))
                .collect(Collectors.toList());
    }

    /**
     * Returns the currently active loans for a specific member.
     */
    @Transactional(readOnly = true)
    public List<LoanDto> getMemberLoans(Long memberId, LocalDate today) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        return loanRepository.findByMemberAndReturnDateIsNull(member).stream()
                .map(loan -> mapper.toLoanDto(loan, today))
                .collect(Collectors.toList());
    }

    /**
     * Returns the complete waitlist across the library.
     */
    @Transactional(readOnly = true)
    public List<WaitingListEntryDto> getWaitlist(LocalDate today) {
        return waitlistRepository.findAll().stream()
                .map(entry -> mapper.toWaitlistDto(entry, today))
                .collect(Collectors.toList());
    }

    /**
     * Returns all active waitlist entries for a specific member.
     */
    @Transactional(readOnly = true)
    public List<WaitingListEntryDto> getMemberWaitlist(Long memberId, LocalDate today) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        return waitlistRepository.findActiveByMember(member).stream()
                .map(entry -> mapper.toWaitlistDto(entry, today))
                .collect(Collectors.toList());
    }

    private BookDto toBookDto(Book book) {
        List<BookCopy> copies = copyRepository.findByBook(book);
        List<WaitingListEntry> activeWaitlist = waitlistRepository.findActiveByBookOrderByCreatedAtAsc(book);
        return mapper.toBookDto(book, copies, activeWaitlist);
    }

    private MemberDto toMemberDto(Member member) {
        return mapper.toMemberDto(member, loanRepository.findByMemberAndReturnDateIsNull(member).size());
    }
}
