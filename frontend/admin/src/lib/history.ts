/**
 * The pages visited in this tab, so a page's back button can return to where the person came from (More, search,
 * a list) instead of always jumping to a fixed parent. Browser back pops it the same way.
 */
const visited: string[] = [];

/** Called by the app shell whenever the page changes. */
export function recordNavigation(path: string) {
  if (visited.at(-1) === path) return;
  if (visited.at(-2) === path) visited.pop();
  else visited.push(path);
}

/** True when there is an earlier page in this app to go back to. */
export function canGoBack(): boolean {
  return visited.length > 1;
}

/** For tests. */
export function resetNavigation() {
  visited.length = 0;
}
