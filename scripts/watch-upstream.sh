#!/usr/bin/env bash
# Watches Switchly upstream (GitLab) test/main branches for new commits.
# State lives outside the repo so `git status` stays clean.
# Notifies via notify-send (desktop) and always appends to the log.
set -u

URL="https://gitlab.com/Saltyy/switchly-public.git"
STATE_DIR="${HOME}/.local/share/switchly-upstream-watch"
STATE_FILE="${STATE_DIR}/refs.state"
LOG_FILE="${STATE_DIR}/watch.log"

mkdir -p "${STATE_DIR}"
touch "${STATE_FILE}" "${LOG_FILE}"

log() {
    echo "$(date '+%F %T') $*" >> "${LOG_FILE}"
}

notify() {
    local title="$1" body="$2"
    log "ALERT ${title} -- ${body}"
    # notify-send is broken on this machine (libnotify mismatch), so talk to
    # the notification daemon directly. Failures must never fail the check.
    if command -v gdbus >/dev/null 2>&1; then
        gdbus call --session \
            --dest org.freedesktop.Notifications \
            --object-path /org/freedesktop/Notifications \
            --method org.freedesktop.Notifications.Notify \
            "Switchly" 0 "" "${title}" "${body}" "[]" "{}" 15000 >/dev/null 2>&1 || true
    fi
}

get_sha() {
    git ls-remote "${URL}" "refs/heads/$1" 2>/dev/null | awk '{print $1}'
}

read_state() {
    local ref="$1"
    grep -E "^${ref} " "${STATE_FILE}" 2>/dev/null | awk '{print $2}' | tail -n 1
}

write_state() {
    local ref="$1" sha="$2" tmp
    tmp="$(mktemp)"
    grep -Ev "^${ref} " "${STATE_FILE}" 2>/dev/null > "${tmp}" || true
    echo "${ref} ${sha}" >> "${tmp}"
    mv "${tmp}" "${STATE_FILE}"
}

for ref in main test; do
    new_sha="$(get_sha "${ref}")"
    if [[ -z "${new_sha}" ]]; then
        log "WARN could not resolve upstream/${ref} (network?)"
        continue
    fi
    old_sha="$(read_state "${ref}")"
    if [[ -z "${old_sha}" ]]; then
        # First run (or state wiped): baseline silently.
        write_state "${ref}" "${new_sha}"
        log "BASELINE upstream/${ref} = ${new_sha}"
        continue
    fi
    if [[ "${old_sha}" != "${new_sha}" ]]; then
        write_state "${ref}" "${new_sha}"
        notify \
            "Switchly upstream/${ref} moved" \
            "${old_sha:0:8}..${new_sha:0:8} — check for merge work"
    fi
done
