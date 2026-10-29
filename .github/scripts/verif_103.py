"""Vérification ponctuelle des preuves JUnit PostgreSQL de la PR #103 (run 40603193328).

Lit uniquement des données : métadonnées de l'API GitHub, journaux des jobs et rapports
JUnit XML. N'exécute rien qui provienne des artefacts. Sortie non nulle si une preuve
manque ou est incohérente.

Usage : python3 -I verif_103.py <dossier-meta> <dossier-artefacts>
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

RUN_ID = 40603193328
HEAD_SHA = "8151209f4536e02bd9e552050a114a3db099b962"

GROUPS = {
    "iam-boot": {
        "artifact_id": 12779392444,
        "artifact_name": "takibo-test-results-iam-boot",
        "digest": "sha256:4cc435bda462973be7c14640a698c6472ea712f685b34959d83ee35fe8685365",
        "job_id": 122536848562,
        "module": "takibo-iam-boot",
        "package": "com.takibo.iamboot.",
        "task": ":takibo-iam-boot:test",
        "total": 246,
        "skipped": {"com.takibo.iamboot.database.DatabaseMigrationTest":
                    ["given_configured_database_check_environment_when_flyway_migrates_then_all_migrations_apply"]},
        "postgres": {
            "com.takibo.iamboot.tas.AuthorizationRetentionIntegrationTest": 10,
            "com.takibo.iamboot.tas.AuthorizationSaveContractBaselineIntegrationTest": 12,
            "com.takibo.iamboot.tas.BoundaryMembershipPostgresIntegrationTest": 8,
            "com.takibo.iamboot.tas.ClientCredentialsBaselineIntegrationTest": 11,
            "com.takibo.iamboot.tas.HumanLoginBaselineIntegrationTest": 9,
            "com.takibo.iamboot.tas.JwkSetEndpointIntegrationTest": 3,
            "com.takibo.iamboot.tas.OAuth2AuthorizationBoundariesConstraintsIntegrationTest": 17,
            "com.takibo.iamboot.tas.OAuth2AuthorizationRestartAcceptanceTest": 1,
            "com.takibo.iamboot.tas.OAuth2ClientBoundaryIntegrationTest": 7,
            "com.takibo.iamboot.tas.OAuth2ClientSnapshotConsistencyIntegrationTest": 1,
            "com.takibo.iamboot.tas.ScopedForeignKeySetNullIntegrationTest": 8,
            "com.takibo.iamboot.tas.SigningKeyBootstrapIntegrationTest": 5,
            "com.takibo.iamboot.tas.SigningKeyRepositoryIntegrationTest": 15,
            "com.takibo.iamboot.tas.SigningKeyRestartAcceptanceTest": 4,
            "com.takibo.iamboot.tas.SigningKeyRotationIntegrationTest": 7,
            "com.takibo.iamboot.tas.SigningKeyScopeConstraintsIntegrationTest": 8,
        },
    },
    "outbox/jpa": {
        "artifact_id": 12780555273,
        "artifact_name": "takibo-test-results-outbox",
        "digest": "sha256:0eb9ef9ec97fdefa71d6565389b38ef9f407c85e1e8411e0b04feac1e5fbabc9",
        "job_id": 122536848533,
        "module": "takibo-outbox/jpa",
        "package": "com.takibo.outbox.jpa.",
        "task": ":takibo-outbox:jpa:test",
        "total": 27,
        "skipped": {},
        "postgres": {
            "com.takibo.outbox.jpa.OutboxJpaClaimIntegrationTest": 1,
            "com.takibo.outbox.jpa.OutboxJpaRepositoryIntegrationTest": 14,
        },
    },
}
EXPECTED_POSTGRES_CLASSES = 18
EXPECTED_POSTGRES_TESTS = 141

LOG_TS = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z) (.*)$")

failures = []
summary = []


def fail(message):
    failures.append(message)
    print(f"::error::{message}")


def check(condition, message):
    if not condition:
        fail(message)
    return condition


def parse_time(value):
    value = value.strip().rstrip("Z")
    if "." in value:
        head, frac = value.split(".", 1)
        value = f"{head}.{frac[:6]}"
    return datetime.fromisoformat(value).replace(tzinfo=timezone.utc)


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def log_lines(path):
    lines = []
    with open(path, encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            match = LOG_TS.match(raw.rstrip("\n").lstrip("﻿"))
            if match:
                lines.append((parse_time(match.group(1)), match.group(2)))
    return lines


def verify_artifact_metadata(name, spec, artifacts, run):
    matches = [a for a in artifacts if a["id"] == spec["artifact_id"]]
    if not check(len(matches) == 1, f"{name} : artefact {spec['artifact_id']} absent du run {RUN_ID}"):
        return
    artifact = matches[0]
    check(artifact["name"] == spec["artifact_name"], f"{name} : nom d'artefact inattendu {artifact['name']}")
    check(artifact["digest"] == spec["digest"], f"{name} : empreinte {artifact['digest']} différente de l'attendue")
    check(not artifact["expired"], f"{name} : artefact expiré")
    check(artifact["workflow_run"]["id"] == RUN_ID, f"{name} : artefact d'un autre run")
    check(artifact["workflow_run"]["head_sha"] == HEAD_SHA, f"{name} : artefact d'un autre commit")
    summary.append(f"| {name} | `{artifact['name']}` | {artifact['id']} | `{artifact['digest']}` | `{artifact['workflow_run']['head_sha'][:7]}` |")


def verify_job(name, spec, meta):
    job = load_json(meta / f"job-{spec['job_id']}.json")
    check(job["run_id"] == RUN_ID, f"{name} : job {spec['job_id']} hors du run {RUN_ID}")
    check(job["head_sha"] == HEAD_SHA, f"{name} : job sur un autre commit")
    check(job["conclusion"] == "success", f"{name} : job conclu {job['conclusion']}")
    lines = log_lines(meta / f"log-{spec['job_id']}.txt")

    task_line = re.compile(rf"^> Task {re.escape(spec['task'])}(\s|$)")
    task_lines = [(t, text) for t, text in lines if task_line.match(text.strip())]
    check(len(task_lines) == 1, f"{name} : ligne de tâche {spec['task']} introuvable ou multiple dans le journal")
    for _, text in task_lines:
        check(text.strip() == f"> Task {spec['task']}",
              f"{name} : tâche non exécutée ({text.strip()}) — cache ou à jour")
    totals = [text for _, text in lines if "actionable tasks:" in text]
    check(len(totals) == 1, f"{name} : bilan Gradle des tâches introuvable")
    for text in totals:
        match = re.search(r"(\d+) actionable tasks?: (\d+) executed$", text.strip())
        check(match is not None and match.group(1) == match.group(2),
              f"{name} : tâches non toutes exécutées ({text.strip()})")
    uploads = [text for _, text in lines if "SHA256 digest of uploaded artifact zip is" in text]
    check(len(uploads) == 1 and uploads[0].strip().endswith(spec["digest"].split(":", 1)[1]),
          f"{name} : empreinte publiée dans le journal différente des métadonnées")

    # La JVM de test démarre avant que Gradle n'affiche l'en-tête de la tâche : la fenêtre
    # commence au démarrage du job et se termine au BUILD SUCCESSFUL de ce job.
    start = parse_time(job["started_at"])
    ends = [t for t, text in lines if "BUILD SUCCESSFUL" in text]
    end = ends[0] if ends else parse_time(job["completed_at"])
    return start, end, totals[0].strip() if totals else "?"


def verify_reports(name, spec, root, window):
    module = spec["module"]
    # upload-artifact retire la racine commune des chemins : la sélection se fait par le
    # paquetage des classes de test, propre à chaque module (vérifié dans le dépôt).
    reports = sorted(p for p in root.rglob("TEST-*.xml")
                     if "test-results/test/" in p.as_posix() and p.name.startswith("TEST-" + spec["package"]))
    names = [p.name for p in reports]
    check(len(names) == len(set(names)), f"{name} : rapports en double {sorted(names)}")
    check(reports, f"{name} : aucun rapport JUnit pour {module}")
    start, end = window
    total = skipped_total = failed = errored = 0
    seen_pg = {}
    skipped_seen = {}
    out_of_window = []
    for report in reports:
        suite = ET.parse(report).getroot()
        cls = suite.get("name")
        tests = int(suite.get("tests"))
        skipped = int(suite.get("skipped"))
        total += tests
        skipped_total += skipped
        failed += int(suite.get("failures"))
        errored += int(suite.get("errors"))
        stamp = parse_time(suite.get("timestamp"))
        if not (start <= stamp <= end):
            out_of_window.append(f"{cls} ({suite.get('timestamp')})")
        for case in suite.findall("testcase"):
            if case.find("skipped") is not None:
                skipped_seen.setdefault(cls, []).append(case.get("name").removesuffix("()"))
        if cls in spec["postgres"]:
            cases = suite.findall("testcase")
            executed = [c for c in cases if c.find("skipped") is None
                        and c.find("failure") is None and c.find("error") is None]
            seen_pg[cls] = (tests, skipped, len(executed), suite.get("timestamp"), suite.get("time"))

    check(total == spec["total"], f"{name} : {total} tests au lieu de {spec['total']}")
    check(failed == 0 and errored == 0, f"{name} : {failed} échec(s), {errored} erreur(s)")
    check(skipped_seen == spec["skipped"],
          f"{name} : tests ignorés {skipped_seen} au lieu de {spec['skipped']}")
    check(not out_of_window, f"{name} : rapports hors de la fenêtre d'exécution : {out_of_window}")

    rows = []
    for cls, expected in spec["postgres"].items():
        if not check(cls in seen_pg, f"{name} : classe PostgreSQL absente : {cls}"):
            rows.append(f"| `{cls.rsplit('.', 1)[1]}` | {expected} | ABSENTE | | | |")
            continue
        tests, skipped, executed, stamp, duration = seen_pg[cls]
        check(tests == expected and skipped == 0 and executed == expected,
              f"{name} : {cls} : {tests} tests, {skipped} ignorés, {executed} exécutés (attendu {expected})")
        rows.append(f"| `{cls.rsplit('.', 1)[1]}` | {expected} | {tests} | {skipped} | {executed} | {stamp} ({duration} s) |")
    executed_pg = sum(v[2] for v in seen_pg.values())
    return {"total": total, "skipped": skipped_total, "failed": failed, "errors": errored,
            "skipped_detail": skipped_seen, "rows": rows, "pg_classes": len(seen_pg), "pg_executed": executed_pg,
            "reports": len(reports)}


def main():
    meta, artifacts_root = Path(sys.argv[1]), Path(sys.argv[2])
    run = load_json(meta / "run.json")
    check(run["id"] == RUN_ID, "run inattendu")
    check(run["head_sha"] == HEAD_SHA, f"run sur {run['head_sha']} au lieu de {HEAD_SHA}")
    check(run["conclusion"] == "success", f"run conclu {run['conclusion']}")
    artifacts = load_json(meta / "artifacts.json")["artifacts"]

    summary.append("## Vérification ponctuelle — PR #103, run 40603193328")
    summary.append(f"Run `{run['id']}` · commit `{run['head_sha']}` · conclusion `{run['conclusion']}`\n")
    summary.append("### Artefacts")
    summary.append("| Groupe | Nom | Id | Empreinte | Commit |\n|---|---|---|---|---|")
    for name, spec in GROUPS.items():
        verify_artifact_metadata(name, spec, artifacts, run)

    pg_classes = pg_tests = 0
    for name, spec in GROUPS.items():
        start, end, tasks = verify_job(name, spec, meta)
        result = verify_reports(name, spec, artifacts_root / name.split("/")[0], (start, end))
        pg_classes += result["pg_classes"]
        pg_tests += result["pg_executed"]
        summary.append(f"\n### {name}")
        summary.append(f"- Fenêtre du job : {start:%Y-%m-%d %H:%M:%S} → {end:%H:%M:%S} UTC · `{tasks}`")
        summary.append(f"- {result['reports']} rapports · **{result['total']} tests**, {result['skipped']} ignoré(s), "
                       f"{result['failed']} échec(s), {result['errors']} erreur(s)")
        summary.append(f"- Ignorés : {result['skipped_detail'] or 'aucun'}")
        summary.append("\n| Classe PostgreSQL | Attendu | Tests | Ignorés | Exécutés | Début (durée) |\n|---|---|---|---|---|---|")
        summary.extend(result["rows"])

    check(pg_classes == EXPECTED_POSTGRES_CLASSES, f"{pg_classes} classes PostgreSQL au lieu de {EXPECTED_POSTGRES_CLASSES}")
    check(pg_tests == EXPECTED_POSTGRES_TESTS, f"{pg_tests} tests PostgreSQL exécutés au lieu de {EXPECTED_POSTGRES_TESTS}")
    summary.append(f"\n### Bilan\n- Classes PostgreSQL : **{pg_classes}/{EXPECTED_POSTGRES_CLASSES}** · "
                   f"tests exécutés : **{pg_tests}/{EXPECTED_POSTGRES_TESTS}**")
    if failures:
        summary.append("- **PREUVE INCOMPLÈTE OU INCOHÉRENTE :**")
        summary.extend(f"  - {f}" for f in failures)
    else:
        summary.append("- **PREUVE ÉTABLIE** : tous les critères sont satisfaits.")

    text = "\n".join(summary)
    print(text)
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as handle:
            handle.write(text + "\n")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
