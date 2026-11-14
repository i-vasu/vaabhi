#!/usr/bin/env bash
# Runs one task with a headless Claude Code agent whose model is DeepSeek (DeepSeek's Anthropic-compatible API).
# Used by the `deepseek` subagent (.claude/agents/deepseek.md); can also be run by hand:
#   .claude/deepseek/run.sh "task text"          (or pipe the task on stdin)
#
# Key: in a Claude cloud session the network proxy adds the saved DeepSeek key for api.deepseek.com, so nothing is
# needed. Locally, export DEEPSEEK_API_KEY first.
# Model: DEEPSEEK_MODEL (default deepseek-v4-pro; deepseek-flash is cheaper and faster).
# Time limit: DEEPSEEK_TIMEOUT seconds (default 570, so it ends before a 10-minute tool call does).
set -euo pipefail

task="${1:-}"
if [[ -z "$task" && ! -t 0 ]]; then
  task="$(cat)"
fi
if [[ -z "$task" ]]; then
  echo "usage: $0 \"task\"  (or pipe the task on stdin)" >&2
  exit 2
fi

cd "$(git rev-parse --show-toplevel)"

rules="You are a DeepSeek worker started by another agent in the Vaabhi repo. Follow CLAUDE.md. Do exactly the task, \
change only the files it needs, and match the style of neighbouring files. Never commit, push, switch branches or run \
test suites (typecheck and lint are fine). Finish with a short report: files changed and what changed in each, \
anything you could not do or were unsure about."

export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
export ANTHROPIC_AUTH_TOKEN="${DEEPSEEK_API_KEY:-proxy-injected}"
export ANTHROPIC_API_KEY=""
export ANTHROPIC_MODEL="${DEEPSEEK_MODEL:-deepseek-v4-pro}"
export ANTHROPIC_SMALL_FAST_MODEL="deepseek-flash"
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1

set +e
timeout "${DEEPSEEK_TIMEOUT:-570}" claude -p "$task" \
  --append-system-prompt "$rules" \
  --permission-mode acceptEdits \
  --allowedTools "Read" "Edit" "Write" "Glob" "Grep" \
    "Bash(git status*)" "Bash(git diff*)" "Bash(git log*)" \
    "Bash(pnpm typecheck*)" "Bash(pnpm lint*)" "Bash(pnpm --filter * typecheck*)" "Bash(pnpm --filter * lint*)" \
    "Bash(./mvnw -q spotless:apply*)" "Bash(./mvnw -q -DskipTests compile*)" \
  --disallowedTools "Bash(git push*)" "Bash(git commit*)" "Bash(git checkout*)" "Bash(git reset*)" \
  --output-format text 2>&1 | grep -v -e "doesn't recognize the model" -e "claude-code:unrecognized_model"
status=${PIPESTATUS[0]}
[[ $status -eq 124 ]] && echo "DeepSeek run stopped after ${DEEPSEEK_TIMEOUT:-570}s (timeout)." >&2
exit "$status"
