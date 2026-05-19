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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CsvViewerActivity extends AppCompatActivity {

    private Spinner spinnerFiles;
    private TableLayout tableLayout;

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
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, filename);

        if (!file.exists()) {
            Toast.makeText(this, "Arquivo não encontrado: " + filename, Toast.LENGTH_SHORT).show();
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",");
                addRowToTable(columns, isHeader);
                isHeader = false;
            }
        } catch (IOException e) {
            Log.e("CsvViewer", "Erro ao ler CSV", e);
            Toast.makeText(this, "Erro ao ler arquivo", Toast.LENGTH_SHORT).show();
        }
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
