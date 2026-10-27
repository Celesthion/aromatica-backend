package com.aromatica.modules.workspace.task.search;

import com.aromatica.common.search.BaseSearchRequest;
import com.aromatica.modules.workspace.task.model.Task;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaskSearchRequest extends BaseSearchRequest<TaskSort> {

    String q;
    Task.Status status;

    @Override
    protected TaskSort getDefaultSortBy() {
        return TaskSort.URGENT;
    }
}
