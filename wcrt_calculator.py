"""
WCRT (Worst-Case Response Time) Calculator for AutoTarget RMA Analysis.
Computes R_i for each task using fixed-priority iterative analysis.
"""

# Task definitions from RMAAnalysis.java, ordered by RM priority (shortest period first)
# (name, period_ms, wcet_ms, deadline_ms, jitter_ms)
tasks = [
    ("T1-Physics",       16,    4,   16,   1),
    ("T2-Projetil",      16,    2,   16,   1),
    ("T3-Alvo",          30,    3,   30,   2),
    ("T4-Render",        33,   12,   33,   3),
    ("T7-Sensor",      1000,   10, 1000,   8),
    ("T5-GameTimer",   1000,    1, 1000,  10),
    ("T6-Canhao",      1500,    5, 1500,  20),
    ("T8-Reconciliacao",10000, 500,10000,  30),
]

import math

def compute_wcrt(task_index, tasks):
    """Compute WCRT for task at task_index using iterative fixed-point."""
    ci = tasks[task_index][2]  # WCET
    di = tasks[task_index][3]  # Deadline
    r = ci
    
    for iteration in range(1000):
        r_new = ci
        for j in range(task_index):
            pj = tasks[j][1]  # Period of higher-priority task
            cj = tasks[j][2]  # WCET of higher-priority task
            r_new += math.ceil(r / pj) * cj
        
        if r_new == r:
            return r, True  # Converged
        if r_new > di:
            return r_new, False  # Deadline miss
        r = r_new
    
    return r, False  # Did not converge

# === Compute and write results to file ===
with open("wcrt_out.txt", "w", encoding="utf-8") as f:
    f.write("=" * 90 + "\n")
    f.write(f"{'Tarefa':<20} {'P (ms)':>8} {'C (ms)':>8} {'D (ms)':>8} {'R_i (ms)':>10} {'Folga':>10} {'Status':>12}\n")
    f.write("=" * 90 + "\n")

    total_util = 0
    all_schedulable = True

    for i, task in enumerate(tasks):
        name, period, wcet, deadline, jitter = task
        ri, schedulable = compute_wcrt(i, tasks)
        utilization = wcet / period
        total_util += utilization
        slack = deadline - ri
        status = "✅ OK" if schedulable else "❌ MISS"
        if not schedulable:
            all_schedulable = False
        
        f.write(f"{name:<20} {period:>8} {wcet:>8} {deadline:>8} {ri:>10.1f} {slack:>10.1f} {status:>12}\n")

    f.write("=" * 90 + "\n")
    f.write(f"{'Utilização Total':>40}: {total_util:.4f} ({total_util*100:.1f}%)\n")
    liu_bound = len(tasks) * (2**(1/len(tasks)) - 1)
    f.write(f"{'Limite Liu & Layland':>40}: {liu_bound:.4f} ({liu_bound*100:.1f}%)\n")
    f.write(f"{'Teste Liu & Layland':>40}: {'PASSA' if total_util <= liu_bound else 'INCONCLUSIVO'}\n")
    f.write(f"{'Teste WCRT (exato)':>40}: {'ESCALONÁVEL' if all_schedulable else 'NÃO-ESCALONÁVEL'}\n\n")

    # With fewer cores, concurrent tasks compete more. 
    # Pessimistic model: multiply WCET of concurrent tasks by scaling factor
    core_configs = [
        ("Todos os núcleos (8)", 1.0),
        ("4 núcleos",            1.3),   # ~30% overhead from contention
        ("2 núcleos",            2.0),   # Double contention
        ("1 núcleo",             4.0),   # All tasks serialize
    ]

    # === Simulate core restriction scenarios ===
    f.write("\n" + "=" * 90 + "\n")
    f.write("SIMULAÇÃO: Impacto da restrição de núcleos no WCRT\n")
    f.write("(Modelo pessimista: WCET escalado por fator 1/cores para tarefas paralelas)\n")
    f.write("=" * 90 + "\n")

    for config_name, scale_factor in core_configs:
        f.write(f"\n--- {config_name} (fator de escala WCET: {scale_factor}x) ---\n")
        scaled_tasks = []
        for name, period, wcet, deadline, jitter in tasks:
            scaled_wcet = wcet * scale_factor
            scaled_tasks.append((name, period, scaled_wcet, deadline, jitter))
        
        total_util_scaled = sum(t[2]/t[1] for t in scaled_tasks)
        all_ok = True
        
        f.write(f"  {'Tarefa':<20} {'C_esc':>8} {'R_i':>10} {'D_i':>8} {'Status':>8}\n")
        for i, task in enumerate(scaled_tasks):
            ri, schedulable = compute_wcrt(i, scaled_tasks)
            status = "✅" if schedulable else "❌"
            if not schedulable:
                all_ok = False
            f.write(f"  {task[0]:<20} {task[2]:>8.1f} {ri:>10.1f} {task[3]:>8} {status:>8}\n")
        
        f.write(f"  Utilização: {total_util_scaled:.3f} | Escalonável: {'SIM' if all_ok else 'NÃO'}\n")
