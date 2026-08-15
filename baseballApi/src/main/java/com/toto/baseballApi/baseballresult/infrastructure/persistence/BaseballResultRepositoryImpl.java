package com.toto.baseballApi.baseballresult.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;

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
                entity.getTotalDiv());
    }
}
