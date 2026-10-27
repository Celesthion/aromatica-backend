package com.aromatica.modules.masterdata.catalog.productmodel.model;

import com.aromatica.common.audit.CreatedAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Фото висит на продукте, а не на форме: у флакона, тестера и распива одна картинка. Байты в базе
 * (диска у бэка нет); основная одна — держит индекс {@code product_model_image_one_main_idx}.
 */
@Entity
@Table(name = "product_model_image")
@Getter
@Setter
@NoArgsConstructor
public class ProductModelImage extends CreatedAudit {

    @Id
    private UUID id;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    // ленивую загрузку поля Hibernate без байткод-плагина не делает, поэтому списки фото
    // читают ProductModelImageSummary, а байты грузятся только при отдаче картинки
    @Column(name = "bytes", nullable = false, columnDefinition = "bytea")
    private byte[] bytes;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    /** Порядок в галерее продукта. */
    @Column(name = "sort", nullable = false)
    private int sort;
}
