import json
import re

log_file = r"C:\Users\marco\.gemini\antigravity\brain\e06a0628-db2b-4c95-9bbd-216395df8732\.system_generated\logs\transcript.jsonl"
output_file = r"C:\Users\marco\OneDrive\Documentos\AutomationAdvanced\AutoTarget\old_report.md"

with open(log_file, "r", encoding="utf-8") as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get("type") == "TOOL_RESPONSE" and "Relatório Técnico AV2" in data.get("content", ""):
                # the content has the file output. Let's write it down.
                content = data["content"]
                
                # The view_file tool output has "The following code has been modified... <line_number>: "
                # We need to strip the line numbers.
                clean_lines = []
                for cl in content.split("\n"):
                    # Match "1: ", "10: ", etc.
                    m = re.match(r'^\d+:\s(.*)', cl)
                    if m:
                        clean_lines.append(m.group(1))
                    else:
                        clean_lines.append(cl)
                
                with open(output_file, "w", encoding="utf-8") as out:
                    out.write("\n".join(clean_lines))
        except Exception as e:
            pass

print("Done extracting.")
