package com.schedit.library.model;

import jakarta.persistence.*;

@Entity
public class BookCopy {
    public enum CopyStatus {
        AVAILABLE,
        ON_LOAN,
        RESERVED,
        DAMAGED,
        LOST
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    private CopyStatus status;

    private String barcode;

    public BookCopy() {}

    public BookCopy(Book book, CopyStatus status, String barcode) {
        this.book = book;
        this.status = status;
        this.barcode = barcode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public CopyStatus getStatus() { return status; }
    public void setStatus(CopyStatus status) { this.status = status; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
