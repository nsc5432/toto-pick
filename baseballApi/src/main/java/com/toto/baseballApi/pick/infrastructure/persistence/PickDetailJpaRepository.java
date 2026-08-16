package com.toto.baseballApi.pick.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PickDetailJpaRepository extends JpaRepository<PickDetailJpaEntity, PickDetailId> {

    List<PickDetailJpaEntity> findByPickMasterId(Integer pickMasterId);

    void deleteByPickMasterIdIn(Collection<Integer> pickMasterIds);
}
