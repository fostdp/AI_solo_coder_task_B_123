package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.ContactEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactEdgeRepository extends JpaRepository<ContactEdge, Long> {

    List<ContactEdge> findByBarracksIdAndIsActiveTrue(Long barracksId);

    @Query("SELECT c FROM ContactEdge c WHERE c.barracksId = :barracksId AND c.isActive = true AND c.contactType = :contactType")
    List<ContactEdge> findByBarracksIdAndContactType(Long barracksId, String contactType);

    @Query("SELECT COUNT(c) FROM ContactEdge c WHERE c.barracksId = :barracksId AND c.isActive = true")
    long countActiveByBarracksId(Long barracksId);
}
