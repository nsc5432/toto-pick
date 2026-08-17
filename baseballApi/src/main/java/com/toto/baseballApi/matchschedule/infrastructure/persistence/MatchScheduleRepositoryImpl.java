package com.toto.baseballApi.matchschedule.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.toto.baseballApi.matchschedule.domain.MatchSchedule;
import com.toto.baseballApi.matchschedule.domain.MatchScheduleRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class MatchScheduleRepositoryImpl implements MatchScheduleRepository {

    private final MatchScheduleJpaRepository jpaRepository;

    @Override
    public List<MatchSchedule> findByYmdAndGameTypes(String ymd, Collection<String> gameTypes) {
        return jpaRepository.findByYmdAndGameTypeIn(ymd, gameTypes).stream()
                .map(MatchScheduleRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<String> findDistinctYmds() {
        return jpaRepository.findDistinctYmds();
    }

    private static MatchSchedule toDomain(MatchScheduleJpaEntity entity) {
        return new MatchSchedule(
                entity.getId(), entity.getYear(), entity.getRound(), entity.getTournament(),
                entity.getYmd(), entity.getTm(), entity.getHome(), entity.getAway(),
                entity.getGameType(), entity.getCond(),
                entity.getPubHomeDiv(), entity.getPubDrawDiv(), entity.getPubAwayDiv());
    }
}
