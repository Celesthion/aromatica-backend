package com.aromatica.modules.inventory.doc.service;

import com.aromatica.modules.inventory.doc.model.DocEvent;

import java.util.List;
import java.util.UUID;

/** Журнал событий документа, лёгкий аудит. Актор — id пользователя. */
public interface DocEventService {

    void record(UUID docId, DocEvent.Action action, String login);

    List<DocEvent> of(UUID docId);
}
