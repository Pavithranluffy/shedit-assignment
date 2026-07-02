package com.schedit.library.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String author;
    private String isbn;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal replacementCost;

    public Book() {}

    public Book(String title, String author, String isbn, BigDecimal replacementCost) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.replacementCost = replacementCost;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public BigDecimal getReplacementCost() { return replacementCost; }
    public void setReplacementCost(BigDecimal replacementCost) { this.replacementCost = replacementCost; }
}
