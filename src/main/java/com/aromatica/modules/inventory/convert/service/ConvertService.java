package com.aromatica.modules.inventory.convert.service;

import com.aromatica.modules.inventory.convert.model.ConvertOpening;
import com.aromatica.modules.inventory.convert.model.SplitOption;
import com.aromatica.modules.inventory.doc.model.StockDoc;

import java.util.UUID;

/** Вскрытие: штуки флакона или тестера → миллилитры распив-SKU того же аромата (создаётся при необходимости). */
public interface ConvertService {

    SplitOption splitInfo(UUID sourceProductId);

    StockDoc open(ConvertOpening opening);
}
