# TodoMVC SSR Example

An example of TodoMVC using:
1. `town.lilac/dom` for rendering HTML on the server
3. htmx for handling updates to the page

It also demonstrates using `dom`'s streaming SSR with async fallbacks when
combined with manifold. The initial page load has a `(sleep 1000)` to act as if
it were loading some initial data.
