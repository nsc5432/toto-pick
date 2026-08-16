package com.toto.baseballApi.baseballresult.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.DivUpdate;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class BaseballResultRepositoryImpl implements BaseballResultRepository {

    private final BaseballResultJpaRepository baseballResultJpaRepository;

    @Override
    public Page<BaseballResult> findAll(Pageable pageable) {
        return baseballResultJpaRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public List<BaseballResult> findByYearAndRound(Integer year, Integer round) {
        return baseballResultJpaRepository.findByYearAndRound(year, round).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BaseballResult> findAllById(Collection<Integer> ids) {
        return baseballResultJpaRepository.findAllById(ids).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BaseballResult> findByGameType(String gameType) {
        return baseballResultJpaRepository.findByGameType(gameType).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BaseballResult> findByGameTypeAndTournamentsAndYmdBetween(
            String gameType, Collection<String> tournaments, String ymdFrom, String ymdTo) {
        return baseballResultJpaRepository
                .findByGameTypeAndTournamentInAndYmdBetween(gameType, tournaments, ymdFrom, ymdTo).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BaseballResult> findByGameTypeAndTournamentsAndYmdBefore(
            String gameType, Collection<String> tournaments, String ymdExclusive) {
        return baseballResultJpaRepository
                .findByGameTypeAndTournamentInAndYmdLessThan(gameType, tournaments, ymdExclusive).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int updateDivs(List<DivUpdate> updates) {
        Map<Integer, DivUpdate> updatesById = updates.stream()
                .collect(Collectors.toMap(DivUpdate::id, Function.identity()));
        List<BaseballResultJpaEntity> entities = baseballResultJpaRepository.findAllById(updatesById.keySet());
        for (BaseballResultJpaEntity entity : entities) {
            DivUpdate update = updatesById.get(entity.getId());
            entity.applyDivs(update.homeDiv(), update.drawDiv(), update.awayDiv());
        }
        // Dirty checking flushes the changes when the transaction commits.
        return entities.size();
    }

    private BaseballResult toDomain(BaseballResultJpaEntity entity) {
        return new BaseballResult(
                entity.getId(),
                entity.getYear(),
                entity.getRound(),
                entity.getTournament(),
                entity.getYmd(),
                entity.getTm(),
                entity.getHome(),
                entity.getAway(),
                entity.getGameType(),
                entity.getCond(),
                entity.getRes1(),
                entity.getRes2(),
                entity.getTotalResult(),
                entity.getTotalDiv(),
                entity.getHomeDiv(),
                entity.getDrawDiv(),
                entity.getAwayDiv());
    }
}
