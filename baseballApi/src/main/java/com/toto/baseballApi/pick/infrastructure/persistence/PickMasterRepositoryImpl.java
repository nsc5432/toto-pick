package com.toto.baseballApi.pick.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.pick.domain.PickDetail;
import com.toto.baseballApi.pick.domain.PickKpiRow;
import com.toto.baseballApi.pick.domain.PickMaster;
import com.toto.baseballApi.pick.domain.PickMasterRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class PickMasterRepositoryImpl implements PickMasterRepository {

    private final PickMasterJpaRepository pickMasterJpaRepository;
    private final PickDetailJpaRepository pickDetailJpaRepository;

    @Override
    public PickMaster save(PickMaster pickMaster) {
        boolean isCreate = pickMaster.id() == null;

        PickMasterJpaEntity savedMaster = pickMasterJpaRepository.save(toMasterEntity(pickMaster));

        List<PickDetailJpaEntity> detailEntities;
        if (isCreate) {
            detailEntities = pickMaster.details().stream()
                    .map(detail -> toDetailEntity(savedMaster.getId(), detail))
                    .toList();
            pickDetailJpaRepository.saveAll(detailEntities);
        } else {
            // Details are immutable after creation (settlement only updates the master row).
            detailEntities = pickDetailJpaRepository.findByPickMasterId(savedMaster.getId());
        }

        return toDomain(savedMaster, detailEntities);
    }

    @Override
    public Optional<PickMaster> findById(Integer id) {
        return pickMasterJpaRepository.findById(id)
                .map(master -> toDomain(master, pickDetailJpaRepository.findByPickMasterId(master.getId())));
    }

    @Override
    public Page<PickMaster> findAll(Pageable pageable) {
        return pickMasterJpaRepository.findAll(pageable)
                .map(master -> toDomain(master, pickDetailJpaRepository.findByPickMasterId(master.getId())));
    }

    @Override
    public List<PickMaster> findAllPendingSettlement() {
        return pickMasterJpaRepository.findByOutputMoneyIsNull().stream()
                .map(master -> toDomain(master, pickDetailJpaRepository.findByPickMasterId(master.getId())))
                .toList();
    }

    @Override
    @Transactional
    public int deleteByUserNameAndAlgorithmCodesAndYmdRange(
            String userName, List<String> algorithmCodes, String ymdFrom, String ymdTo) {
        List<PickMasterJpaEntity> masters = pickMasterJpaRepository
                .findByUserNameAndAlgorithmCodeInAndYmdBetween(userName, algorithmCodes, ymdFrom, ymdTo);
        if (masters.isEmpty()) {
            return 0;
        }
        List<Integer> masterIds = masters.stream().map(PickMasterJpaEntity::getId).toList();
        pickDetailJpaRepository.deleteByPickMasterIdIn(masterIds);
        // The derived delete is queued in the persistence context; flush it so the detail rows are
        // gone before the bulk master delete below hits the pick_dtl -> pick_mstr FK constraint.
        pickDetailJpaRepository.flush();
        pickMasterJpaRepository.deleteAllInBatch(masters);
        return masters.size();
    }

    @Override
    public List<PickKpiRow> findKpiRows(
            String userName, List<String> algorithmCodes, String ymdFrom, String ymdTo) {
        List<PickMasterJpaEntity> masters = (algorithmCodes == null || algorithmCodes.isEmpty())
                ? pickMasterJpaRepository.findByUserNameAndAlgorithmCodeIsNotNullAndYmdBetween(
                        userName, ymdFrom, ymdTo)
                : pickMasterJpaRepository.findByUserNameAndAlgorithmCodeInAndYmdBetween(
                        userName, algorithmCodes, ymdFrom, ymdTo);
        return masters.stream()
                .map(m -> new PickKpiRow(
                        m.getAlgorithmCode(), m.getYmd(), m.getYear(), m.getRound(),
                        m.getInputMoney(), m.getOutputMoney()))
                .toList();
    }

    private PickMasterJpaEntity toMasterEntity(PickMaster pickMaster) {
        return new PickMasterJpaEntity(
                pickMaster.id(),
                pickMaster.year(),
                pickMaster.round(),
                pickMaster.ymd(),
                pickMaster.userName(),
                pickMaster.algorithmCode(),
                pickMaster.inputMoney(),
                pickMaster.outputMoney());
    }

    private PickDetailJpaEntity toDetailEntity(Integer pickMasterId, PickDetail detail) {
        return new PickDetailJpaEntity(pickMasterId, detail.resultId(), detail.totalResult());
    }

    private PickMaster toDomain(PickMasterJpaEntity master, List<PickDetailJpaEntity> details) {
        return new PickMaster(
                master.getId(),
                master.getYear(),
                master.getRound(),
                master.getYmd(),
                master.getUserName(),
                master.getAlgorithmCode(),
                master.getInputMoney(),
                master.getOutputMoney(),
                details.stream().map(this::toDomainDetail).toList());
    }

    private PickDetail toDomainDetail(PickDetailJpaEntity entity) {
        return new PickDetail(entity.getPickMasterId(), entity.getResultId(), entity.getTotalResult());
    }
}
