package com.aromatica.modules.masterdata.party.model;

import com.aromatica.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Контрагент: поставщик и/или покупатель. */
@Entity
@Table(name = "counterparty")
@Getter
@Setter
@NoArgsConstructor
public class Counterparty extends Auditable {

    /** Тип управляет поведением: приёмка предлагает поставщиков, продажа и возврат — покупателей. */
    public enum Type { SUPPLIER, CUSTOMER }

    /** У организаций пола нет, поэтому необязателен. */
    public enum Gender { MALE, FEMALE }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String phone;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    /** Дата рождения — для поздравлений и возрастных акций. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(columnDefinition = "text")
    private String note;

    private boolean archived;
}
