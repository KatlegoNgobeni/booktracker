import http from 'http';

const PORT = 9999;

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

http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(STUB_SEARCH_RESPONSE);
}).listen(PORT, () => {
  console.log(`[mock] Open Library stub listening on :${PORT}`);
});
