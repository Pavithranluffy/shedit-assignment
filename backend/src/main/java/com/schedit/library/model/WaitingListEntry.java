package com.schedit.library.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class WaitingListEntry {
    public enum WaitlistStatus {
        WAITING,
        RESERVED,
        EXPIRED,
        FULFILLED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private WaitlistStatus status;

    private LocalDateTime reservedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reserved_copy_id")
    private BookCopy reservedCopy;

    public WaitingListEntry() {}

    public WaitingListEntry(Book book, Member member, LocalDateTime createdAt, WaitlistStatus status) {
        this.book = book;
        this.member = member;
        this.createdAt = createdAt;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public WaitlistStatus getStatus() { return status; }
    public void setStatus(WaitlistStatus status) { this.status = status; }
    public LocalDateTime getReservedAt() { return reservedAt; }
    public void setReservedAt(LocalDateTime reservedAt) { this.reservedAt = reservedAt; }
    public BookCopy getReservedCopy() { return reservedCopy; }
    public void setReservedCopy(BookCopy reservedCopy) { this.reservedCopy = reservedCopy; }
}
