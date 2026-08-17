package com.toto.baseballApi.matchschedule.domain;

import java.util.Collection;
import java.util.List;

public interface MatchScheduleRepository {

    /** Every listed fixture for one date, across the given markets. */
    List<MatchSchedule> findByYmdAndGameTypes(String ymd, Collection<String> gameTypes);

    /** Distinct dates currently staged, ascending — how far ahead the ingestion has been run. */
    List<String> findDistinctYmds();
}
