package com.schedit.library.service;

import com.schedit.library.model.WaitingListEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryDtoMapperTest {

    @Test
    void shouldCalculateRemainingDaysForActiveReservation() {
        LibraryDtoMapper mapper = new LibraryDtoMapper();
        WaitingListEntry entry = new WaitingListEntry();
        entry.setReservedAt(LocalDateTime.of(2026, 7, 1, 12, 0));

        long remainingDays = mapper.calculateDaysRemaining(LocalDate.of(2026, 7, 2), entry);

        assertEquals(2L, remainingDays);
    }
}
