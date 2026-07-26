ALTER TABLE agents ALTER COLUMN knowledge_base_id DROP NOT NULL;
ALTER TABLE agent_versions ALTER COLUMN knowledge_base_id DROP NOT NULL;

ALTER TABLE agents ADD COLUMN conversation_starters_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE agents ADD COLUMN use_knowledge BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE agents ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agents ADD COLUMN labels_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE agents ADD COLUMN enabled_actions_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE agents ADD COLUMN knowledge_cutoff_date DATE;

ALTER TABLE agent_versions ADD COLUMN conversation_starters_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE agent_versions ADD COLUMN use_knowledge BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE agent_versions ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agent_versions ADD COLUMN labels_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE agent_versions ADD COLUMN enabled_actions_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE agent_versions ADD COLUMN knowledge_cutoff_date DATE;
