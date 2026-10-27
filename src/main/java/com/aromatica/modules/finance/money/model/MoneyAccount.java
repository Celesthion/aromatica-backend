package com.aromatica.modules.finance.money.model;

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

import java.util.UUID;

@Entity
@Table(name = "money_account")
@Getter
@Setter
@NoArgsConstructor
public class MoneyAccount extends Auditable {

    public enum Type { CASH, BANK }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "is_default")
    private boolean isDefault;

    private boolean archived;
}
