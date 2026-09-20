-- ============================================================
-- V2.0.2 — P3 workflow hardening: optimistic-lock version on wf_task
-- WfTask.version is annotated with @Version; MyBatis-Plus's
-- OptimisticLockerInnerInterceptor increments the column on
-- updateById and treats affected_rows=0 as a lock conflict.
-- ============================================================

ALTER TABLE wf_task
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER comment;
