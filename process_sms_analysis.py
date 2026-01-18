


import json
import re
from datetime import datetime
from collections import defaultdict

RAW_SMS_FILE = r"c:\Users\godha\AndroidStudioProjects\ExpenseTracker\raw_sms_inbox_scan.jsonl.json"
EXISTING_TXN_FILE = r"c:\Users\godha\AndroidStudioProjects\ExpenseTracker\transactions_unique_corrected.jsonl"
OUTPUT_REPORT_FILE = r"c:\Users\godha\AndroidStudioProjects\ExpenseTracker\sender_analysis_report.md"

# Regex for basic transaction extraction
AMOUNT_REGEX = r"(?i)(?:rs\.?|inr)\s*[\.]?\s*([\d,]+(?:\.\d{1,2})?)"
MERCHANT_REGEX = r"(?i)(?:at|to|from)\s+([a-zA-Z0-9\s\.\-\*]+?)(?:\s+(?:on|using|via|with)|$)"

def load_jsonl(filepath):
    data = []
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                if line.strip():
                    try:
                        data.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
    except FileNotFoundError:
        print(f"File not found: {filepath}")
    return data

def extract_transaction_info(body, sender):
    body_lower = body.lower()
    
    txn_type = "UNKNOWN"
    if "credited" in body_lower or "received" in body_lower or "deposited" in body_lower:
        txn_type = "INCOME"
    elif "debited" in body_lower or "spent" in body_lower or "paid" in body_lower or "sent" in body_lower:
        txn_type = "EXPENSE"
    elif "due" in body_lower or "bill" in body_lower:
        txn_type = "PENDING"
        
    amount_match = re.search(AMOUNT_REGEX, body)
    amount = amount_match.group(1) if amount_match else "0.00"
    
    merchant_match = re.search(MERCHANT_REGEX, body)
    merchant = merchant_match.group(1).strip() if merchant_match else "Unknown"
    
    # Category heuristics
    category = "Other"
    
    if "zomato" in body_lower or "swiggy" in body_lower:
        category = "Food & Dining"
    elif "uber" in body_lower or "ola" in body_lower or "rapido" in body_lower:
        category = "Transport"
    elif "jio" in body_lower or "airtel" in body_lower:
        category = "Recharge/Bill"
    elif "amazon" in body_lower or "flipkart" in body_lower:
        category = "Shopping"
    elif "zerodha" in body_lower or "upstox" in body_lower or "groww" in body_lower:
        category = "Investment"
        if txn_type == "EXPENSE": txn_type = "INVESTMENT"
    elif "salary" in body_lower:
        category = "Salary"
        txn_type = "INCOME"
    
    return {
        "sender": sender,
        "body": body,
        "type": txn_type,
        "category": category,
        "counterparty": merchant
    }

def analyze_senders(raw_data):
    sender_stats = defaultdict(lambda: {"count": 0, "bodies": []})
    for entry in raw_data:
        sender = entry.get("sender", "UNKNOWN")
        sender_stats[sender]["count"] += 1
        if len(sender_stats[sender]["bodies"]) < 5:
            sender_stats[sender]["bodies"].append(entry.get("body", "")[:100])
            
    return sender_stats

def main():
    print("Loading data...")
    raw_data = load_jsonl(RAW_SMS_FILE)
    existing_data = load_jsonl(EXISTING_TXN_FILE)
    
    existing_bodies = {entry.get("body", "").strip() for entry in existing_data if entry.get("body")}
    
    print(f"Loaded {len(raw_data)} raw SMS and {len(existing_data)} existing transactions.")
    
    new_transactions = []
    sender_stats = analyze_senders(raw_data)
    
    print("Processing...")
    for entry in raw_data:
        body = entry.get("body", "").strip()
        sender = entry.get("sender", "")
        
        if body in existing_bodies:
            continue
            
        if re.search(r"(rs\.|inr|debited|credited|spent|paid|sent)", body, re.IGNORECASE):
            txn = extract_transaction_info(body, sender)
            if txn["type"] != "UNKNOWN":
                new_transactions.append(txn)
                existing_bodies.add(body)
    
    print(f"Found {len(new_transactions)} new unique transactions.")
    
    # Append new transactions
    if new_transactions:
        with open(EXISTING_TXN_FILE, 'a', encoding='utf-8') as f:
            for txn in new_transactions:
                f.write(json.dumps(txn) + "\n")
        print(f"Appended to {EXISTING_TXN_FILE}")
    else:
        print("No new transactions to append.")
    
    # Generate report
    with open(OUTPUT_REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write("# Sender Pattern Analysis\n\n")
        f.write("| Sender | Count | Common Patterns |\n")
        f.write("|---|---|---|\n")
        
        sorted_senders = sorted(sender_stats.items(), key=lambda x: x[1]['count'], reverse=True)
        
        for sender, stats in sorted_senders:
            samples = "<br>".join([s.replace('\n', ' ').replace('|', '') for s in stats['bodies']])
            f.write(f"| {sender} | {stats['count']} | {samples} |\n")
            
    print(f"Analysis written to {OUTPUT_REPORT_FILE}")

if __name__ == "__main__":
    main()
