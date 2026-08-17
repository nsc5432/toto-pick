package com.toto.baseballApi.baseballresult.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BaseballResultRepository {

    Page<BaseballResult> findAll(Pageable pageable);

    List<BaseballResult> findByYearAndRound(Integer year, Integer round);

    List<BaseballResult> findAllById(Collection<Integer> ids);

    List<BaseballResult> findByGameType(String gameType);

    List<BaseballResult> findByGameTypeAndTournamentsAndYmdBetween(
            String gameType, Collection<String> tournaments, String ymdFrom, String ymdTo);

    List<BaseballResult> findByGameTypeAndTournamentsAndYmdBefore(
            String gameType, Collection<String> tournaments, String ymdExclusive);
}
