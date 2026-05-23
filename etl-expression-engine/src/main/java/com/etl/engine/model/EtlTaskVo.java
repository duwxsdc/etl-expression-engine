package com.etl.engine.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class EtlTaskVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String taskName;
    private String taskType;
    private String status;
    private Integer priority;
    private LocalDateTime createdAt;

    public EtlTaskVo() {
    }

    public EtlTaskVo(Integer id, String taskName, String taskType, String status, Integer priority) {
        this.id = id;
        this.taskName = taskName;
        this.taskType = taskType;
        this.status = status;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EtlTaskVo etlTaskVo = (EtlTaskVo) o;
        return Objects.equals(id, etlTaskVo.id)
                && Objects.equals(taskName, etlTaskVo.taskName)
                && Objects.equals(taskType, etlTaskVo.taskType)
                && Objects.equals(status, etlTaskVo.status)
                && Objects.equals(priority, etlTaskVo.priority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, taskName, taskType, status, priority);
    }

    @Override
    public String toString() {
        return "EtlTaskVo{id=" + id + ", taskName='" + taskName + "', taskType='" + taskType
                + "', status='" + status + "', priority=" + priority + '}';
    }
}
