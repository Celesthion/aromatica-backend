package com.aromatica.modules.inventory.doc.service.impl;

import com.aromatica.modules.inventory.doc.event.DocActionEvent;
import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.repository.DocEventRepository;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocEventServiceImpl implements DocEventService {

    DocEventRepository repository;
    DocService docService;
    ApplicationEventPublisher events;
    UserDirectory users;

    @Transactional
    @Override
    public void record(
            UUID docId,
            DocEvent.Action action,
            String login
    ) {
        UUID actorId = users.idByLogin(login);
        DocEvent event = new DocEvent();
        event.setDocId(docId);
        event.setAction(action);
        event.setActorId(actorId);
        repository.save(event);
        // лента активности подписана на это событие
        events.publishEvent(new DocActionEvent(docService.find(docId), action, actorId, event.getAt()));
    }

    @Override
    public List<DocEvent> of(UUID docId) {
        return repository.findByDocIdOrderByAtAscIdAsc(docId);
    }
}
