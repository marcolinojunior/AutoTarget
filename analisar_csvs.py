import os
import csv
from collections import defaultdict

base_dir = r"C:\Users\marco\OneDrive\Documentos\AutomationAdvanced\AutoTarget\dados"
folders = ["dados2", "dados3", "dados4"]

reducoes_pct = []
normas_a = []
total_reconciliacoes = 0
reconciliacoes_sucesso = 0

deadline_misses = defaultdict(int)
total_tasks_run = defaultdict(int)

energia_vs_canhoes = defaultdict(list)

for folder in folders:
    folder_path = os.path.join(base_dir, folder)
    
    # 1. Reconciliação
    recon_path = os.path.join(folder_path, "telemetry_reconciliation.csv")
    if os.path.exists(recon_path):
        with open(recon_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                total_reconciliacoes += 1
                reducao = float(row.get("Reducao_Pct", 0))
                norma = float(row.get("NormA_yHat", 0))
                if reducao > 0:  # Sucesso (quando há espaço nulo N>=4)
                    reducoes_pct.append(reducao)
                    normas_a.append(norma)
                    reconciliacoes_sucesso += 1

    # 2. Deadline Misses
    misses_path = os.path.join(folder_path, "deadline_misses_ALL_CORES.csv")
    if os.path.exists(misses_path):
        with open(misses_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                tarefa = row.get("Tarefa")
                if tarefa:
                    deadline_misses[tarefa] += int(row.get("DeadlineMisses", 0))
                    total_tasks_run[tarefa] += int(row.get("ExecucoesTotais", row.get("Contagem", 1000))) # aprox se faltar

    # 3. Penalidade
    penalty_path = os.path.join(folder_path, "telemetry_energy_penalty.csv")
    if os.path.exists(penalty_path):
        with open(penalty_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                c_esq = int(row.get("CanhoesEsq", 0))
                int_esq = float(row.get("IntervaloEsq", 1500))
                c_dir = int(row.get("CanhoesDir", 0))
                int_dir = float(row.get("IntervaloDir", 1500))
                
                if c_esq > 0: energia_vs_canhoes[c_esq].append(int_esq)
                if c_dir > 0: energia_vs_canhoes[c_dir].append(int_dir)

print("=== ANÁLISE AGREGADA (dados2, dados3, dados4) ===")
print("\n--- Reconciliação de Dados (WLS/EJML) ---")
print(f"Total de amostras processadas: {total_reconciliacoes}")
print(f"Amostras com sucesso (N>=4): {reconciliacoes_sucesso}")
if reducoes_pct:
    media_reducao = sum(reducoes_pct) / len(reducoes_pct)
    media_norma = sum(normas_a) / len(normas_a)
    print(f"Média de Redução do Erro (MSE): {media_reducao:.2f}%")
    print(f"Média da Norma das Restrições (||A*yHat||): {media_norma:.6f} (Próximo a zero = perfeito)")

print("\n--- Escalonamento Tempo Real (Deadline Misses ALL_CORES) ---")
for tarefa, misses in deadline_misses.items():
    print(f"Tarefa {tarefa:<20}: {misses} falhas agregadas nas 3 execuções")

print("\n--- Impacto de Penalidade de Energia (L = 5 canhões) ---")
for num_canhoes in sorted(energia_vs_canhoes.keys()):
    intervalos = energia_vs_canhoes[num_canhoes]
    media_intervalo = sum(intervalos) / len(intervalos)
    print(f"Canhões em campo: {num_canhoes} -> Intervalo de Recarga Médio: {media_intervalo:.1f} ms")
