/**
 * trending.ts — Curated static list of trending books for the discovery surface
 *
 * CURATED_TRENDING is a static data constant — no API calls. Cover IDs are numeric
 * Open Library cover IDs fetched from the OL Works + Search APIs (stable per-edition).
 *
 * T-15-07: Cover IDs are public (openlibrary.org covers endpoint is unauthenticated);
 * no sensitive data is exposed here.
 */

export interface TrendingBook {
  olKey: string;     // short form, no /works/ prefix
  title: string;
  coverId: number;   // numeric OL cover ID for /b/id/{coverId}-M.jpg
}

export const CURATED_TRENDING: TrendingBook[] = [
  { olKey: 'OL82586W',    title: "Harry Potter and the Philosopher's Stone", coverId: 15158660 },
  { olKey: 'OL17930368W', title: 'Atomic Habits',                            coverId: 12539702 },
  { olKey: 'OL7535509W',  title: "The Hitchhiker's Guide to the Galaxy",     coverId: 12986869 },
  { olKey: 'OL7353617W',  title: 'The Great Gatsby',                         coverId: 10590366 },
  { olKey: 'OL98227W',    title: 'To Kill a Mockingbird',                    coverId: 382628   },
  { olKey: 'OL59634W',    title: 'Pride and Prejudice',                      coverId: 14348537 },
  { olKey: 'OL2630653W',  title: 'The Alchemist',                            coverId: 7463992  },
  { olKey: 'OL81805W',    title: 'Nineteen Eighty-Four',                     coverId: 233641   },
  { olKey: 'OL18335804W', title: 'Project Hail Mary',                        coverId: 11684511 },
  { olKey: 'OL5682950W',  title: 'The Midnight Library',                     coverId: 317807   },
];
