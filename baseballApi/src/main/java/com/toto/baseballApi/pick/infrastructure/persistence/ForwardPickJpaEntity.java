package com.toto.baseballApi.pick.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pick_forward")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class ForwardPickJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pick_forward_id")
    private Integer id;

    @Column(name = "algorithm_code", nullable = false, length = 30)
    private String algorithmCode;

    @Column(name = "param_signature", nullable = false, length = 200)
    private String paramSignature;

    @Column(name = "user_name", nullable = false, length = 20)
    private String userName;

    @Column(name = "ymd", nullable = false, length = 8)
    private String ymd;

    @Column(name = "bucket", nullable = false, length = 10)
    private String bucket;

    @Column(name = "slip_no", nullable = false)
    private Integer slipNo;

    @Column(name = "input_money", nullable = false)
    private BigDecimal inputMoney;

    @Column(name = "combined_odds")
    private BigDecimal combinedOdds;

    // The only mutable fields: everything else is the pre-game record and must not be rewritten.
    @Setter
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Setter
    @Column(name = "output_money")
    private BigDecimal outputMoney;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
