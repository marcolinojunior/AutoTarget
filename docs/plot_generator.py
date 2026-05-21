import matplotlib.pyplot as plt
import numpy as np
import os

# Set global aesthetics
plt.style.use('ggplot')
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['font.sans-serif'] = ['Inter', 'Roboto', 'Arial']
plt.rcParams['axes.titlesize'] = 14
plt.rcParams['axes.labelsize'] = 12

out_dir = r"c:\Users\marco\OneDrive\Documentos\AutomationAdvanced\AutoTarget\docs"
os.makedirs(out_dir, exist_ok=True)

# 1. Penalty Impact Chart
canhoes = np.arange(1, 11)
intervalo = [1500 if x <= 5 else 1500 * (1 + 0.2 * (x - 5)) for x in canhoes]

plt.figure(figsize=(8, 5))
plt.plot(canhoes, intervalo, marker='o', linestyle='-', color='#d32f2f', linewidth=2.5, markersize=8)
plt.axvline(x=5, color='#1976d2', linestyle='--', label='Limiar de Penalidade (L=5)')
plt.title('Impacto da Penalidade de Escala no Intervalo de Disparo', fontweight='bold')
plt.xlabel('Número de Canhões em Campo (N)')
plt.ylabel('Intervalo Médio de Disparo (ms)')
plt.xticks(canhoes)
plt.grid(True, linestyle=':', alpha=0.7)
plt.legend()
plt.tight_layout()
plt.savefig(os.path.join(out_dir, 'penalty_impact_chart.png'), dpi=300)
plt.close()

# 2. Affinity Comparison Chart
labels = ['T4-Render', 'T1-Physics', 'T7-Sensor', 'T8-Reconciliação']
single_core = [931, 75, 60, 915]
two_cores = [120, 15, 25, 400]
all_cores = [31, 0.3, 3.3, 16]

x = np.arange(len(labels))
width = 0.25

fig, ax = plt.subplots(figsize=(10, 6))
rects1 = ax.bar(x - width, single_core, width, label='SINGLE_CORE', color='#ff9800')
rects2 = ax.bar(x, two_cores, width, label='TWO_CORES', color='#2196f3')
rects3 = ax.bar(x + width, all_cores, width, label='ALL_CORES (big.LITTLE)', color='#4caf50')

ax.set_ylabel('Tempo de Resposta Máximo (ms) - Escala Log')
ax.set_title('Impacto da Afinidade de CPU nos Tempos de Resposta', fontweight='bold')
ax.set_xticks(x)
ax.set_xticklabels(labels)
ax.set_yscale('log')
ax.legend()
plt.grid(True, axis='y', linestyle=':', alpha=0.7)
plt.tight_layout()
plt.savefig(os.path.join(out_dir, 'affinity_comparison_chart.png'), dpi=300)
plt.close()

# 3. AI Comparison Chart
metrics = ['Sobrevida Média (s)', 'Abates Confirmados']
greedy = [28, 12]
optimized = [60, 31]

x2 = np.arange(len(metrics))
width2 = 0.35

fig2, ax2 = plt.subplots(figsize=(7, 5))
rects_g = ax2.bar(x2 - width2/2, greedy, width2, label='IA Gulosa', color='#f44336')
rects_o = ax2.bar(x2 + width2/2, optimized, width2, label='IA Otimizada (WLS)', color='#4caf50')

ax2.set_ylabel('Valor absoluto')
ax2.set_title('Comparativo de Desempenho: Gulosa vs Otimizada', fontweight='bold')
ax2.set_xticks(x2)
ax2.set_xticklabels(metrics)
ax2.legend()

def autolabel(rects, ax):
    for rect in rects:
        height = rect.get_height()
        ax.annotate(f'{height}',
                    xy=(rect.get_x() + rect.get_width() / 2, height),
                    xytext=(0, 3),  
                    textcoords="offset points",
                    ha='center', va='bottom', fontweight='bold')

autolabel(rects_g, ax2)
autolabel(rects_o, ax2)

plt.grid(True, axis='y', linestyle=':', alpha=0.7)
plt.tight_layout()
plt.savefig(os.path.join(out_dir, 'ai_comparison_chart.png'), dpi=300)
plt.close()

print("Plots generated successfully.")
