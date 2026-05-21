import pandas as pd
import matplotlib.pyplot as plt
import os

# Create output directory
out_dir = r"C:\Users\marco\OneDrive\Documentos\AutomationAdvanced\AutoTarget\graficos"
os.makedirs(out_dir, exist_ok=True)

dados_dir = r"C:\Users\marco\OneDrive\Documentos\AutomationAdvanced\AutoTarget\dados\dados4"

# 1. Gráfico de Energia e Penalidade
try:
    df_energy = pd.read_csv(os.path.join(dados_dir, "telemetry_energy_penalty.csv"))
    plt.figure(figsize=(10, 6))
    plt.plot(df_energy['Index'], df_energy['Energia_Esq'], label='Energia Esquerda', color='blue')
    plt.plot(df_energy['Index'], df_energy['Energia_Dir'], label='Energia Direita', color='red')
    plt.ylabel('Energia')
    plt.xlabel('Tempo (s)')
    
    ax2 = plt.gca().twinx()
    ax2.plot(df_energy['Index'], df_energy['Canhoes_Esq'], label='Canhões Esq', color='lightblue', linestyle='--')
    ax2.plot(df_energy['Index'], df_energy['Canhoes_Dir'], label='Canhões Dir', color='salmon', linestyle='--')
    ax2.set_ylabel('Quantidade de Canhões')
    
    plt.title('Consumo de Energia vs Quantidade de Canhões')
    plt.legend(loc='upper left')
    plt.savefig(os.path.join(out_dir, "grafico_energia.png"))
    plt.close()
    print("Gerado grafico_energia.png")
except Exception as e:
    print(f"Erro em energia: {e}")

# 2. Gráfico de Redução de Erro de Reconciliação
try:
    df_recon = pd.read_csv(os.path.join(dados_dir, "telemetry_reconciliation.csv"))
    # Filter where Reducao_Pct > 0 to show actual reconciliations
    df_recon = df_recon[df_recon['Reducao_Pct'] > 0]
    if not df_recon.empty:
        plt.figure(figsize=(10, 6))
        bar_width = 0.35
        indices = range(len(df_recon))
        
        plt.bar([i - bar_width/2 for i in indices], df_recon['MSE_Bruto'], bar_width, label='MSE Bruto', color='orange')
        plt.bar([i + bar_width/2 for i in indices], df_recon['MSE_Reconciliado'], bar_width, label='MSE Reconciliado', color='green')
        
        plt.ylabel('Erro Quadrático Médio (MSE)')
        plt.xlabel('Instância de Reconciliação')
        plt.title('Redução de Erro por Reconciliação WLS')
        plt.xticks(indices, df_recon['Index'])
        plt.legend()
        plt.savefig(os.path.join(out_dir, "grafico_reconciliacao.png"))
        plt.close()
        print("Gerado grafico_reconciliacao.png")
except Exception as e:
    print(f"Erro em reconciliação: {e}")

print("Gráficos básicos gerados com sucesso.")
