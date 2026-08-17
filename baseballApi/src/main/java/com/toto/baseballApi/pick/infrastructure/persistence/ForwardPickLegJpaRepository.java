package com.toto.baseballApi.pick.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ForwardPickLegJpaRepository
        extends JpaRepository<ForwardPickLegJpaEntity, ForwardPickLegId> {

    List<ForwardPickLegJpaEntity> findByForwardPickIdInOrderByForwardPickIdAscLegNoAsc(
            Collection<Integer> forwardPickIds);
}
