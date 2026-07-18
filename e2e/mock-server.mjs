import http from 'http';

const PORT = 9999;

// Search response — returned for GET /search.json
const STUB_SEARCH_RESPONSE = JSON.stringify({
  docs: [
    {
      key: '/works/OL82563W',
      title: 'The Hobbit',
      author_name: ['J.R.R. Tolkien'],
      first_publish_year: 1937,
      cover_i: 8406786,
      number_of_pages_median: 310,
    },
  ],
  numFound: 1,
});

// Work detail response — returned for GET /works/OL82563W.json
// OpenLibraryWorkResponse expects: key, title, description, number_of_pages, covers[], subjects[]
const STUB_WORK_RESPONSE = JSON.stringify({
  key: '/works/OL82563W',
  title: 'The Hobbit',
  description: 'In a hole in the ground there lived a hobbit.',
  number_of_pages: 310,
  covers: [8406786],
  subjects: ['Fantasy fiction'],
});

http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  // Route based on path: /works/*.json → work detail; everything else → search
  if (req.url && req.url.startsWith('/works/')) {
    res.end(STUB_WORK_RESPONSE);
  } else {
    res.end(STUB_SEARCH_RESPONSE);
  }
}).listen(PORT, () => {
  console.log(`[mock] Open Library stub listening on :${PORT}`);
});
