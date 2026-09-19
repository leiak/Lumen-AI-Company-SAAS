-- ============================================================
-- V2.0.1 — P3 workflow hardening: unique active instance
-- Prevents two concurrent startInstance calls with the same
-- businessKey from both passing the in-app duplicate check.
-- The (business_key, deleted) tuple allows re-use after a
-- logical delete (deleted flips to 1).
-- ============================================================

ALTER TABLE wf_instance
    ADD UNIQUE KEY uk_business_key_active (business_key, deleted);
