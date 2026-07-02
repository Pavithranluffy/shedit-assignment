package com.schedit.library.service;

import com.schedit.library.model.SystemClockState;
import com.schedit.library.repository.SystemClockStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Coordinates the virtual date used by business rules and reservation expiration logic.
 */
@Service
public class ClockService {

    private final SystemClockStateRepository clockStateRepository;

    /**
     * Creates a clock service bound to the persisted clock state repository.
     */
    public ClockService(SystemClockStateRepository clockStateRepository) {
        this.clockStateRepository = clockStateRepository;
    }

    /**
     * Returns the current virtual date, creating it if it does not exist yet.
     */
    @Transactional
    public LocalDate getVirtualDate() {
        SystemClockState state = clockStateRepository.findById(1L)
                .orElseGet(() -> clockStateRepository.save(new SystemClockState(LocalDate.now())));
        return state.getCurrentDate();
    }

    /**
     * Advances the virtual date by the provided number of days.
     *
     * @param days the number of days to move the virtual clock forward
     * @return the new virtual date after the update
     */
    @Transactional
    public LocalDate advanceClock(int days) {
        SystemClockState state = clockStateRepository.findById(1L)
                .orElseGet(() -> clockStateRepository.save(new SystemClockState(LocalDate.now())));
        LocalDate nextDate = state.getCurrentDate().plusDays(days);
        state.setCurrentDate(nextDate);
        clockStateRepository.save(state);
        return nextDate;
    }
}
