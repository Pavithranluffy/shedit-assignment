package com.schedit.library.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;

@Entity
public class SystemClockState {
    @Id
    private Long id = 1L;

    @Column(name = "clock_date")
    private LocalDate currentDate;

    public SystemClockState() {}

    public SystemClockState(LocalDate currentDate) {
        this.currentDate = currentDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getCurrentDate() { return currentDate; }
    public void setCurrentDate(LocalDate currentDate) { this.currentDate = currentDate; }
}
