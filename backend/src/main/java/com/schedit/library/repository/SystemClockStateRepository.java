package com.schedit.library.repository;

import com.schedit.library.model.SystemClockState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemClockStateRepository extends JpaRepository<SystemClockState, Long> {
}
