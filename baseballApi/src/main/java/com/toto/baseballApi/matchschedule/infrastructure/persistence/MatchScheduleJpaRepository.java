package com.toto.baseballApi.matchschedule.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MatchScheduleJpaRepository extends JpaRepository<MatchScheduleJpaEntity, Integer> {

    List<MatchScheduleJpaEntity> findByYmdAndGameTypeIn(String ymd, Collection<String> gameTypes);

    @Query("select distinct m.ymd from MatchScheduleJpaEntity m order by m.ymd")
    List<String> findDistinctYmds();
}
