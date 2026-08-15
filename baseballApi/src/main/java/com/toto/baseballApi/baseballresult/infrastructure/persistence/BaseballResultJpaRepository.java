package com.toto.baseballApi.baseballresult.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface BaseballResultJpaRepository extends JpaRepository<BaseballResultJpaEntity, Integer> {
}
