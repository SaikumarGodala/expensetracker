import json
import os

INPUT_FILE = "log_batch_1769003454695_bff214f4.jsonl.json"
OUTPUT_FILE = "raw_sms_messages.txt"

def extract_messages():
    if not os.path.exists(INPUT_FILE):
        print(f"❌ Input file not found: {INPUT_FILE}")
        return

    count = 0
    with open(OUTPUT_FILE, "w", encoding="utf-8") as out:
        with open(INPUT_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line: continue
                try:
                    data = json.loads(line)
                    # Extract from rawInput -> fullMessageText
                    if "rawInput" in data and "fullMessageText" in data["rawInput"]:
                        msg_text = data["rawInput"]["fullMessageText"]
                        # Clean up newlines for better readability
                        clean_text = msg_text.replace("\n", " ").strip()
                        out.write(clean_text + "\n")
                        count += 1
                except json.JSONDecodeError:
                    continue
    
    print(f"✅ Extracted {count} raw messages to {OUTPUT_FILE}")

if __name__ == "__main__":
    extract_messages()
