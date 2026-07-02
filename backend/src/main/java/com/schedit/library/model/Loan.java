package com.schedit.library.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "copy_id", nullable = false)
    private BookCopy bookCopy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal feeCharged = BigDecimal.ZERO;

    public Loan() {}

    public Loan(BookCopy bookCopy, Member member, LocalDate borrowDate, LocalDate dueDate) {
        this.bookCopy = bookCopy;
        this.member = member;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.feeCharged = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BookCopy getBookCopy() { return bookCopy; }
    public void setBookCopy(BookCopy bookCopy) { this.bookCopy = bookCopy; }
    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDate borrowDate) { this.borrowDate = borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public BigDecimal getFeeCharged() { return feeCharged; }
    public void setFeeCharged(BigDecimal feeCharged) { this.feeCharged = feeCharged; }
}
