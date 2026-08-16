package com.toto.baseballApi.baseballresult.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface BaseballResultJpaRepository extends JpaRepository<BaseballResultJpaEntity, Integer> {

    List<BaseballResultJpaEntity> findByYearAndRound(Integer year, Integer round);

    List<BaseballResultJpaEntity> findByGameType(String gameType);

    List<BaseballResultJpaEntity> findByGameTypeAndTournamentInAndYmdBetween(
            String gameType, Collection<String> tournaments, String ymdFrom, String ymdTo);

    List<BaseballResultJpaEntity> findByGameTypeAndTournamentInAndYmdLessThan(
            String gameType, Collection<String> tournaments, String ymd);
}
