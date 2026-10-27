package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.modules.inventory.doc.model.DocType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Стратегии по типам; новый тип без ровно одной стратегии не даст приложению запуститься. */
@Component
public class DocPostings {

    private final Map<DocType, DocPosting> byType = new EnumMap<>(DocType.class);

    public DocPostings(List<DocPosting> postings) {
        for (DocPosting p : postings) {
            if (byType.put(p.type(), p) != null) {
                throw new IllegalStateException("Две стратегии проведения для " + p.type());
            }
        }
        List<DocType> missing = Arrays.stream(DocType.values()).filter(t -> !byType.containsKey(t)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Нет стратегии проведения для " + missing);
        }
    }

    public DocPosting of(DocType type) {
        return byType.get(type);
    }
}
