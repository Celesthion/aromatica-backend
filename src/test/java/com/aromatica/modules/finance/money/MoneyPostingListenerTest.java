package com.aromatica.modules.finance.money;

import com.aromatica.modules.finance.money.listener.MoneyPostingListener;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.event.DocPostedEvent;
import com.aromatica.modules.inventory.doc.event.DocUnpostedEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/** Деньги идут вслед за проведением и распроведением. */
@ExtendWith(MockitoExtension.class)
class MoneyPostingListenerTest {

    @Mock
    private MoneyService moneyService;

    @Test
    void postedAndUnposted_reachMoneyService() {
        StockDoc doc = new StockDoc();
        MoneyPostingListener listener = new MoneyPostingListener(moneyService);

        listener.onPosted(new DocPostedEvent(doc));
        verify(moneyService).onPost(doc);

        listener.onUnposted(new DocUnpostedEvent(doc));
        verify(moneyService).onUnpost(doc);
    }
}
