-- ==============================================================================
-- Schema Initialization for Enterprise Closed-Corpus Benchmark
-- Creates 3 pgvector tables with HNSW cosine indexes:
-- 1. vector_store_enterprise_closed (baseline deduplicated chunks)
-- 2. vector_store_enterprise_closed_large (S2B parent context, 1024 tokens)
-- 3. vector_store_enterprise_closed_small (S2B child search index, 128 tokens)
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Baseline table
CREATE TABLE IF NOT EXISTS public.vector_store_enterprise_closed (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);
CREATE INDEX IF NOT EXISTS vector_store_enterprise_closed_index 
ON public.vector_store_enterprise_closed USING hnsw (embedding vector_cosine_ops);

-- 2. Small-to-Big Parent context table (1024 tokens)
CREATE TABLE IF NOT EXISTS public.vector_store_enterprise_closed_large (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);
CREATE INDEX IF NOT EXISTS vector_store_enterprise_closed_large_index 
ON public.vector_store_enterprise_closed_large USING hnsw (embedding vector_cosine_ops);

-- 3. Small-to-Big Child dense index table (128 tokens)
CREATE TABLE IF NOT EXISTS public.vector_store_enterprise_closed_small (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);
CREATE INDEX IF NOT EXISTS vector_store_enterprise_closed_small_index 
ON public.vector_store_enterprise_closed_small USING hnsw (embedding vector_cosine_ops);
