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
}
