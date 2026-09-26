// MySQL has no array parameter type, so a Postgres `col = ANY($1::int[])` /
// `col = ANY($1::text[])` becomes a plain `IN (...)` with one placeholder per
// element. Returns the placeholder list to splice into the query text plus
// the flattened params to splice into the params array at that position.
function inClause(values) {
  if (!values.length) {
    // Caller's query should use "1=0" (or similar) instead of calling this
    // for an empty array - MySQL has no valid empty IN (...) syntax.
    throw new Error('inClause called with an empty array');
  }
  return { sql: values.map(() => '?').join(','), params: values };
}

module.exports = { inClause };
