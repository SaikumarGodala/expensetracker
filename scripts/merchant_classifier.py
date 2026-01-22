#!/usr/bin/env python3
"""
Merchant Extraction & Classification Pipeline
Uses pre-trained ML models to:
1. Extract merchant names from SMS messages
2. Classify merchants into expense categories
3. Export training data for the Android app

Requirements:
    pip install torch transformers tqdm pandas
    
Usage:
    python merchant_classifier.py --input log_batch_*.json --output training_data.json
"""

import json
import re
import argparse
import sys
import os
from pathlib import Path
from collections import Counter, defaultdict
from tqdm import tqdm
import torch
from transformers import pipeline

# Check for GPU
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
print(f"🔧 Using device: {DEVICE}")

# ==========================================
# ⚙️ CONFIGURATION & PATTERNS
# ==========================================

# 1. Deterministic Categories (Regex Rules)
# These are structural/transactional types that ML often confuses.
REGEX_PREFILTERS = [
    (r'(?i)(salary|credited by|payroll|bonus credited)', "Salary"),
    (r'(?i)(refund|reversed|reimbursement|credited back)', "Refund"),
    (r'(?i)(sip purchase|mutual fund|folio|nav |allocat|investment)', "Investment"),
    # Broad investment keywords often catch general banking
    (r'(?i)(loaded|added money|add money to|wallet)', "Wallet Load"),
]

# 2. Semantic Categories for ML (EXPANDED LIST)
# User requested more granularity for the clean dataset.
SEMANTIC_CANDIDATES = [
    "Food Delivery", "Dining Out", "Groceries", "Shopping",
    "Travel", "Cab & Taxi", "Fuel", "Utilities", "Mobile + WiFi",
    "Housing & Rent", "Education", "Healthcare", "Personal Care",
    "Entertainment", "Insurance", "Donations", "Gifts",
    "Mutual Funds", "Stocks", "Credit Bill Payments"
]

# 3. Tuning
BATCH_SIZE = 32 if torch.cuda.is_available() else 4
HIGH_CONFIDENCE_THRESHOLD = 0.85

def load_sms_data(input_path: str) -> list[dict]:
    """Load SMS data from JSONL or Plain Text file."""
    messages = []
    if not os.path.exists(input_path):
        print(f"❌ Input file not found: {input_path}")
        sys.exit(1)

    is_text_file = input_path.endswith('.txt')

    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()

    for line in content.strip().split('\n'):
        if not line.strip(): continue
        
        if is_text_file:
            # Plain text mode
            messages.append({'text': line.strip(), 'sender': 'UNKNOWN'})
        else:
            # JSONL mode
            try:
                msg = json.loads(line)
                if 'body' in msg:
                    messages.append({'text': msg['body'], 'sender': msg.get('sender', '')})
                elif 'rawInput' in msg and 'fullMessageText' in msg['rawInput']:
                    messages.append({'text': msg['rawInput']['fullMessageText'], 'sender': msg.get('rawInput', {}).get('sender', '')})
            except json.JSONDecodeError:
                continue

    print(f"📱 Loaded {len(messages)} SMS messages")
    return messages

def extract_merchant_regex(text: str) -> str:
    """Extract potential merchant name using common patterns."""
    text = text.replace('\n', ' ').strip()
    
    def validate(m):
        if not m: return None
        m = m.strip()
        # Invalid if starts with digit, has slash (date), or is too short
        if re.match(r'^\d', m) or '/' in m or len(m) < 3: return None
        return m

    # Pattern 1: 'at MERCHANT'
    match = re.search(r'(?i)\s+at\s+([A-Z0-9\s\&\.\-\*]{3,25}?)(?:\s+(?:on|for|using|via|worth|with|datetime)|$)', text)
    if match and validate(match.group(1)): return validate(match.group(1))

    # Pattern 2: 'Info: MERCHANT*'
    match = re.search(r'(?i)Info:\s*([A-Z0-9\s\&\.\-\*]{3,25}?)(?:\*|$)', text)
    if match and validate(match.group(1)): return validate(match.group(1))

    # Pattern 3: UPI 'paid to VPA'
    match = re.search(r'(?i)paid to\s+([A-Z0-9]+?)@', text)
    if match and validate(match.group(1)): return validate(match.group(1))

    # Pattern 4: ICICI/HDFC 'on MERCHANT'
    match = re.search(r'(?i)\s+on\s+([A-Z0-9\s\&\.\-\*]{3,25}?)(?:\.|$|Avl Limit)', text)
    # Exclude obvious months
    if match:
        raw = match.group(1)
        if "Jan" not in raw and "Feb" not in raw and validate(raw):
            return validate(raw)

    # Pattern 5: 'To MERCHANT'
    match = re.search(r'(?i)\s+To\s+([A-Z0-9\s\&\.\-\*]{3,25}?)(?:\s+On\s+)', text)
    if match and validate(match.group(1)): return validate(match.group(1))

    # Pattern 6: Axis Bank 'IST MERCHANT Avl Limit'
    match = re.search(r'(?i)\s+IST\s+([A-Z0-9\s\&\.\-\*]+?)\s+Avl Limit', text)
    if match and validate(match.group(1)): return validate(match.group(1))

    return None

def build_hypothesis(text: str, merchant: str) -> str:
    """Create a clean context string for the model."""
    if merchant:
        return f"A financial transaction purchase at {merchant}"
    else:
        # Heavily truncate to ensure deduplication works for "Uber trip..."
        clean_text = re.sub(r'\d+', '', text[:40]) 
        return f"A financial transaction related to {clean_text}"

def main():
    parser = argparse.ArgumentParser(description="Hybrid ML Merchant Classifier")
    parser.add_argument('-i', '--input', required=True, help="Input file (JSONL or TXT)")
    parser.add_argument('-o', '--output', required=True, help="Output JSONL file")
    parser.add_argument('--kotlin-output', default="merchant_categories.kt", help="Output Kotlin file")
    parser.add_argument('--skip-ml', action='store_true', help="Skip ML stage (Regex only)")
    parser.add_argument('--pre-labeled', action='store_true', help="Input file is already labeled (skip classification)")

    args = parser.parse_args()

    print("\n🚀 Starting Hybrid Classification Pipeline...")
    
    unique_patterns = {} # text -> data
    
    if args.pre_labeled:
        print(f"📂 Loading pre-labeled data from {args.input}...")
        with open(args.input, 'r', encoding='utf-8') as f:
            for line in f:
                if not line.strip(): continue
                try:
                    record = json.loads(line)
                    text = record.get('text', '')
                    if not text: continue
                    
                    if text in unique_patterns:
                        unique_patterns[text]['count'] += 1
                    else:
                        unique_patterns[text] = {
                            'count': 1,
                            'merchant': record.get('merchant'),
                            'category': record.get('label'),
                            'confidence': record.get('conf', 1.0),
                            'method': record.get('source', 'PRE-LABELED')
                        }
                except json.JSONDecodeError:
                    continue
        print(f"✅ Loaded {len(unique_patterns)} labeled records.")
        
    else:
        # 1. Load Data
        messages = load_sms_data(args.input)

        # 2. Setup Pipeline
        classifier = None
        if not args.skip_ml:
            print(f"\n🧠 Loading Model (Batch Size: {BATCH_SIZE})...")
            try:
                classifier = pipeline(
                    "zero-shot-classification",
                    model="MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli",
                    device=0 if torch.cuda.is_available() else -1,
                    framework="pt"
                )
            except Exception as e:
                print(f"❌ Failed to load model: {e}")
                sys.exit(1)

        ml_queue_keys = []   # List of UNIQUE keys for ML batching
        ml_queue_data = {}   # key -> {texts: [], merchant: str, hypothesis: str}

        # Preprocessing loop
        for msg in tqdm(messages, desc="Preprocessing"):
            text = msg['text'].strip()
            
            if text in unique_patterns:
                unique_patterns[text]['count'] += 1
                continue

            merchant = extract_merchant_regex(text)
            
            # Regex Check
            regex_match = None
            for pattern, category in REGEX_PREFILTERS:
                if re.search(pattern, text):
                    regex_match = category
                    break
            
            if regex_match:
                unique_patterns[text] = {
                    'count': 1,
                    'merchant': merchant,
                    'category': regex_match,
                    'confidence': 1.0,
                    'method': 'Regex'
                }
            else:
                # Queue for ML
                hypothesis = build_hypothesis(text, merchant)
                queue_key = merchant.strip().upper() if merchant else hypothesis[:50]
                
                if queue_key not in ml_queue_data:
                    ml_queue_keys.append(queue_key)
                    ml_queue_data[queue_key] = {
                        'texts': [],
                        'merchant': merchant,
                        'hypothesis': hypothesis
                    }
                
                ml_queue_data[queue_key]['texts'].append(text)
                
                unique_patterns[text] = {
                    'count': 1,
                    'merchant': merchant,
                    'category': None, # Pending
                    'confidence': 0.0,
                    'method': 'ML'
                }

        print(f"\n📊 Pre-filtering Stats:")
        print(f"   Total Unique: {len(unique_patterns)}")
        print(f"   Queued for ML: {len(ml_queue_keys)} hypothesis groups")

        # ML Loop
        if not args.skip_ml and ml_queue_keys:
            print(f"\n🏷️ Running Zero-Shot ML (Batch Size {BATCH_SIZE})...")
            all_hypotheses = [ml_queue_data[k]['hypothesis'] for k in ml_queue_keys]
            
            with tqdm(total=len(all_hypotheses), desc="ML Inference") as pbar:
                for i in range(0, len(all_hypotheses), BATCH_SIZE):
                    batch = all_hypotheses[i : i + BATCH_SIZE]
                    try:
                        with torch.inference_mode():
                            outputs = classifier(batch, SEMANTIC_CANDIDATES, multi_label=False)
                        if isinstance(outputs, dict): outputs = [outputs]

                        for idx, output in enumerate(outputs):
                            key = ml_queue_keys[i + idx]
                            best_label = output['labels'][0]
                            best_score = output['scores'][0]
                            
                            # Apply to all texts
                            for t in ml_queue_data[key]['texts']:
                                unique_patterns[t]['category'] = best_label
                                unique_patterns[t]['confidence'] = best_score
                                
                    except Exception as e:
                        print(f"⚠️ Batch Error: {e}")
                    
                    pbar.update(len(batch))

    # Output Generation (JSONL)
    print(f"\n💾 Saving to {args.output}...")
    with open(args.output, 'w', encoding='utf-8') as f:
        for text, data in unique_patterns.items():
            if not data['category']: continue
            
            # Minimal JSONL record
            record = {
                "text": text,
                "merchant": data['merchant'],
                "label": data['category'],
                "conf": round(data['confidence'], 2)
            }
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
            
    # Write Kotlin
    merchant_votes = defaultdict(lambda: defaultdict(float))
    for data in unique_patterns.values():
        if data['confidence'] >= HIGH_CONFIDENCE_THRESHOLD and data['merchant']:
             m_key = re.sub(r'\s+', ' ', data['merchant']).strip().upper()
             merchant_votes[m_key][data['category']] += data['count'] * data['confidence']
    
    kotlin_code = """// Auto-generated merchant mappings
val ML_DETECTED_MERCHANTS = mapOf(
"""
    for m in sorted(merchant_votes.keys(), key=lambda k: -sum(merchant_votes[k].values())):
        winner = max(merchant_votes[m].items(), key=lambda x: x[1])[0]
        m_escaped = m.replace('"', '\\"')
        kotlin_code += f'    "{m_escaped}" to "{winner}",\n'
        
    kotlin_code += ")\n"
    
    with open(args.kotlin_output, 'w', encoding='utf-8') as f:
        f.write(kotlin_code)
    
    print("\n✅ Pipeline Complete!")

if __name__ == "__main__":
    main()
