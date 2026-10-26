#!/usr/bin/env bash
# Source from any directory: source scripts/dev-env.sh
# Parse literal KEY=value entries; do not execute .env as shell code.
quest_env_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
if [ ! -f "$quest_env_root/.env" ]; then
  echo 'Copy .env.example to .env and set local credentials first.' >&2
  return 1
fi
while IFS= read -r quest_env_line || [[ -n "$quest_env_line" ]]; do
  quest_env_line=${quest_env_line%$'\r'}
  [[ -z "$quest_env_line" || "$quest_env_line" == \#* ]] && continue
  if [[ "$quest_env_line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
    quest_env_key=${BASH_REMATCH[1]}
    quest_env_value=${BASH_REMATCH[2]}
    if [[ "$quest_env_value" == \"*\" || "$quest_env_value" == \'*\' ]]; then
      quest_env_value=${quest_env_value:1:${#quest_env_value}-2}
    fi
    export "$quest_env_key=$quest_env_value"
  else
    echo 'Invalid .env entry; expected KEY=value.' >&2
    return 1
  fi
done < "$quest_env_root/.env"
unset quest_env_root quest_env_line quest_env_key quest_env_value
