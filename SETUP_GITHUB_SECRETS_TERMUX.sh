#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
REPO="efotball2024hdvhb-oss/gpt"
ROOT="$(cd "$(dirname "$0")" && pwd)"
source "$ROOT/.private/signing.env"
source "$ROOT/.private/api.env"
KEY_B64="$(base64 -w 0 "$ROOT/.private/mindgpt-release.jks")"
printf '%s' "$KEY_B64" | gh secret set MINDGPT_SIGNING_KEY_B64 --repo "$REPO"
printf '%s' "$MINDGPT_KEYSTORE_PASSWORD" | gh secret set MINDGPT_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$MINDGPT_KEY_ALIAS" | gh secret set MINDGPT_KEY_ALIAS --repo "$REPO"
printf '%s' "$MINDGPT_KEY_PASSWORD" | gh secret set MINDGPT_KEY_PASSWORD --repo "$REPO"
printf '%s' "$CODECRAFT_API_KEY" | gh secret set CODECRAFT_API_KEY --repo "$REPO"
echo "GitHub secrets configured. .private/ stays ignored and must never be committed."
