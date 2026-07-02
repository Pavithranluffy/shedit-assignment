package com.schedit.library.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Member {
    public enum MembershipTier {
        REGULAR(3, 14), // Max 3 books, 14 days loan duration
        SUPPORTING(6, 28); // Max 6 books, 28 days loan duration

        private final int maxBooks;
        private final int loanPeriodDays;

        MembershipTier(int maxBooks, int loanPeriodDays) {
            this.maxBooks = maxBooks;
            this.loanPeriodDays = loanPeriodDays;
        }

        public int getMaxBooks() { return maxBooks; }
        public int getLoanPeriodDays() { return loanPeriodDays; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    @Enumerated(EnumType.STRING)
    private MembershipTier tier;

    @Column(precision = 10, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    public Member() {}

    public Member(String name, String email, MembershipTier tier) {
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.balance = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public MembershipTier getTier() { return tier; }
    public void setTier(MembershipTier tier) { this.tier = tier; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
