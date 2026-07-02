package com.schedit.library.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class Dtos {

    public static class BookDto {
        public Long id;
        public String title;
        public String author;
        public String isbn;
        public BigDecimal replacementCost;
        public int totalCopies;
        public int availableCopies;
        public int reservedCopies;
        public int waitingListCount;
        public List<CopyDto> copies;
    }

    public static class CopyDto {
        public Long id;
        public String barcode;
        public String status;
    }

    public static class MemberDto {
        public Long id;
        public String name;
        public String email;
        public String tier;
        public BigDecimal balance;
        public int activeLoansCount;
    }

    public static class LoanDto {
        public Long id;
        public String bookTitle;
        public String copyBarcode;
        public Long copyId;
        public Long memberId;
        public String memberName;
        public LocalDate borrowDate;
        public LocalDate dueDate;
        public LocalDate returnDate;
        public BigDecimal feeCharged;
        public boolean isOverdue;
    }

    public static class WaitingListEntryDto {
        public Long id;
        public Long bookId;
        public String bookTitle;
        public Long memberId;
        public String memberName;
        public String status;
        public LocalDateTime createdAt;
        public LocalDateTime reservedAt;
        public String reservedCopyBarcode;
        public Long reservedCopyId;
        public Long daysRemaining;
    }

    public static class BorrowRequest {
        public Long memberId;
        public Long bookId;
    }

    public static class WaitlistRequest {
        public Long memberId;
        public Long bookId;
    }

    public static class AddBookRequest {
        public String title;
        public String author;
        public String isbn;
        public BigDecimal replacementCost;
    }

    public static class AddCopyRequest {
        public String barcode;
    }

    public static class DowngradeRequest {
        public String tier;
    }

    public static class SettleRequest {
        public BigDecimal amount;
    }

    public static class AdvanceClockRequest {
        public int days;
    }
}
