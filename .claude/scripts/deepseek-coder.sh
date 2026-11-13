#!/usr/bin/env bash
# Runs a headless Claude Code session against DeepSeek's Anthropic-compatible API to implement
# one coding spec. Usage: .claude/scripts/deepseek-coder.sh <spec-file>   (or pipe the spec on stdin)
# Needs DEEPSEEK_API_KEY in the environment. Edit-only: no shell, no web, no MCP servers.
set -euo pipefail

if [ -z "${DEEPSEEK_API_KEY:-}" ] && command -v powershell.exe >/dev/null; then
  # setx only reaches new processes; read the user variable straight from the registry.
  DEEPSEEK_API_KEY="$(powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY','User')" | tr -d '\r')"
fi
if [ -z "${DEEPSEEK_API_KEY:-}" ]; then
  echo "DEEPSEEK_API_KEY is not set" >&2
  exit 2
fi

repo="$(cd "$(dirname "$0")/../.." && pwd)"
if [ $# -ge 1 ]; then spec="$(cat "$1")"; else spec="$(cat)"; fi

# Coder rules: the body of .claude/agents/coder.md without its frontmatter.
rules="$(awk 'BEGIN{n=0} /^---$/{n++; next} n>=2' "$repo/.claude/agents/coder.md")"

cd "$repo"
unset ANTHROPIC_API_KEY
export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
export ANTHROPIC_AUTH_TOKEN="$DEEPSEEK_API_KEY"
export ANTHROPIC_MODEL="${DEEPSEEK_MODEL:-deepseek-flash[1m]}"
export ANTHROPIC_DEFAULT_HAIKU_MODEL="deepseek-flash"
export ANTHROPIC_DEFAULT_SONNET_MODEL="deepseek-flash"
export CLAUDE_CODE_SUBAGENT_MODEL="deepseek-flash"
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1

exec claude -p "$spec" \
  --append-system-prompt "$rules" \
  --permission-mode acceptEdits \
  --strict-mcp-config \
  --allowedTools "Read" "Edit" "Write" "Glob" "Grep" \
  --disallowedTools "Bash" "PowerShell" "WebFetch" "WebSearch" "Agent" \
    "Read(**/.env*)" "Read(**/hs_err_pid*)" "Read(**/*.sql.bak)" "Read(vaabhi-before-rupees.sql)" \
  --output-format text < /dev/null
