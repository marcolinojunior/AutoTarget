package com.autotarget.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.autotarget.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CsvViewerActivity extends AppCompatActivity {

    private Spinner spinnerFiles;
    private TableLayout tableLayout;
    private LineChart csvChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_viewer);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Visualizador de Dados CSV");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        spinnerFiles = findViewById(R.id.spinnerFiles);
        tableLayout = findViewById(R.id.tableLayout);
        csvChart = findViewById(R.id.csvChart);

        setupSpinner();
    }

    private void setupSpinner() {
        String[] files = {
                "telemetry_reconciliation.csv",
                "telemetry_energy_penalty.csv",
                "telemetry_rma_runtime.csv",
                "telemetry_sensor_variance.csv",
                "telemetry_utility.csv",
                "telemetry_energy_restoration.csv",
                "deadline_misses_DEFAULT.csv",
                "deadline_misses_CRITICAL.csv"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, files);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFiles.setAdapter(adapter);

        spinnerFiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadCsvData(files[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadCsvData(String filename) {
        tableLayout.removeAllViews();
        csvChart.setVisibility(View.GONE);
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, filename);

        if (!file.exists()) {
            Toast.makeText(this, "Arquivo não encontrado: " + filename, Toast.LENGTH_SHORT).show();
            return;
        }

        List<List<Entry>> chartEntries = new ArrayList<>();
        List<String> lineLabels = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            int rowIndex = 0;

            // Identificar colunas para o gráfico baseada no nome do arquivo
            int[] numericCols = getNumericColsForFile(filename);
            if (numericCols.length > 0) {
                for (int i = 0; i < numericCols.length; i++) chartEntries.add(new ArrayList<>());
            }

            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",");
                addRowToTable(columns, isHeader);

                if (isHeader) {
                    if (numericCols.length > 0) {
                        for (int colIdx : numericCols) {
                            if (colIdx < columns.length) lineLabels.add(columns[colIdx]);
                            else lineLabels.add("Valor");
                        }
                    }
                } else {
                    if (numericCols.length > 0) {
                        for (int i = 0; i < numericCols.length; i++) {
                            int colIdx = numericCols[i];
                            if (colIdx < columns.length) {
                                try {
                                    float val = Float.parseFloat(columns[colIdx]);
                                    chartEntries.get(i).add(new Entry(rowIndex, val));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                    rowIndex++;
                }
                isHeader = false;
            }

            if (rowIndex > 0 && !chartEntries.isEmpty() && !chartEntries.get(0).isEmpty()) {
                setupChart(chartEntries, lineLabels);
            }

        } catch (IOException e) {
            Log.e("CsvViewer", "Erro ao ler CSV", e);
            Toast.makeText(this, "Erro ao ler arquivo", Toast.LENGTH_SHORT).show();
        }
    }

    private int[] getNumericColsForFile(String filename) {
        if (filename.contains("energy_penalty")) return new int[]{1, 2}; // Energia Esq, Dir
        if (filename.contains("reconciliation")) return new int[]{2, 4}; // MSE_Reconciliado, Erro_Pos
        if (filename.contains("utility")) return new int[]{3}; // U_Atual
        if (filename.contains("rma_runtime")) return new int[]{2, 3}; // Max_ms, Avg_ms
        return new int[0];
    }

    private void setupChart(List<List<Entry>> allEntries, List<String> labels) {
        csvChart.setVisibility(View.VISIBLE);
        LineData lineData = new LineData();
        int[] colors = {Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.GREEN};

        for (int i = 0; i < allEntries.size(); i++) {
            String label = i < labels.size() ? labels.get(i) : "Série " + (i + 1);
            LineDataSet dataSet = new LineDataSet(allEntries.get(i), label);
            dataSet.setColor(colors[i % colors.length]);
            dataSet.setCircleColor(colors[i % colors.length]);
            dataSet.setDrawCircles(allEntries.get(i).size() < 50);
            dataSet.setLineWidth(2f);
            dataSet.setValueTextColor(Color.WHITE);
            lineData.addDataSet(dataSet);
        }

        csvChart.setData(lineData);
        csvChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        csvChart.getXAxis().setTextColor(Color.WHITE);
        csvChart.getAxisLeft().setTextColor(Color.WHITE);
        csvChart.getAxisRight().setEnabled(false);
        csvChart.getLegend().setTextColor(Color.WHITE);
        csvChart.getDescription().setText("Evolução Temporal");
        csvChart.getDescription().setTextColor(Color.WHITE);
        csvChart.invalidate();
    }

    private void addRowToTable(String[] columns, boolean isHeader) {
        TableRow row = new TableRow(this);
        row.setPadding(8, 8, 8, 8);

        for (String col : columns) {
            TextView tv = new TextView(this);
            tv.setText(col);
            tv.setPadding(16, 8, 16, 8);
            tv.setTextColor(Color.WHITE);
            if (isHeader) {
                tv.setTypeface(null, Typeface.BOLD);
                tv.setBackgroundColor(Color.parseColor("#E94560"));
            } else {
                tv.setBackgroundColor(Color.parseColor("#223344"));
            }
            row.addView(tv);
        }

        tableLayout.addView(row);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
