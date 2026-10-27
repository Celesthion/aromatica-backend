package com.aromatica.modules.masterdata.catalog.brand.model;

import com.aromatica.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Справочник брендов. Продукты хранят имя бренда строкой, без ссылки. */
@Entity
@Table(name = "brand")
@Getter
@Setter
@NoArgsConstructor
public class Brand extends Auditable {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;
    private boolean archived;
}
