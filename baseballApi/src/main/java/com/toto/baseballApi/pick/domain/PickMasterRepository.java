package com.toto.baseballApi.pick.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PickMasterRepository {

    PickMaster save(PickMaster pickMaster);

    Optional<PickMaster> findById(Integer id);

    Page<PickMaster> findAll(Pageable pageable);

    List<PickMaster> findAllPendingSettlement();

    /**
     * Deletes masters (and their details) for the user, restricted to the given algorithm codes,
     * within the ymd range; returns the master count removed.
     */
    int deleteByUserNameAndAlgorithmCodesAndYmdRange(
            String userName, List<String> algorithmCodes, String ymdFrom, String ymdTo);

    /**
     * KPI read model for the user's algorithm-generated picks within the ymd range.
     * Null/empty {@code algorithmCodes} means every pick whose algorithm_code is non-null.
     */
    List<PickKpiRow> findKpiRows(String userName, List<String> algorithmCodes, String ymdFrom, String ymdTo);
}
