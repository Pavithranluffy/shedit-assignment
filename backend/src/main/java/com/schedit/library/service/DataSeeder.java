package com.schedit.library.service;

import com.schedit.library.model.Book;
import com.schedit.library.model.BookCopy;
import com.schedit.library.model.Member;
import com.schedit.library.model.SystemClockState;
import com.schedit.library.repository.BookCopyRepository;
import com.schedit.library.repository.BookRepository;
import com.schedit.library.repository.MemberRepository;
import com.schedit.library.repository.SystemClockStateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DataSeeder implements ApplicationRunner {

    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final MemberRepository memberRepository;
    private final SystemClockStateRepository clockRepository;

    public DataSeeder(BookRepository bookRepository,
                      BookCopyRepository copyRepository,
                      MemberRepository memberRepository,
                      SystemClockStateRepository clockRepository) {
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.memberRepository = memberRepository;
        this.clockRepository = clockRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasSeedBook = bookRepository.findAll().stream().anyMatch(book -> "The Hobbit".equals(book.getTitle()));
        if (!hasSeedBook) {
            Member regular = memberRepository.save(new Member("Mina", "mina@example.com", Member.MembershipTier.REGULAR));
            Member supporting = memberRepository.save(new Member("Owen", "owen@example.com", Member.MembershipTier.SUPPORTING));
            memberRepository.save(new Member("Lina", "lina@example.com", Member.MembershipTier.REGULAR));

            Book hobbit = bookRepository.save(new Book("The Hobbit", "J.R.R. Tolkien", "9780261102217", new BigDecimal("15.00")));
            Book dune = bookRepository.save(new Book("Dune", "Frank Herbert", "9780441172719", new BigDecimal("20.00")));
            Book pride = bookRepository.save(new Book("Pride and Prejudice", "Jane Austen", "9780141439518", new BigDecimal("12.00")));

            copyRepository.save(new BookCopy(hobbit, BookCopy.CopyStatus.AVAILABLE, "HB-101"));
            copyRepository.save(new BookCopy(hobbit, BookCopy.CopyStatus.AVAILABLE, "HB-102"));
            copyRepository.save(new BookCopy(dune, BookCopy.CopyStatus.AVAILABLE, "DN-201"));
            copyRepository.save(new BookCopy(pride, BookCopy.CopyStatus.AVAILABLE, "PP-301"));
            copyRepository.save(new BookCopy(pride, BookCopy.CopyStatus.AVAILABLE, "PP-302"));

            regular.setBalance(BigDecimal.ZERO);
            supporting.setBalance(BigDecimal.ZERO);
            memberRepository.save(regular);
            memberRepository.save(supporting);
        }

        if (clockRepository.findById(1L).isEmpty()) {
            clockRepository.save(new SystemClockState(LocalDate.now()));
        }
    }
}
