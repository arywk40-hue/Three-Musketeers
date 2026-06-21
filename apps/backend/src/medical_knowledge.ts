/**
 * Medical knowledge fetcher.
 *
 * Two sources:
 *  1. PubMed E-utilities (NCBI) — free, no API key required for low volume.
 *     Searches for recent review articles matching a health term.
 *  2. WHO News RSS — parses the WHO global health feed for matching items.
 *
 * Results are cached in memory for 1 hour to avoid hammering the APIs.
 */

import { XMLParser } from 'fast-xml-parser';

const PUBMED_BASE = 'https://eutils.ncbi.nlm.nih.gov/entrez/eutils';
const WHO_RSS     = 'https://www.who.int/rss-feeds/news-english.xml';
const CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

interface Article {
  title: string;
  url: string;
  source: 'pubmed' | 'who';
  published?: string;
}

interface CacheEntry {
  articles: Article[];
  fetchedAt: number;
}

const cache = new Map<string, CacheEntry>();

function cacheKey(query: string) { return query.toLowerCase().trim(); }

function isFresh(entry: CacheEntry): boolean {
  return Date.now() - entry.fetchedAt < CACHE_TTL_MS;
}

// ── PubMed ────────────────────────────────────────────────────────────────────

async function fetchPubMed(query: string, maxResults = 5): Promise<Article[]> {
  try {
    // Step 1: search for IDs
    const searchUrl = `${PUBMED_BASE}/esearch.fcgi?db=pubmed&term=${encodeURIComponent(query + '[Title/Abstract] AND Review[pt]')}&retmax=${maxResults}&retmode=json&sort=relevance`;
    const searchRes = await fetch(searchUrl, { signal: AbortSignal.timeout(8000) });
    if (!searchRes.ok) return [];
    const searchJson = await searchRes.json() as any;
    const ids: string[] = searchJson?.esearchresult?.idlist ?? [];
    if (ids.length === 0) return [];

    // Step 2: fetch summaries
    const summaryUrl = `${PUBMED_BASE}/esummary.fcgi?db=pubmed&id=${ids.join(',')}&retmode=json`;
    const summaryRes = await fetch(summaryUrl, { signal: AbortSignal.timeout(8000) });
    if (!summaryRes.ok) return [];
    const summaryJson = await summaryRes.json() as any;
    const result = summaryJson?.result ?? {};

    return ids
      .map((id): Article | null => {
        const doc = result[id];
        if (!doc?.title) return null;
        return {
          title: doc.title,
          url: `https://pubmed.ncbi.nlm.nih.gov/${id}/`,
          source: 'pubmed',
          published: doc.pubdate,
        };
      })
      .filter((a): a is Article => a !== null);
  } catch {
    return [];
  }
}

// ── WHO RSS ───────────────────────────────────────────────────────────────────

async function fetchWHO(query: string, maxResults = 3): Promise<Article[]> {
  try {
    const res = await fetch(WHO_RSS, { signal: AbortSignal.timeout(8000) });
    if (!res.ok) return [];
    const xml = await res.text();
    const parser = new XMLParser({ ignoreAttributes: false });
    const parsed = parser.parse(xml);
    const items: any[] = parsed?.rss?.channel?.item ?? [];
    const q = query.toLowerCase();
    return items
      .filter((item: any) =>
        (item.title?.toLowerCase().includes(q) || item.description?.toLowerCase().includes(q))
      )
      .slice(0, maxResults)
      .map((item: any): Article => ({
        title: item.title ?? 'WHO News',
        url: item.link ?? 'https://www.who.int',
        source: 'who',
        published: item.pubDate,
      }));
  } catch {
    return [];
  }
}

// ── Public API ────────────────────────────────────────────────────────────────

export interface MedicalKnowledgeResult {
  query: string;
  articles: Article[];
  cached: boolean;
  fetchedAt: string;
}

export async function fetchMedicalKnowledge(query: string): Promise<MedicalKnowledgeResult> {
  const key = cacheKey(query);
  const cached = cache.get(key);
  if (cached && isFresh(cached)) {
    return { query, articles: cached.articles, cached: true, fetchedAt: new Date(cached.fetchedAt).toISOString() };
  }

  // Fetch both sources in parallel
  const [pubmedArticles, whoArticles] = await Promise.all([
    fetchPubMed(query),
    fetchWHO(query),
  ]);

  const articles = [...pubmedArticles, ...whoArticles];
  const entry: CacheEntry = { articles, fetchedAt: Date.now() };
  cache.set(key, entry);

  return { query, articles, cached: false, fetchedAt: new Date(entry.fetchedAt).toISOString() };
}
