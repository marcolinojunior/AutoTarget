import csv
import io
import os
from collections import defaultdict
from datetime import datetime
from statistics import median


def safe_int(value, default=0):
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return default


def safe_float(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def mean(values):
    if not values:
        return None
    return sum(values) / len(values)


def fmt_float(value, decimals=2):
    if value is None:
        return "n/a"
    return f"{value:.{decimals}f}"


def fmt_int(value):
    if value is None:
        return "n/a"
    return str(value)


def fmt_pct(value, decimals=2):
    if value is None:
        return "n/a"
    return f"{value:.{decimals}f}%"


def read_csv_dicts(path):
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        return [row for row in reader]


def read_rma_runtime(path):
    with open(path, "r", encoding="utf-8", newline="") as f:
        lines = [line.strip() for line in f if line.strip()]
    header_idx = None
    for i, line in enumerate(lines):
        lower = line.lower()
        if "," in line and lower.startswith("task,"):
            header_idx = i
            break
    if header_idx is None:
        return []
    buffer = io.StringIO("\n".join(lines[header_idx:]))
    reader = csv.DictReader(buffer)
    return [row for row in reader]


def main():
    root_dir = os.path.dirname(os.path.abspath(__file__))
    dados_dir = os.path.join(root_dir, "dados")
    out_path = os.path.join(root_dir, "relatorio_telemetria.md")

    if not os.path.isdir(dados_dir):
        print(f"Pasta de dados nao encontrada: {dados_dir}")
        return

    runs = sorted(
        [d for d in os.listdir(dados_dir) if os.path.isdir(os.path.join(dados_dir, d))]
    )
    if not runs:
        print("Nenhuma pasta de dados encontrada dentro de /dados")
        return

    coverage = {}

    recon_runs = {}
    recon_total_all = 0
    recon_success_all = 0
    recon_reductions_all = []
    recon_norma_all = []

    deadline_totals = defaultdict(lambda: {"exec": 0, "miss": 0})
    deadline_misses_by_run = {}

    rma_totals = defaultdict(
        lambda: {"exec": 0, "avg_sum": 0.0, "std_sum": 0.0, "max": None, "miss": 0}
    )

    energy_min_left = None
    energy_min_right = None
    energy_sum_left = 0.0
    energy_sum_right = 0.0
    energy_count = 0
    interval_under = []
    interval_over = []
    intervals_by_cannons = defaultdict(list)

    restoration_by_side = defaultdict(lambda: {"count": 0, "sum_restore": 0.0, "sum_after": 0.0, "max_kills": 0})

    variance_by_side = defaultdict(lambda: {"count": 0, "var_x": 0.0, "var_y": 0.0, "var_vx": 0.0, "var_vy": 0.0})

    utility_by_side = defaultdict(
        lambda: {"count": 0, "sum_u": 0.0, "add": 0, "remove": 0}
    )

    for run in runs:
        run_path = os.path.join(dados_dir, run)
        coverage[run] = {}

        recon_path = os.path.join(run_path, "telemetry_reconciliation.csv")
        recon_rows = read_csv_dicts(recon_path) if os.path.exists(recon_path) else []
        coverage[run]["reconciliation"] = len(recon_rows)
        if recon_rows:
            total = len(recon_rows)
            success_rows = [r for r in recon_rows if safe_float(r.get("Reducao_Pct")) > 0]
            success = len(success_rows)
            reductions = [safe_float(r.get("Reducao_Pct")) for r in success_rows]
            normas = [safe_float(r.get("NormA_yHat")) for r in success_rows]
            recon_runs[run] = {
                "total": total,
                "success": success,
                "success_rate": (success / total) * 100 if total else None,
                "mean_reduction": mean(reductions),
                "median_reduction": median(reductions) if reductions else None,
                "mean_norma": mean(normas),
            }
            recon_total_all += total
            recon_success_all += success
            recon_reductions_all.extend(reductions)
            recon_norma_all.extend(normas)

        miss_path = os.path.join(run_path, "deadline_misses_ALL_CORES.csv")
        miss_rows = read_csv_dicts(miss_path) if os.path.exists(miss_path) else []
        coverage[run]["deadline_misses"] = len(miss_rows)
        if miss_rows:
            total_miss_run = 0
            for row in miss_rows:
                task = row.get("TaskID") or row.get("Tarefa") or row.get("task")
                if not task:
                    continue
                execs = safe_int(row.get("Executions") or row.get("ExecucoesTotais") or row.get("Contagem"))
                misses = safe_int(row.get("DeadlineMisses") or row.get("deadline_misses"))
                deadline_totals[task]["exec"] += execs
                deadline_totals[task]["miss"] += misses
                total_miss_run += misses
            deadline_misses_by_run[run] = total_miss_run

        rma_path = os.path.join(run_path, "telemetry_rma_runtime.csv")
        rma_rows = read_rma_runtime(rma_path) if os.path.exists(rma_path) else []
        coverage[run]["rma_runtime"] = len(rma_rows)
        for row in rma_rows:
            task = row.get("task") or row.get("TaskID")
            if not task:
                continue
            execs = safe_int(row.get("executions"))
            max_ms = safe_float(row.get("max_ms") or row.get("MaxResponseMs"))
            avg_ms = safe_float(row.get("avg_ms") or row.get("AvgResponseMs"))
            std_ms = safe_float(row.get("stddev_ms") or row.get("StddevResponseMs"))
            misses = safe_int(row.get("deadline_misses") or row.get("DeadlineMisses"))

            rma_totals[task]["exec"] += execs
            rma_totals[task]["avg_sum"] += avg_ms * execs
            rma_totals[task]["std_sum"] += std_ms * execs
            rma_totals[task]["miss"] += misses
            if rma_totals[task]["max"] is None:
                rma_totals[task]["max"] = max_ms
            else:
                rma_totals[task]["max"] = max(rma_totals[task]["max"], max_ms)

        energy_path = os.path.join(run_path, "telemetry_energy_penalty.csv")
        energy_rows = read_csv_dicts(energy_path) if os.path.exists(energy_path) else []
        coverage[run]["energy_penalty"] = len(energy_rows)
        for row in energy_rows:
            energia_esq = safe_float(row.get("Energia_Esq") or row.get("Energia_Esquerda"))
            energia_dir = safe_float(row.get("Energia_Dir") or row.get("Energia_Direita"))
            canhoes_esq = safe_int(row.get("Canhoes_Esq") or row.get("CanhoesEsq"))
            canhoes_dir = safe_int(row.get("Canhoes_Dir") or row.get("CanhoesDir"))
            intervalo_esq = safe_float(row.get("Intervalo_Esq_ms") or row.get("IntervaloEsq") or row.get("Intervalo_Esq"))
            intervalo_dir = safe_float(row.get("Intervalo_Dir_ms") or row.get("IntervaloDir") or row.get("Intervalo_Dir"))

            energy_sum_left += energia_esq
            energy_sum_right += energia_dir
            energy_count += 1
            energy_min_left = energia_esq if energy_min_left is None else min(energy_min_left, energia_esq)
            energy_min_right = energia_dir if energy_min_right is None else min(energy_min_right, energia_dir)

            if canhoes_esq > 0 and intervalo_esq > 0:
                intervals_by_cannons[canhoes_esq].append(intervalo_esq)
                if canhoes_esq <= 5:
                    interval_under.append(intervalo_esq)
                else:
                    interval_over.append(intervalo_esq)

            if canhoes_dir > 0 and intervalo_dir > 0:
                intervals_by_cannons[canhoes_dir].append(intervalo_dir)
                if canhoes_dir <= 5:
                    interval_under.append(intervalo_dir)
                else:
                    interval_over.append(intervalo_dir)

        restoration_path = os.path.join(run_path, "telemetry_energy_restoration.csv")
        restoration_rows = read_csv_dicts(restoration_path) if os.path.exists(restoration_path) else []
        coverage[run]["energy_restoration"] = len(restoration_rows)
        for row in restoration_rows:
            lado = row.get("Lado") or "DESCONHECIDO"
            restaurada = safe_float(row.get("Energia_Restaurada"))
            energia_apos = safe_float(row.get("Energia_Apos"))
            abates = safe_int(row.get("Abates_Cumulativos"))
            data = restoration_by_side[lado]
            data["count"] += 1
            data["sum_restore"] += restaurada
            data["sum_after"] += energia_apos
            data["max_kills"] = max(data["max_kills"], abates)

        variance_path = os.path.join(run_path, "telemetry_sensor_variance.csv")
        variance_rows = read_csv_dicts(variance_path) if os.path.exists(variance_path) else []
        coverage[run]["sensor_variance"] = len(variance_rows)
        for row in variance_rows:
            lado = row.get("Lado") or "DESCONHECIDO"
            data = variance_by_side[lado]
            data["count"] += 1
            data["var_x"] += safe_float(row.get("Var_X"))
            data["var_y"] += safe_float(row.get("Var_Y"))
            data["var_vx"] += safe_float(row.get("Var_VelX"))
            data["var_vy"] += safe_float(row.get("Var_VelY"))

        utility_path = os.path.join(run_path, "telemetry_utility.csv")
        utility_rows = read_csv_dicts(utility_path) if os.path.exists(utility_path) else []
        coverage[run]["utility"] = len(utility_rows)
        for row in utility_rows:
            lado = row.get("Lado") or "DESCONHECIDO"
            u_atual = safe_float(row.get("U_Atual"))
            u_mais = safe_float(row.get("U_Mais1"))
            u_menos = safe_float(row.get("U_Menos1"))
            limiar = safe_float(row.get("Limiar_Ganho"))
            data = utility_by_side[lado]
            data["count"] += 1
            data["sum_u"] += u_atual
            if u_mais >= u_atual + limiar:
                data["add"] += 1
            if u_menos >= u_atual + limiar:
                data["remove"] += 1

    lines = []
    lines.append("# Relatorio de Analise de Telemetria - AutoTarget")
    lines.append("")
    lines.append(f"Gerado em: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append("")

    recon_success_rate_all = (recon_success_all / recon_total_all * 100) if recon_total_all else None
    mean_reduction_all = mean(recon_reductions_all)
    mean_norma_all = mean(recon_norma_all)
    total_deadline_misses = sum(v["miss"] for v in deadline_totals.values())

    lines.append("## Resumo Executivo")
    lines.append(f"- Runs analisadas: {len(runs)} ({', '.join(runs)})")
    lines.append(f"- Reconcilicao: {fmt_int(recon_success_all)}/{fmt_int(recon_total_all)} sucesso ({fmt_pct(recon_success_rate_all)})")
    lines.append(f"- Reducao media do erro (apenas sucesso): {fmt_float(mean_reduction_all, 2)}%")
    lines.append(f"- Norma media ||A*yHat|| (apenas sucesso): {fmt_float(mean_norma_all, 6)}")
    lines.append(f"- Deadline misses totais (ALL_CORES): {fmt_int(total_deadline_misses)}")
    lines.append("")

    lines.append("## Cobertura dos Dados")
    lines.append("| Run | reconciliation | deadline_misses | rma_runtime | energy_penalty | energy_restoration | sensor_variance | utility |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for run in runs:
        cov = coverage[run]
        lines.append(
            f"| {run} | {cov.get('reconciliation', 0)} | {cov.get('deadline_misses', 0)} | {cov.get('rma_runtime', 0)} | "
            f"{cov.get('energy_penalty', 0)} | {cov.get('energy_restoration', 0)} | {cov.get('sensor_variance', 0)} | {cov.get('utility', 0)} |"
        )
    lines.append("")

    lines.append("## Reconcilicao (telemetry_reconciliation.csv)")
    lines.append("| Run | Total | Sucesso | Sucesso% | Reducao media (%) | Reducao mediana (%) | Norma media |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- |")
    for run in runs:
        stats = recon_runs.get(run)
        if not stats:
            lines.append(f"| {run} | 0 | 0 | n/a | n/a | n/a | n/a |")
            continue
        lines.append(
            f"| {run} | {stats['total']} | {stats['success']} | {fmt_pct(stats['success_rate'])} | "
            f"{fmt_float(stats['mean_reduction'], 2)} | {fmt_float(stats['median_reduction'], 2)} | {fmt_float(stats['mean_norma'], 6)} |"
        )
    lines.append(
        f"| TOTAL | {recon_total_all} | {recon_success_all} | {fmt_pct(recon_success_rate_all)} | "
        f"{fmt_float(mean_reduction_all, 2)} | {fmt_float(median(recon_reductions_all) if recon_reductions_all else None, 2)} | "
        f"{fmt_float(mean_norma_all, 6)} |"
    )
    lines.append("")

    lines.append("## Escalonamento e Deadline Misses (deadline_misses_ALL_CORES.csv)")
    if deadline_totals:
        sorted_tasks = sorted(
            deadline_totals.items(),
            key=lambda kv: kv[1]["miss"],
            reverse=True,
        )
        lines.append("| Tarefa | Execucoes | Misses | Miss rate |")
        lines.append("| --- | --- | --- | --- |")
        for task, data in sorted_tasks[:10]:
            execs = data["exec"]
            misses = data["miss"]
            rate = (misses / execs * 100) if execs else None
            lines.append(f"| {task} | {execs} | {misses} | {fmt_pct(rate)} |")
    else:
        lines.append("Sem dados de deadline misses.")
    lines.append("")

    lines.append("## RMA Runtime (telemetry_rma_runtime.csv)")
    if rma_totals:
        items = []
        for task, data in rma_totals.items():
            execs = data["exec"]
            avg_ms = (data["avg_sum"] / execs) if execs else None
            std_ms = (data["std_sum"] / execs) if execs else None
            items.append((task, execs, data["max"], avg_ms, std_ms, data["miss"]))
        items.sort(key=lambda x: (x[2] if x[2] is not None else 0), reverse=True)
        lines.append("| Tarefa | Execucoes | Max ms | Avg ms | Stddev ms | Misses |")
        lines.append("| --- | --- | --- | --- | --- | --- |")
        for task, execs, max_ms, avg_ms, std_ms, misses in items:
            lines.append(
                f"| {task} | {execs} | {fmt_float(max_ms, 3)} | {fmt_float(avg_ms, 3)} | {fmt_float(std_ms, 3)} | {misses} |"
            )
    else:
        lines.append("Sem dados de runtime.")
    lines.append("")

    lines.append("## Energia e Penalidade (telemetry_energy_penalty.csv)")
    energy_avg_left = (energy_sum_left / energy_count) if energy_count else None
    energy_avg_right = (energy_sum_right / energy_count) if energy_count else None
    lines.append(
        f"- Energia minima: esquerda {fmt_float(energy_min_left, 2)} | direita {fmt_float(energy_min_right, 2)}"
    )
    lines.append(
        f"- Energia media: esquerda {fmt_float(energy_avg_left, 2)} | direita {fmt_float(energy_avg_right, 2)}"
    )
    lines.append(
        f"- Intervalo medio com N<=5: {fmt_float(mean(interval_under), 1)} ms | N>5: {fmt_float(mean(interval_over), 1)} ms"
    )
    if intervals_by_cannons:
        lines.append("| Canhoes | Intervalo medio ms | Amostras |")
        lines.append("| --- | --- | --- |")
        for n in sorted(intervals_by_cannons.keys()):
            vals = intervals_by_cannons[n]
            lines.append(f"| {n} | {fmt_float(mean(vals), 1)} | {len(vals)} |")
    lines.append("")

    lines.append("## Energia Restoration (telemetry_energy_restoration.csv)")
    if restoration_by_side:
        lines.append("| Lado | Eventos | Energia restaurada media | Energia apos media | Abates max |")
        lines.append("| --- | --- | --- | --- | --- |")
        for lado, data in restoration_by_side.items():
            count = data["count"]
            avg_restore = (data["sum_restore"] / count) if count else None
            avg_after = (data["sum_after"] / count) if count else None
            lines.append(
                f"| {lado} | {count} | {fmt_float(avg_restore, 2)} | {fmt_float(avg_after, 2)} | {data['max_kills']} |"
            )
    else:
        lines.append("Sem dados de restauracao.")
    lines.append("")

    lines.append("## Variancia de Sensores (telemetry_sensor_variance.csv)")
    if variance_by_side:
        lines.append("| Lado | Amostras | Var_X media | Var_Y media | Var_VelX media | Var_VelY media |")
        lines.append("| --- | --- | --- | --- | --- | --- |")
        for lado, data in variance_by_side.items():
            count = data["count"]
            lines.append(
                f"| {lado} | {count} | {fmt_float(data['var_x'] / count, 3) if count else 'n/a'} | "
                f"{fmt_float(data['var_y'] / count, 3) if count else 'n/a'} | "
                f"{fmt_float(data['var_vx'] / count, 3) if count else 'n/a'} | "
                f"{fmt_float(data['var_vy'] / count, 3) if count else 'n/a'} |"
            )
    else:
        lines.append("Sem dados de variancia.")
    lines.append("")

    lines.append("## Funcao de Utilidade (telemetry_utility.csv)")
    if utility_by_side:
        lines.append("| Lado | Amostras | U_Atual media | Sinal adicionar | Sinal remover |")
        lines.append("| --- | --- | --- | --- | --- |")
        for lado, data in utility_by_side.items():
            count = data["count"]
            avg_u = (data["sum_u"] / count) if count else None
            lines.append(
                f"| {lado} | {count} | {fmt_float(avg_u, 3)} | {data['add']} | {data['remove']} |"
            )
    else:
        lines.append("Sem dados de utilidade.")
    lines.append("")

    with open(out_path, "w", encoding="utf-8", newline="") as f:
        f.write("\n".join(lines))

    print(f"Relatorio gerado em: {out_path}")


if __name__ == "__main__":
    main()
