package com.schedit.library.service;

import com.schedit.library.model.SystemClockState;
import com.schedit.library.repository.SystemClockStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ClockService {

    private final SystemClockStateRepository clockStateRepository;

    @Autowired
    public ClockService(SystemClockStateRepository clockStateRepository) {
        this.clockStateRepository = clockStateRepository;
    }

    @Transactional
    public LocalDate getVirtualDate() {
        SystemClockState state = clockStateRepository.findById(1L)
                .orElseGet(() -> clockStateRepository.save(new SystemClockState(LocalDate.now())));
        return state.getCurrentDate();
    }

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
