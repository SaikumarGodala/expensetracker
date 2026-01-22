import json
import os
import sys

def convert_log_to_training_data(log_file, output_file):
    print(f"Reading log file: {log_file}")
    
    records = []
    
    with open(log_file, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line: continue
            
            try:
                data = json.loads(line)
                
                # Extract fields
                raw_input = data.get('rawInput', {})
                parsed = data.get('parsedFields', {})
                decision = data.get('finalDecision', {})
                
                text = raw_input.get('fullMessageText')
                merchant = parsed.get('merchantName')
                label = decision.get('categoryName')
                
                # Sanity checks
                if not text or not label:
                    continue
                    
                # Skip if merchant is null/empty and we want merchant data
                # But sometimes we might want to learn from description patterns? 
                # For this task, we focus on Merchant -> Category.
                if not merchant:
                    continue

                # Create standardized record
                record = {
                    "text": text,
                    "merchant": merchant,
                    "label": label,
                    "conf": 1.0, # Trusting app data
                    "source": "APP_LOG"
                }
                records.append(record)
                
            except json.JSONDecodeError:
                print(f"Skipping invalid JSON line")
                continue

    print(f"Extracted {len(records)} valid records.")
    
    print(f"Saving to {output_file}...")
    with open(output_file, 'w', encoding='utf-8') as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
            
    print("Done!")

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('input', help='Input log JSONL file')
    parser.add_argument('output', help='Output clean JSONL file')
    args = parser.parse_args()
    
    convert_log_to_training_data(args.input, args.output)
