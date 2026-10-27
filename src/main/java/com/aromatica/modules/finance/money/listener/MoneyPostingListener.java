package com.aromatica.modules.finance.money.listener;

import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.event.DocPostedEvent;
import com.aromatica.modules.inventory.doc.event.DocUnpostedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Склад о деньгах не знает, только публикует событие. Слушаем синхронно в той же транзакции:
 * движения денег без проведённого документа быть не должно, и наоборот.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MoneyPostingListener {

    MoneyService moneyService;

    @EventListener
    public void onPosted(DocPostedEvent event) {
        moneyService.onPost(event.doc());
    }

    @EventListener
    public void onUnposted(DocUnpostedEvent event) {
        moneyService.onUnpost(event.doc());
    }
}
