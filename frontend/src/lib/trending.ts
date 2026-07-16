/**
 * trending.ts — Curated static list of trending books for the discovery surface
 *
 * CURATED_TRENDING is a static data constant — no API calls. It uses known stable
 * Open Library keys and cover IDs for well-known, widely-held works.
 *
 * T-15-07: Cover IDs are public (openlibrary.org covers endpoint is unauthenticated);
 * no sensitive data is exposed here.
 */

export interface TrendingBook {
  olKey: string;   // short form, no /works/ prefix
  title: string;
  coverId: string;
}

export const CURATED_TRENDING: TrendingBook[] = [
  { olKey: 'OL82586W',     title: "Harry Potter and the Philosopher's Stone", coverId: '8228691' },
  { olKey: 'OL17930368W',  title: 'Atomic Habits',                             coverId: '12563711' },
  { olKey: 'OL7535509W',   title: "The Hitchhiker's Guide to the Galaxy",      coverId: '8482656' },
  { olKey: 'OL7353617W',   title: 'The Great Gatsby',                          coverId: '8432559' },
  { olKey: 'OL98227W',     title: 'To Kill a Mockingbird',                     coverId: '8810140' },
  { olKey: 'OL59634W',     title: 'Pride and Prejudice',                       coverId: '12749976' },
  { olKey: 'OL2630653W',   title: 'The Alchemist',                             coverId: '8714647' },
  { olKey: 'OL81805W',     title: 'Dune',                                      coverId: '10604490' },
  { olKey: 'OL18335804W',  title: 'Project Hail Mary',                         coverId: '12175785' },
  { olKey: 'OL5682950W',   title: 'The Midnight Library',                      coverId: '10527843' },
];
