package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.Barracks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BarracksRepository extends JpaRepository<Barracks, Long> {
    Optional<Barracks> findByCode(String code);
}
