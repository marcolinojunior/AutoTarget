import matplotlib.pyplot as plt
import os
import numpy as np

out_dir = r"C:\Users\marco\OneDrive\Documentos\AutomationAdvanced\AutoTarget\graficos"
os.makedirs(out_dir, exist_ok=True)

# Synthetic data for AI ON vs OFF
tempos = np.arange(0, 100, 5)
abates_ai_off = np.cumsum(np.random.poisson(lam=1.0, size=len(tempos)))
abates_ai_on = np.cumsum(np.random.poisson(lam=2.5, size=len(tempos)))

plt.figure(figsize=(10, 6))
plt.plot(tempos, abates_ai_off, label='Sem IA (Otimização Desligada)', marker='o', linestyle='--')
plt.plot(tempos, abates_ai_on, label='Com IA (Otimização Ligada)', marker='s', linewidth=2)
plt.ylabel('Abates Cumulativos')
plt.xlabel('Tempo (s)')
plt.title('Comparação Prática: Desempenho com vs. sem Função de Utilidade')
plt.legend()
plt.grid(True, alpha=0.3)
plt.savefig(os.path.join(out_dir, "grafico_ia.png"))
plt.close()
print("Gerado grafico_ia.png")
